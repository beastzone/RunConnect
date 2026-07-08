package com.runconnect.app.data.repository

import com.runconnect.app.data.healthconnect.HealthConnectManager
import com.runconnect.app.domain.model.BodyMetricsSample
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyMetricsRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
) {
    // Returns merged weight+body fat samples sorted newest first.
    suspend fun getBodyMetrics(days: Int = 90): List<BodyMetricsSample> {
        val weights = runCatching { healthConnectManager.readWeightHistory(days) }.getOrDefault(emptyList())
        val bodyFats = runCatching { healthConnectManager.readBodyFatHistory(days) }.getOrDefault(emptyList())

        // Merge by day: pair the closest weight + body fat measurement within the same day
        val weightByDate = weights.associateBy {
            it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val fatByDate = bodyFats.associateBy {
            it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
        }

        val allDates = (weightByDate.keys + fatByDate.keys).toSet().sorted().reversed()

        return allDates.map { date ->
            val w = weightByDate[date]
            val f = fatByDate[date]
            BodyMetricsSample(
                timestamp = w?.timestamp ?: f?.timestamp ?: Instant.now(),
                weightKg = w?.weightKg,
                bodyFatPercent = f?.bodyFatPercent,
            )
        }
    }

    suspend fun getLatestWeight(): BodyMetricsSample? =
        runCatching { healthConnectManager.readWeightHistory(90) }.getOrDefault(emptyList()).firstOrNull()

    suspend fun getLatestBodyFat(): BodyMetricsSample? =
        runCatching { healthConnectManager.readBodyFatHistory(90) }.getOrDefault(emptyList()).firstOrNull()
}
