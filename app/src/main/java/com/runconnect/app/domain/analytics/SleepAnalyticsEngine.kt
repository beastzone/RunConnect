package com.runconnect.app.domain.analytics

import com.runconnect.app.domain.model.BedtimeStats
import com.runconnect.app.domain.model.MonthlySleepReport
import com.runconnect.app.domain.model.SleepAnnotation
import com.runconnect.app.domain.model.SleepCorrelation
import com.runconnect.app.domain.model.SleepDebtInfo
import com.runconnect.app.domain.model.SleepFactorTag
import com.runconnect.app.domain.model.SleepPrediction
import com.runconnect.app.domain.model.SleepRecommendation
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.WeeklySleepReport
import com.runconnect.app.domain.model.WakeTimeStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.max
import kotlin.math.sqrt

object SleepAnalyticsEngine {

    fun computePersonalSleepNeed(sessions: List<SleepSession>): Long {
        val values = sessions.filterNot { it.isNap }.map { it.totalSleepMinutes }.sorted()
        if (values.isEmpty()) return 480L
        val idx = (values.size * 0.75).toInt().coerceAtMost(values.size - 1)
        return values[idx]
    }

    fun computeDebt(sessions: List<SleepSession>, sleepNeed: Long): SleepDebtInfo {
        val mainSessions = sessions.filterNot { it.isNap }
        val lastNight = mainSessions.firstOrNull()
        val lastDebt = max(0L, sleepNeed - (lastNight?.totalSleepMinutes ?: 0L))
        val sevenDay = mainSessions.take(7).sumOf { max(0L, sleepNeed - it.totalSleepMinutes) }
        return SleepDebtInfo(lastDebt, sevenDay, sleepNeed)
    }

    fun computeBedtimeStats(sessions: List<SleepSession>): BedtimeStats? {
        val main = sessions.filterNot { it.isNap }
        if (main.size < 3) return null
        val minutesList = main.map { session ->
            val zdt = session.startTime.atZone(ZoneId.systemDefault())
            var mins = zdt.hour * 60 + zdt.minute
            // Times before noon are treated as early morning (next day); add 1440 to wrap correctly
            if (mins < 720) mins += 1440
            mins
        }
        val avg = minutesList.average().toInt() % 1440
        val earliest = minutesList.min() % 1440
        val latest = minutesList.max() % 1440
        val stdDev = stdDevOf(minutesList)

        val weekdayMins = main.filter { !isWeekend(it.startTime) }.map { s ->
            var m = s.startTime.atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
            if (m < 720) m += 1440
            m
        }
        val weekendMins = main.filter { isWeekend(it.startTime) }.map { s ->
            var m = s.startTime.atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
            if (m < 720) m += 1440
            m
        }
        return BedtimeStats(
            avgMinutes = avg,
            earliestMinutes = earliest,
            latestMinutes = latest,
            stdDevMinutes = stdDev,
            weekdayAvgMinutes = if (weekdayMins.isEmpty()) avg else (weekdayMins.average().toInt() % 1440),
            weekendAvgMinutes = if (weekendMins.isEmpty()) avg else (weekendMins.average().toInt() % 1440),
        )
    }

    fun computeWakeTimeStats(sessions: List<SleepSession>): WakeTimeStats? {
        val main = sessions.filterNot { it.isNap }
        if (main.size < 3) return null
        val minutesList = main.map { session ->
            val zdt = session.endTime.atZone(ZoneId.systemDefault())
            zdt.hour * 60 + zdt.minute
        }
        val avg = minutesList.average().toInt()
        val earliest = minutesList.min()
        val latest = minutesList.max()
        val stdDev = stdDevOf(minutesList)

        val weekdayMins = main.filter { !isWeekend(it.endTime) }.map { it.endTime.atZone(ZoneId.systemDefault()).let { z -> z.hour * 60 + z.minute } }
        val weekendMins = main.filter { isWeekend(it.endTime) }.map { it.endTime.atZone(ZoneId.systemDefault()).let { z -> z.hour * 60 + z.minute } }
        return WakeTimeStats(
            avgMinutes = avg,
            earliestMinutes = earliest,
            latestMinutes = latest,
            stdDevMinutes = stdDev,
            weekdayAvgMinutes = if (weekdayMins.isEmpty()) avg else weekdayMins.average().toInt(),
            weekendAvgMinutes = if (weekendMins.isEmpty()) avg else weekendMins.average().toInt(),
        )
    }

