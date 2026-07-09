package com.runconnect.app.domain.scoring

import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStage
import com.runconnect.app.domain.model.SleepStageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SleepScoreCalculatorTest {

    private val base = Instant.ofEpochSecond(1_700_000_000L)

    private fun makeSession(
        durationMinutes: Long = 480,
        stages: List<SleepStage> = emptyList(),
        hrSamples: List<Pair<Instant, Int>> = emptyList(),
    ): SleepSession {
        return SleepSession(
            id = "test",
            startTime = base,
            endTime = base.plusSeconds(durationMinutes * 60),
            stages = stages,
            heartRateSamples = hrSamples,
        )
    }

    private fun stagesForFullNight(deepPct: Double = 0.20, remPct: Double = 0.20): List<SleepStage> {
        val totalMinutes = 480L
        val deepMins = (totalMinutes * deepPct).toLong()
        val remMins = (totalMinutes * remPct).toLong()
        val lightMins = totalMinutes - deepMins - remMins
        var cursor = 0L
        fun seg(type: SleepStageType, mins: Long): SleepStage {
            val s = SleepStage(base.plusSeconds(cursor * 60), base.plusSeconds((cursor + mins) * 60), type)
            cursor += mins
            return s
        }
        return listOf(seg(SleepStageType.LIGHT, lightMins), seg(SleepStageType.DEEP, deepMins), seg(SleepStageType.REM, remMins))
    }

    // 1: Perfect 8h session → score ≥ 85
    @Test fun `perfect 8h session scores above 85`() {
        val hr = (0 until 20).map { base.plusSeconds(it * 1200L) to 55 }
        val session = makeSession(stages = stagesForFullNight(), hrSamples = hr)
        val history = List(10) { makeSession(stages = stagesForFullNight(), hrSamples = hr) }
        val result = SleepScoreCalculator.calculate(session, history, 480L)
        assertTrue("Expected score ≥ 85, got ${result.score}", result.score >= 85)
    }

    // 2: 2h session → score ≤ 40
    @Test fun `2h session scores at most 40`() {
        val session = makeSession(durationMinutes = 120)
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertTrue("Expected score ≤ 40, got ${result.score}", result.score <= 40)
    }

    // 3: Duration at target (480m / 480m target) → durationScore = 100
    @Test fun `duration exactly at target yields durationScore 100`() {
        val session = makeSession(durationMinutes = 480)
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertEquals(100, result.durationScore)
    }

    // 4: Duration at 75% of target → durationScore < 85
    @Test fun `duration at 75 percent target yields durationScore below 85`() {
        val session = makeSession(durationMinutes = 360)  // 360/480 = 75%
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertTrue("Expected durationScore < 85, got ${result.durationScore}", (result.durationScore ?: 100) < 85)
    }

    // 5: 90% efficiency → efficiencyScore = 100
    @Test fun `90 percent efficiency yields efficiencyScore 100`() {
        // 432 asleep out of 480 in-bed = 90%
        val stages = listOf(
            SleepStage(base, base.plusSeconds(432 * 60), SleepStageType.LIGHT),
        )
        val session = makeSession(stages = stages)
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertEquals(100, result.efficiencyScore)
    }

    // 6: 45% efficiency → efficiencyScore ≤ 55
    @Test fun `45 percent efficiency yields efficiencyScore at most 55`() {
        val stages = listOf(
            SleepStage(base, base.plusSeconds(216 * 60), SleepStageType.LIGHT),
        )
        val session = makeSession(stages = stages)
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertTrue("Expected efficiencyScore ≤ 55, got ${result.efficiencyScore}", (result.efficiencyScore ?: 100) <= 55)
    }

    // 7: 90-min WASO decreases score ≥ 10 vs same session with 0 WASO
    @Test fun `high WASO reduces score by at least 10`() {
        fun makeWithWaso(wasoMinutes: Long): SleepSession {
            val totalMins = 480L
            var cursor = 0L
            fun seg(type: SleepStageType, mins: Long): SleepStage {
                val s = SleepStage(base.plusSeconds(cursor * 60), base.plusSeconds((cursor + mins) * 60), type)
                cursor += mins
                return s
            }
            val stagelist = if (wasoMinutes > 0) {
                listOf(
                    seg(SleepStageType.LIGHT, 100),
                    seg(SleepStageType.AWAKE, wasoMinutes),
                    seg(SleepStageType.LIGHT, totalMins - 100 - wasoMinutes),
                )
            } else {
                listOf(seg(SleepStageType.LIGHT, totalMins))
            }
            return makeSession(stages = stagelist)
        }
        val goodScore = SleepScoreCalculator.calculate(makeWithWaso(0), emptyList(), 480L).score
        val badScore = SleepScoreCalculator.calculate(makeWithWaso(90), emptyList(), 480L).score
        assertTrue("90-min WASO should reduce score by ≥10 (was $goodScore vs $badScore)", goodScore - badScore >= 10)
    }

    // 8: redistribute(2 keys).values.sum() ≈ 1.0
    @Test fun `redistribute two components sums to 1`() {
        val weights = SleepScoreWeights.redistribute(setOf("duration", "efficiency"))
        assertEquals(2, weights.size)
        val sum = weights.values.sum()
        assertTrue("Expected sum ≈ 1.0, got $sum", kotlin.math.abs(sum - 1.0) < 0.001)
    }

    // 9: redistribute({"duration"})["duration"] == 1.0
    @Test fun `redistribute single component yields weight 1`() {
        val weights = SleepScoreWeights.redistribute(setOf("duration"))
        assertEquals(1.0, weights["duration"]!!, 0.001)
    }

    // 10: No stages → stageScore == null in result
    @Test fun `no stages means stageScore is null`() {
        val session = makeSession(stages = emptyList())
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertNull(result.stageScore)
    }

    // 11: Edge case (0 duration, no stages, no HR) → score in [0, 100]
    @Test fun `zero duration session produces valid score`() {
        val session = makeSession(durationMinutes = 0)
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertTrue("Score must be in [0, 100], got ${result.score}", result.score in 0..100)
    }

    // 12: result.modelVersion == SleepScoreWeights.MODEL_VERSION
    @Test fun `result model version matches weights model version`() {
        val session = makeSession()
        val result = SleepScoreCalculator.calculate(session, emptyList(), 480L)
        assertEquals(SleepScoreWeights.MODEL_VERSION, result.modelVersion)
    }
}
