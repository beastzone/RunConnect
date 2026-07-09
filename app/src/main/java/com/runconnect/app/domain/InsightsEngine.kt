package com.runconnect.app.domain

import com.runconnect.app.domain.analytics.ActivityHrAnalytics
import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.DailyStats
import com.runconnect.app.domain.model.HealthInsight
import com.runconnect.app.domain.model.InsightPriority
import com.runconnect.app.domain.model.InsightType
import com.runconnect.app.domain.model.SleepSession
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsEngine @Inject constructor() {

    fun generateInsights(
        activities: List<Activity>,
        sleepSessions: List<SleepSession>,
        dailyStats: List<DailyStats>,
    ): List<HealthInsight> {
        val insights = mutableListOf<HealthInsight>()
        sleepInsights(sleepSessions, insights)
        trainingInsights(activities, insights)
        recoveryInsights(dailyStats, insights)
        hrRecoveryTrendInsight(activities, insights)
        return insights.sortedBy { it.priority.ordinal }.take(5)
    }

    private fun sleepInsights(sessions: List<SleepSession>, out: MutableList<HealthInsight>) {
        if (sessions.isEmpty()) return
        val recent = sessions.take(7)
        val avgDuration = recent.map { it.totalDurationMinutes }.average()
        val avgDeepPct = recent.map {
            it.deepSleepMinutes.toDouble() / it.totalDurationMinutes.coerceAtLeast(1)
        }.average()

        when {
            avgDuration < 360 -> out.add(HealthInsight(
                type = InsightType.SLEEP,
                priority = InsightPriority.HIGH,
                title = "Short Sleep Pattern",
                body = "You're averaging ${avgDuration.toInt() / 60}h ${avgDuration.toInt() % 60}m per night. 7–9 hours supports recovery and performance.",
                actionHint = "Try a consistent bedtime 30 minutes earlier.",
            ))
            avgDuration > 540 -> out.add(HealthInsight(
                type = InsightType.SLEEP,
                priority = InsightPriority.LOW,
                title = "Longer Than Usual Sleep",
                body = "You're averaging over 9 hours — this can indicate fatigue or overtraining.",
            ))
        }

        if (avgDeepPct < 0.15 && recent.size >= 3) {
            out.add(HealthInsight(
                type = InsightType.SLEEP,
                priority = InsightPriority.MEDIUM,
                title = "Low Deep Sleep",
                body = "Deep sleep is ${(avgDeepPct * 100).toInt()}% of your total. Aim for 20%+ — deep sleep drives physical recovery.",
                actionHint = "Avoid alcohol and screens 1h before bed.",
            ))
        }
    }

    private fun trainingInsights(activities: List<Activity>, out: MutableList<HealthInsight>) {
        if (activities.isEmpty()) return
        val now = LocalDate.now()
        val weekStart = now.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val prevWeekStart = now.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val thisWeekKm = activities.filter { it.startTime.isAfter(weekStart) }
            .sumOf { it.distanceMeters } / 1000.0
        val lastWeekKm = activities
            .filter { it.startTime.isAfter(prevWeekStart) && it.startTime.isBefore(weekStart) }
            .sumOf { it.distanceMeters } / 1000.0

        if (lastWeekKm > 5 && thisWeekKm > lastWeekKm * 1.10) {
            val pctIncrease = ((thisWeekKm / lastWeekKm - 1) * 100).toInt()
            out.add(HealthInsight(
                type = InsightType.TRAINING,
                priority = InsightPriority.HIGH,
                title = "Training Load Up $pctIncrease%",
                body = "This week (${String.format("%.1f", thisWeekKm)}km) is $pctIncrease% more than last week. Increasing more than 10% raises injury risk.",
                actionHint = "Consider swapping a hard run for an easy recovery session.",
            ))
        }

        val activeDays = activities
            .filter { it.startTime.isAfter(now.minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant()) }
            .map { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .toSet()

        if (activeDays.size >= 7) {
            out.add(HealthInsight(
                type = InsightType.CONSISTENCY,
                priority = InsightPriority.LOW,
                title = "Strong Consistency",
                body = "You've been active ${activeDays.size} of the last 14 days. Consistency is the top predictor of long-term performance.",
            ))
        } else {
            val daysSinceLast = now.toEpochDay() -
                activities.first().startTime.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            if (daysSinceLast >= 3) {
                out.add(HealthInsight(
                    type = InsightType.TRAINING,
                    priority = InsightPriority.MEDIUM,
                    title = "${daysSinceLast} Days Since Last Activity",
                    body = "An easy run or walk today helps maintain your aerobic base.",
                ))
            }
        }
    }

    private fun recoveryInsights(dailyStats: List<DailyStats>, out: MutableList<HealthInsight>) {
        val withRhr = dailyStats.mapNotNull { it.restingHeartRate }
        if (withRhr.size >= 7) {
            val recentAvg = withRhr.take(3).average()
            val baselineAvg = withRhr.average()
            if (recentAvg > baselineAvg + 5) {
                out.add(HealthInsight(
                    type = InsightType.RECOVERY,
                    priority = InsightPriority.HIGH,
                    title = "Elevated Resting HR",
                    body = "Last 3-day avg RHR is ${recentAvg.toInt()} bpm — ${(recentAvg - baselineAvg).toInt()} bpm above your baseline. A sign of incomplete recovery.",
                    actionHint = "Prioritize sleep and consider a rest day.",
                ))
            }
        }

        val withHrv = dailyStats.mapNotNull { it.hrvRmssd }
        if (withHrv.size >= 7) {
            val recentHrvAvg = withHrv.take(3).average()
            val baselineHrvAvg = withHrv.average()
            if (recentHrvAvg < baselineHrvAvg * 0.85) {
                out.add(HealthInsight(
                    type = InsightType.RECOVERY,
                    priority = InsightPriority.MEDIUM,
                    title = "HRV Below Baseline",
                    body = "Recent HRV (${String.format("%.0f", recentHrvAvg)}ms) is below your ${String.format("%.0f", baselineHrvAvg)}ms baseline. Higher HRV means better recovery readiness.",
                    actionHint = "Easy training and 8h sleep will restore HRV.",
                ))
            }
        }
    }

    private fun hrRecoveryTrendInsight(activities: List<Activity>, out: MutableList<HealthInsight>) {
        // Require at least 8 activities with HR samples and enough duration for a post-workout window
        val eligible = activities
            .filter { it.heartRateSamples.size >= 20 && it.durationSeconds >= 1200 }
            .takeLast(10)
        if (eligible.size < 6) return

        val drops = eligible.mapNotNull { activity ->
            runCatching {
                ActivityHrAnalytics.computeEndOfActivityRecovery(
                    activity.heartRateSamples, activity.endTime
                )?.drop1Min
            }.getOrNull()
        }
        if (drops.size < 6) return

        val recentAvg = drops.takeLast(3).average()
        val olderAvg = drops.take(drops.size - 3).average()

        if (olderAvg > 10 && recentAvg < olderAvg * 0.70) {
            out.add(HealthInsight(
                type = InsightType.RECOVERY,
                priority = InsightPriority.MEDIUM,
                title = "HR Recovery Slowing",
                body = "Your 1-min post-workout HR drop has fallen from ${olderAvg.toInt()} to ${recentAvg.toInt()} bpm recently. Slower recovery can signal accumulated fatigue.",
                actionHint = "Check the Heart Rate screen for your recovery trend.",
            ))
        }
    }
}