    fun buildRecommendations(
        sessions: List<SleepSession>,
        debt: SleepDebtInfo?,
        bedtimeStats: BedtimeStats?,
        wakeStats: WakeTimeStats?,
    ): List<SleepRecommendation> {
        val main = sessions.filterNot { it.isNap }
        val recs = mutableListOf<SleepRecommendation>()

        debt?.let {
            if (it.sevenDayDebt >= 300) {
                val hrs = it.sevenDayDebt / 60
                recs += SleepRecommendation(
                    title = "High Sleep Debt",
                    body = "You've accumulated ~${hrs}h of sleep debt over the last 7 nights. Aim for an earlier bedtime.",
                    priority = 3,
                )
            }
        }

        bedtimeStats?.let {
            if (it.stdDevMinutes > 60) {
                recs += SleepRecommendation(
                    title = "Inconsistent Bedtimes",
                    body = "Your bedtime varies by over an hour (±${it.stdDevMinutes.toInt()} min). A consistent schedule improves sleep quality.",
                    priority = 2,
                )
            }
            if (it.weekendAvgMinutes - it.weekdayAvgMinutes > 60) {
                recs += SleepRecommendation(
                    title = "Social Jet Lag",
                    body = "Your weekend bedtime is over an hour later than weekdays. Try to keep the difference under 60 minutes.",
                    priority = 2,
                )
            }
        }

        val recentWaso = main.take(7).map { it.wasoMinutes }.average()
        if (main.size >= 3 && recentWaso > 30) {
            recs += SleepRecommendation(
                title = "Frequent Nighttime Awakenings",
                body = "You're averaging ${recentWaso.toInt()} minutes awake after sleep onset. Avoid screens and caffeine in the evening.",
                priority = 2,
            )
        }

        val recentLatency = main.take(7).map { it.sleepLatencyMinutes }.average()
        if (main.size >= 3 && recentLatency > 30) {
            recs += SleepRecommendation(
                title = "Long Sleep Onset",
                body = "It takes you ~${recentLatency.toInt()} min to fall asleep on average. Consider a wind-down routine.",
                priority = 1,
            )
        }

        val avgDeepPct = main.take(14).takeIf { it.isNotEmpty() }?.map { s ->
            if (s.totalDurationMinutes > 0) s.deepSleepMinutes * 100.0 / s.totalDurationMinutes else 0.0
        }?.average() ?: 0.0
        if (main.size >= 5 && avgDeepPct < 15.0) {
            recs += SleepRecommendation(
                title = "Low Deep Sleep",
                body = "Your deep sleep is below 15% of total sleep time. Regular exercise and limiting alcohol can help.",
                priority = 1,
            )
        }

        val avgScore = main.take(7).takeIf { it.isNotEmpty() }?.map { it.sleepScore }?.average() ?: 100.0
        if (main.size >= 3 && avgScore < 60) {
            recs += SleepRecommendation(
                title = "Low Sleep Quality",
                body = "Your 7-night average sleep score is ${avgScore.toInt()}. Review the other insights for specific areas to improve.",
                priority = 1,
            )
        }

        return recs.sortedByDescending { it.priority }
    }

    fun buildPrediction(
        sessions: List<SleepSession>,
        debt: SleepDebtInfo?,
        bedtimeStats: BedtimeStats?,
    ): SleepPrediction? {
        val main = sessions.filterNot { it.isNap }
        if (main.size < 3) return null

        val recent = main.take(7)
        val durations = recent.map { it.totalSleepMinutes }.sorted()
        val predictedDuration = durations[durations.size / 2]

        val avgScore = recent.map { it.sleepScore }.average().toInt()

        val debtProjection = max(0L, (debt?.lastNightDebt ?: 0L) - predictedDuration + (debt?.sleepNeed ?: 480L))

        val recommendedBedtime = if (bedtimeStats != null) {
            val avgLatency = main.take(14).map { it.sleepLatencyMinutes }.average().toLong()
            val sleepNeed = debt?.sleepNeed ?: 480L
            // Target wake time = avg wake time; back-calculate bedtime
            val wakeMinutes = sessions.filterNot { it.isNap }.take(14)
                .map { it.endTime.atZone(ZoneId.systemDefault()).let { z -> z.hour * 60 + z.minute } }
                .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 420
            val bedtimeMinutes = ((wakeMinutes - sleepNeed - avgLatency).toInt() + 1440) % 1440
            LocalTime.of(bedtimeMinutes / 60, bedtimeMinutes % 60)
        } else null

        return SleepPrediction(
            predictedDurationMinutes = predictedDuration,
            predictedScore = avgScore,
            recommendedBedtime = recommendedBedtime,
            debtProjection = debtProjection,
        )
    }

