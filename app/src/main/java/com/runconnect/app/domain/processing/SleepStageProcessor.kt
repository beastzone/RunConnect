package com.runconnect.app.domain.processing

import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStage
import java.time.Duration
import java.time.Instant

object SleepStageProcessor {

    fun computeUnionCoverage(stages: List<SleepStage>, sessionStart: Instant, sessionEnd: Instant): Duration {
        if (stages.isEmpty()) return Duration.ZERO
        val sorted = stages.sortedBy { it.startTime }
        var totalSeconds = 0L
        var currentStart = sorted[0].startTime
        var currentEnd = sorted[0].endTime
        for (i in 1 until sorted.size) {
            val s = sorted[i]
            if (s.startTime <= currentEnd) {
                if (s.endTime > currentEnd) currentEnd = s.endTime
            } else {
                totalSeconds += currentEnd.epochSecond - currentStart.epochSecond
                currentStart = s.startTime
                currentEnd = s.endTime
            }
        }
        totalSeconds += currentEnd.epochSecond - currentStart.epochSecond
        return Duration.ofSeconds(totalSeconds)
    }

    fun computeStageCoveragePercent(stages: List<SleepStage>, session: SleepSession): Float {
        val sessionDurationSec = (session.endTime.epochSecond - session.startTime.epochSecond).toFloat()
        if (sessionDurationSec <= 0) return 0f
        val covered = computeUnionCoverage(stages, session.startTime, session.endTime).seconds.toFloat()
        return (covered / sessionDurationSec * 100f).coerceIn(0f, 100f)
    }

    fun hasCoverageAtLeast(stages: List<SleepStage>, session: SleepSession, minPercent: Float): Boolean {
        return computeStageCoveragePercent(stages, session) >= minPercent
    }
}
