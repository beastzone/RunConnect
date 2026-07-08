package com.runconnect.app.utils

import com.runconnect.app.domain.model.Activity
import com.runconnect.app.domain.model.ActivityType
import com.runconnect.app.domain.model.RaceDistance
import com.runconnect.app.domain.model.RacePrediction
import com.runconnect.app.domain.model.averagePaceSecondsPerKm
import kotlin.math.pow

object RacePredictionCalculator {

    // Pete Riegel formula: T2 = T1 × (D2/D1)^1.06
    private const val RIEGEL_EXPONENT = 1.06

    fun predict(
        referenceActivity: Activity,
        personalBests: Map<RaceDistance, Long> = emptyMap(),
    ): List<RacePrediction> {
        val refDistanceM = referenceActivity.distanceMeters
        val refDurationS = referenceActivity.durationSeconds.toDouble()

        if (refDistanceM <= 0 || refDurationS <= 0) return emptyList()

        return RaceDistance.values().map { race ->
            val predictedSeconds = riegel(
                t1Seconds = refDurationS,
                d1Meters = refDistanceM,
                d2Meters = race.meters,
            )
            val predictedPace = if (race.meters > 0) predictedSeconds / (race.meters / 1000.0) else 0.0
            RacePrediction(
                distanceLabel = race.label,
                distanceMeters = race.meters,
                predictedSeconds = predictedSeconds.toLong(),
                predictedPaceSecondsPerKm = predictedPace,
                personalBestSeconds = personalBests[race],
            )
        }
    }

    private fun riegel(t1Seconds: Double, d1Meters: Double, d2Meters: Double): Double =
        t1Seconds * (d2Meters / d1Meters).pow(RIEGEL_EXPONENT)

    fun findBestRunForPrediction(activities: List<Activity>): Activity? =
        activities
            .filter { it.type == ActivityType.RUNNING && it.distanceMeters >= 1000 }
            .maxByOrNull { it.distanceMeters }

    fun extractPersonalBests(activities: List<Activity>): Map<RaceDistance, Long> {
        val runActivities = activities.filter {
            it.type == ActivityType.RUNNING && it.durationSeconds > 0
        }
        return RaceDistance.values().associateWith { race ->
            runActivities
                .filter { it.distanceMeters >= race.meters * 0.95 && it.distanceMeters <= race.meters * 1.05 }
                .minByOrNull { it.durationSeconds }
                ?.durationSeconds ?: return@associateWith 0L
        }.filter { it.value > 0 }
    }
}
