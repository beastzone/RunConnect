package com.runconnect.app.domain.analytics

import com.runconnect.app.domain.model.HeartRateSample
import com.runconnect.app.domain.model.HeartRateZoneSummary
import com.runconnect.app.domain.model.HrZone
import com.runconnect.app.domain.model.SpeedSample
import com.runconnect.app.domain.model.ZoneModel
import com.runconnect.app.domain.model.computeHrZone
import java.time.Instant
import kotlin.math.abs

object ActivityHrAnalytics {

    data class ZoneDistribution(
        val summaries: List<HeartRateZoneSummary>,
        val totalSamples: Int,
    )

    data class HrDriftResult(
        val driftPercent: Double,
        val firstHalfAvgBpm: Double,
        val secondHalfAvgBpm: Double,
        val isSignificant: Boolean,
    )

    data class AerobicDecouplingResult(
        val paceHrRatio1stHalf: Double,
        val paceHrRatio2ndHalf: Double,
        val decouplingPercent: Double,
        val isWellCoupled: Boolean,
        val historicalDecouplingAvg: Double?,
    )

    data class HrRecoveryResult(
        val peakBpm: Int,
        val at1Min: Int?,
        val at2Min: Int?,
        val at5Min: Int?,
        val drop1Min: Int?,
        val drop2Min: Int?,
        val drop5Min: Int?,
    )

    data class ArtifactSpans(
        val suspectTimestamps: Set<Long>,
        val reasons: Map<Long, String>,
    ) {
        companion object {
            val empty = ArtifactSpans(emptySet(), emptyMap())
        }
    }

    fun computeZones(
        samples: List<HeartRateSample>,
        maxHr: Int,
        model: ZoneModel = ZoneModel.MAX_HR,
        restingHr: Int = 60,
    ): ZoneDistribution {
        if (samples.isEmpty()) return ZoneDistribution(
            summaries = HrZone.entries.map { HeartRateZoneSummary(it, 0L, 0f) },
            totalSamples = 0,
        )

        val counts = mutableMapOf<HrZone, Int>().withDefault { 0 }
        samples.forEach { s ->
            val zone = computeHrZone(s.bpm.toInt(), maxHr, model, restingHr)
            counts[zone] = counts.getValue(zone) + 1
        }

        val total = samples.size
        return ZoneDistribution(
            summaries = HrZone.entries.map { zone ->
                val count = counts.getValue(zone)
                HeartRateZoneSummary(
                    zone = zone,
                    totalMinutes = count.toLong(),
                    percentOfTotal = count.toFloat() / total,
                )
            },
            totalSamples = total,
        )
    }

    fun computeHrDrift(
        samples: List<HeartRateSample>,
        durationSeconds: Long,
    ): HrDriftResult? {
        if (samples.size < 20 || durationSeconds < 600) return null
        val midpoint = samples.first().timestamp.epochSecond + durationSeconds / 2
        val first = samples.filter { it.timestamp.epochSecond < midpoint }
        val second = samples.filter { it.timestamp.epochSecond >= midpoint }
        if (first.isEmpty() || second.isEmpty()) return null

        val firstAvg = first.map { it.bpm }.average()
        val secondAvg = second.map { it.bpm }.average()
        val drift = (secondAvg - firstAvg) / firstAvg * 100.0
        return HrDriftResult(
            driftPercent = drift,
            firstHalfAvgBpm = firstAvg,
            secondHalfAvgBpm = secondAvg,
            isSignificant = abs(drift) > 5.0,
        )
    }

    fun computeAerobicDecoupling(
        hrSamples: List<HeartRateSample>,
        speedSamples: List<SpeedSample>,
        durationSeconds: Long,
        historicalDecouplingAvg: Double? = null,
    ): AerobicDecouplingResult? {
        if (hrSamples.size < 20 || speedSamples.size < 20 || durationSeconds < 1200) return null

        data class Matched(val epoch: Long, val speedMps: Double, val bpm: Double)

        val matched = mutableListOf<Matched>()
        for (hr in hrSamples) {
            val closest = speedSamples.minByOrNull { abs(it.timestamp.epochSecond - hr.timestamp.epochSecond) }
                ?: continue
            if (abs(closest.timestamp.epochSecond - hr.timestamp.epochSecond) > 10) continue
            if (closest.speedMps < 0.5) continue
            matched.add(Matched(hr.timestamp.epochSecond, closest.speedMps, hr.bpm.toDouble()))
        }
        if (matched.size < 20) return null

        val startEpoch = matched.first().epoch
        val midEpoch = startEpoch + durationSeconds / 2
        val first = matched.filter { it.epoch < midEpoch }
        val second = matched.filter { it.epoch >= midEpoch }
        if (first.isEmpty() || second.isEmpty()) return null

        val ratio1 = first.map { it.speedMps / it.bpm }.average()
        val ratio2 = second.map { it.speedMps / it.bpm }.average()
        val decoupling = (ratio2 - ratio1) / ratio1 * 100.0

        return AerobicDecouplingResult(
            paceHrRatio1stHalf = ratio1,
            paceHrRatio2ndHalf = ratio2,
            decouplingPercent = decoupling,
            isWellCoupled = abs(decoupling) < 5.0,
            historicalDecouplingAvg = historicalDecouplingAvg,
        )
    }