    fun buildWeeklyReport(sessions: List<SleepSession>, weekOffset: Int = 0): WeeklySleepReport? {
        val today = LocalDate.now(ZoneId.systemDefault())
        val targetWeek = today.minusWeeks(weekOffset.toLong())
        val weekNum = targetWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val weekYear = targetWeek.get(IsoFields.WEEK_BASED_YEAR)

        val weekSessions = sessions.filterNot { it.isNap }.filter { session ->
            val d = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == weekNum && d.get(IsoFields.WEEK_BASED_YEAR) == weekYear
        }
        if (weekSessions.isEmpty()) return null

        val weekStart = weekSessions.minOf { it.startTime }.atZone(ZoneId.systemDefault()).with(DayOfWeek.MONDAY).toLocalDate()
        val avgDuration = weekSessions.map { it.totalSleepMinutes }.average().toLong()
        val avgScore = weekSessions.map { it.sleepScore }.average().toInt()
        val avgDebt = weekSessions.map { it.totalSleepMinutes }.sumOf { max(0L, 480L - it) } / weekSessions.size
        val consistency = stdDevOf(weekSessions.map { s ->
            var m = s.startTime.atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
            if (m < 720) m += 1440
            m
        })
        return WeeklySleepReport(
            weekStartDate = weekStart,
            avgDurationMinutes = avgDuration,
            avgScore = avgScore,
            avgDebt = avgDebt,
            consistencyMinutes = consistency,
            bestNightId = weekSessions.maxByOrNull { it.sleepScore }?.id,
            worstNightId = weekSessions.minByOrNull { it.sleepScore }?.id,
            nightCount = weekSessions.size,
        )
    }

    fun buildMonthlyReport(sessions: List<SleepSession>): MonthlySleepReport? {
        val now = LocalDate.now(ZoneId.systemDefault())
        val month = YearMonth.from(now)
        val monthSessions = sessions.filterNot { it.isNap }.filter { session ->
            val d = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(d) == month
        }
        if (monthSessions.isEmpty()) return null

        val weekday = monthSessions.filterNot { isWeekend(it.startTime) }
        val weekend = monthSessions.filter { isWeekend(it.startTime) }
        return MonthlySleepReport(
            month = month,
            avgDurationMinutes = monthSessions.map { it.totalSleepMinutes }.average().toLong(),
            avgScore = monthSessions.map { it.sleepScore }.average().toInt(),
            weekdayAvgMinutes = if (weekday.isEmpty()) 0L else weekday.map { it.totalSleepMinutes }.average().toLong(),
            weekendAvgMinutes = if (weekend.isEmpty()) 0L else weekend.map { it.totalSleepMinutes }.average().toLong(),
            longestSleepMinutes = monthSessions.maxOf { it.totalSleepMinutes },
            shortestSleepMinutes = monthSessions.minOf { it.totalSleepMinutes },
        )
    }

    fun computeCorrelations(
        sessions: List<SleepSession>,
        annotations: Map<String, SleepAnnotation>,
    ): List<SleepCorrelation> {
        val main = sessions.filterNot { it.isNap }
        return SleepFactorTag.values().mapNotNull { tag ->
            val withTag = main.filter { s -> annotations[s.id]?.tags?.contains(tag.key) == true }
            if (withTag.size < 3) return@mapNotNull null
            val withoutTag = main.filter { s -> annotations[s.id]?.tags?.contains(tag.key) != true }
            SleepCorrelation(
                tag = tag.key,
                tagLabel = tag.label,
                sampleSize = withTag.size,
                avgDurationWith = withTag.map { it.totalSleepMinutes }.average().toLong(),
                avgDurationWithout = if (withoutTag.isEmpty()) 0L else withoutTag.map { it.totalSleepMinutes }.average().toLong(),
                avgScoreWith = withTag.map { it.sleepScore }.average().toInt(),
                avgScoreWithout = if (withoutTag.isEmpty()) 0 else withoutTag.map { it.sleepScore }.average().toInt(),
            )
        }
    }

    private fun isWeekend(instant: Instant): Boolean {
        val dow = instant.atZone(ZoneId.systemDefault()).dayOfWeek
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
    }

    private fun stdDevOf(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.map { (it - mean) * (it - mean) }.average())
    }
}
