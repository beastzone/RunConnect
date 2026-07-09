package com.runconnect.app.domain.scoring

import com.runconnect.app.domain.model.SleepConfidence
import com.runconnect.app.domain.model.SleepScoreResult
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.processing.SleepStageProcessor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SleepScoreCalculator {

    fun calculate(
        session: SleepSession,
        history: List<SleepSession>,
        targetMinutes: Long = 480L,
    ): SleepScoreResult {
        val available = mutableSetOf<String>()
        val rawScores = mutableMapOf<String, Double>()

        // Duration (always available)
        val durationRatio = session.totalSleepMinutes.toDouble() / targetMinutes.toDouble()
        rawScores["duration"] = (durationRatio / 1.1).coerceAtMost(1.0) * 100.0
        available.add("duration")

        // Efficiency (always available)
        rawScores["efficiency"] = (session.sleepEfficiencyPercent / 90.0).coerceAtMost(1.0) * 100.0
        available.add("efficiency")

        // Continuity (requires 40% stage coverage)
        if (SleepStageProcessor.hasCoverageAtLeast(session.stages, session, 40f)) {
            val wasoScore = 100.0 - (session.wasoMinutes * 1.5).coerceIn(0.0, 100.0)
            val lat = session.sleepLatencyMinutes.toDouble()
            val latencyScore = when {
                lat <= 5.0 -> 100.0
                lat <= 30.0 -> 100.0 - (lat - 5.0) * 2.0
                else -> max(0.0, 50.0 - (lat - 30.0) * 2.0)
            }
            val awakeningScore = 100.0 - (session.awakeningCount * 8).coerceIn(0, 100)
            rawScores["continuity"] = wasoScore * 0.50 + latencyScore * 0.25 + awakeningScore * 0.25
            available.add("continuity")
        }

        // Consistency (requires >= 7 historical main sessions)
        val mainHistory = history.filterNot { it.isNap }.take(14)
        if (mainHistory.size >= 7) {
            val bedtimesMinutes = mainHistory.map { s ->
                val minutesPastMidnight = s.startTime.atZone(java.time.ZoneId.systemDefault())
                    .let { it.hour * 60 + it.minute }
                // Evening times (after noon): add 1440 so midnight wraps correctly
                if (minutesPastMidnight < 720) minutesPastMidnight + 1440 else minutesPastMidnight
            }
            val mean = bedtimesMinutes.average()
            val variance = bedtimesMinutes.map { (it - mean) * (it - mean) }.average()
            val stdDev = sqrt(variance)
            rawScores["consistency"] = max(0.0, 100.0 - stdDev / 1.2)
            available.add("consistency")
        }

        // Stages (requires 80% stage coverage)
        if (SleepStageProcessor.hasCoverageAtLeast(session.stages, session, 80f) &&
            session.totalSleepMinutes > 0) {
            val deepPct = session.deepSleepMinutes.toDouble() / session.totalSleepMinutes
            val remPct = session.remSleepMinutes.toDouble() / session.totalSleepMinutes
            val deepScore = min(deepPct / 0.20, 1.0) * 100.0
            val remScore = min(remPct / 0.20, 1.0) * 100.0
            rawScores["stages"] = deepScore * 0.5 + remScore * 0.5
            available.add("stages")
        }

        // Recovery (requires >= 5 HR samples)
        val avgHr = session.avgHeartRate
        if (avgHr != null && session.heartRateSamples.size >= 5) {
            val historicalHrs = history.mapNotNull { it.avgHeartRate }
            val baseline = if (historicalHrs.size >= 3) historicalHrs.average() else avgHr * 1.05
            rawScores["recovery"] = (100.0 - max(0.0, (avgHr - baseline) / baseline * 200.0))
                .coerceIn(0.0, 100.0)
            available.add("recovery")
        }

        val weights = SleepScoreWeights.redistribute(available)
        val finalScore = available.sumOf { key -> (rawScores[key] ?: 0.0) * (weights[key] ?: 0.0) }
            .roundToInt().coerceIn(0, 100)

        val confidence = when {
            available.size == 6 -> SleepConfidence.HIGH
            available.size >= 3 -> SleepConfidence.MEDIUM
            else -> SleepConfidence.LOW
        }

        val rating = when {
            finalScore >= 90 -> "Excellent"
            finalScore >= 80 -> "Good"
            finalScore >= 70 -> "Fair"
            finalScore >= 60 -> "Low"
            else -> "Poor"
        }

        return SleepScoreResult(
            score = finalScore,
            rating = rating,
            durationScore = rawScores["duration"]?.roundToInt(),
            durationWeight = weights["duration"] ?: 0.0,
            efficiencyScore = rawScores["efficiency"]?.roundToInt(),
            efficiencyWeight = weights["efficiency"] ?: 0.0,
            continuityScore = rawScores["continuity"]?.roundToInt(),
            continuityWeight = weights["continuity"] ?: 0.0,
            consistencyScore = rawScores["consistency"]?.roundToInt(),
            consistencyWeight = weights["consistency"] ?: 0.0,
            stageScore = rawScores["stages"]?.roundToInt(),
            stageWeight = weights["stages"] ?: 0.0,
            recoveryScore = rawScores["recovery"]?.roundToInt(),
            recoveryWeight = weights["recovery"] ?: 0.0,
            confidence = confidence,
            modelVersion = SleepScoreWeights.MODEL_VERSION,
        )
    }

    private fun Double.coerceIn(min: Int, max: Int): Double = this.coerceIn(min.toDouble(), max.toDouble())
}