    fun computeEndOfActivityRecovery(
        samples: List<HeartRateSample>,
        activityEndTime: Instant,
    ): HrRecoveryResult? {
        if (samples.size < 10) return null

        val endEpoch = activityEndTime.epochSecond
        val windowStart = endEpoch - 300
        val finalSamples = samples.filter { it.timestamp.epochSecond in windowStart..endEpoch }
        if (finalSamples.isEmpty()) return null

        val peakBpm = finalSamples.maxOf { it.bpm }.toInt()

        fun bpmAt(offsetSeconds: Long): Int? {
            val target = endEpoch + offsetSeconds
            return samples
                .filter { abs(it.timestamp.epochSecond - target) <= 15 }
                .minByOrNull { abs(it.timestamp.epochSecond - target) }
                ?.bpm?.toInt()
        }

        val at1 = bpmAt(60)
        val at2 = bpmAt(120)
        val at5 = bpmAt(300)

        return HrRecoveryResult(
            peakBpm = peakBpm,
            at1Min = at1,
            at2Min = at2,
            at5Min = at5,
            drop1Min = at1?.let { peakBpm - it },
            drop2Min = at2?.let { peakBpm - it },
            drop5Min = at5?.let { peakBpm - it },
        )
    }

    fun detectArtifacts(samples: List<HeartRateSample>): ArtifactSpans {
        if (samples.size < 5) return ArtifactSpans.empty

        val suspects = mutableMapOf<Long, String>()
        val sorted = samples.sortedBy { it.timestamp.epochSecond }

        for (i in 1 until sorted.size - 1) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            val next = sorted[i + 1]
            val epoch = cur.timestamp.epochSecond

            // Spike: sudden rise then immediate drop
            if (cur.bpm > prev.bpm * 1.4 && next.bpm < cur.bpm * 0.8) {
                suspects[epoch] = "spike"
                continue
            }

            // Dropout: large gap
            if (epoch - prev.timestamp.epochSecond > 120) {
                suspects[epoch] = "dropout"
                continue
            }
        }

        // Flatline: 5+ consecutive identical values over 60s
        var flatStart = 0
        for (i in 1 until sorted.size) {
            if (sorted[i].bpm == sorted[flatStart].bpm) {
                val span = sorted[i].timestamp.epochSecond - sorted[flatStart].timestamp.epochSecond
                if (i - flatStart >= 4 && span >= 60) {
                    for (j in flatStart..i) {
                        suspects[sorted[j].timestamp.epochSecond] = "flatline"
                    }
                }
            } else {
                flatStart = i
            }
        }

        // Cadence lock: 3+ consecutive samples in common cadence-lock band (170–200 at a flat value)
        for (i in 1 until sorted.size - 1) {
            val bpm = sorted[i].bpm
            if (bpm in 150..200) {
                val prev = sorted[i - 1].bpm
                val next = sorted[i + 1].bpm
                if (abs(bpm - prev) <= 2 && abs(bpm - next) <= 2 &&
                    bpm != 0L && (bpm % 5L == 0L || bpm % 6L == 0L)
                ) {
                    suspects[sorted[i].timestamp.epochSecond] = "cadence_lock"
                }
            }
        }

        return ArtifactSpans(suspects.keys.toSet(), suspects)
    }

    fun computeEfficiency(
        samples: List<HeartRateSample>,
        speedSamples: List<SpeedSample>,
    ): Double? {
        if (samples.isEmpty() || speedSamples.isEmpty()) return null

        val pairs = mutableListOf<Pair<Double, Double>>()
        for (hr in samples) {
            val closest = speedSamples.minByOrNull { abs(it.timestamp.epochSecond - hr.timestamp.epochSecond) }
                ?: continue
            if (abs(closest.timestamp.epochSecond - hr.timestamp.epochSecond) > 15) continue
            if (closest.speedMps < 0.5 || hr.bpm <= 0) continue
            pairs.add(closest.speedMps to hr.bpm.toDouble())
        }
        if (pairs.size < 10) return null

        return pairs.map { (speed, bpm) -> speed / bpm }.average()
    }
}
