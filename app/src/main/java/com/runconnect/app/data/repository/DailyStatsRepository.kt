package com.runconnect.app.data.repository

import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.domain.model.DailyStats
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyStatsRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
) {
    suspend fun getDailyStats(days: Int = 30): List<DailyStats> {
        // Fetch restingHR and HRV for the whole range, then aggregate per day
        val restingHrHistory = runCatching {
            healthConnectManager.readRestingHeartRateHistory(days)
        }.getOrDefault(emptyList())

        val hrvHistory = runCatching {
            healthConnectManager.readHrvHistory(days)
        }.getOrDefault(emptyList())

        val restingHrByDate = restingHrHistory.groupBy { (ts, _) ->
            ts.atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { (_, entries) -> entries.map { it.second }.average().toInt() }

        val hrvByDate = hrvHistory.groupBy { (ts, _) ->
            ts.atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { (_, entries) -> entries.map { it.second }.average() }

        return (0 until days).map { daysAgo ->
            val date = LocalDate.now().minusDays(daysAgo.toLong())
            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val steps = runCatching {
                healthConnectManager.readStepsForRange(dayStart, dayEnd)
            }.getOrDefault(0L)

            val calories = runCatching {
                healthConnectManager.readActiveCaloriesForRange(dayStart, dayEnd)
            }.getOrDefault(0.0)

            DailyStats(
                date = date,
                steps = steps,
                activeCaloriesKcal = calories,
                restingHeartRate = restingHrByDate[date],
                hrvRmssd = hrvByDate[date],
            )
        }.sortedByDescending { it.date }
    }
}
