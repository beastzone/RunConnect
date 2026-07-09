package com.runconnect.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SleepStageMappingTest {

    // 1–7: each HC integer 0–6 maps to correct SleepStageType
    @Test fun `HC stage 0 maps to UNKNOWN`() {
        assertEquals(SleepStageType.UNKNOWN, SleepStageType.fromHealthConnect(0))
    }

    @Test fun `HC stage 1 maps to AWAKE`() {
        assertEquals(SleepStageType.AWAKE, SleepStageType.fromHealthConnect(1))
    }

    @Test fun `HC stage 2 maps to SLEEPING_UNSPECIFIED`() {
        assertEquals(SleepStageType.SLEEPING_UNSPECIFIED, SleepStageType.fromHealthConnect(2))
    }

    @Test fun `HC stage 3 maps to OUT_OF_BED`() {
        assertEquals(SleepStageType.OUT_OF_BED, SleepStageType.fromHealthConnect(3))
    }

    @Test fun `HC stage 4 maps to LIGHT`() {
        assertEquals(SleepStageType.LIGHT, SleepStageType.fromHealthConnect(4))
    }

    @Test fun `HC stage 5 maps to DEEP`() {
        assertEquals(SleepStageType.DEEP, SleepStageType.fromHealthConnect(5))
    }

    @Test fun `HC stage 6 maps to REM`() {
        assertEquals(SleepStageType.REM, SleepStageType.fromHealthConnect(6))
    }

    // 8: unknown integer → UNKNOWN
    @Test fun `HC stage 99 maps to UNKNOWN`() {
        assertEquals(SleepStageType.UNKNOWN, SleepStageType.fromHealthConnect(99))
    }

    private fun makeSession(vararg stagePairs: Pair<SleepStageType, LongRange>): SleepSession {
        val base = Instant.ofEpochSecond(1_700_000_000L)
        val stages = stagePairs.map { (type, range) ->
            SleepStage(
                startTime = base.plusSeconds(range.first * 60),
                endTime = base.plusSeconds(range.last * 60),
                type = type,
            )
        }
        return SleepSession(
            id = "test",
            startTime = base,
            endTime = base.plusSeconds(480 * 60),
            stages = stages,
            heartRateSamples = emptyList(),
            hrvSamples = emptyList(),
            spo2Samples = emptyList(),
            dataOriginPackage = "test.pkg",
        )
    }

    // 9: wasoMinutes includes OUT_OF_BED stages after onset
    @Test fun `wasoMinutes includes OUT_OF_BED after onset`() {
        val session = makeSession(
            SleepStageType.LIGHT to 0L..60L,            // sleep onset here
            SleepStageType.OUT_OF_BED to 60L..80L,      // 20m out of bed = WASO
            SleepStageType.LIGHT to 80L..240L,
        )
        assertTrue("wasoMinutes should include OUT_OF_BED after onset", session.wasoMinutes >= 20)
    }

    // 10: awakeMinutes includes OUT_OF_BED
    @Test fun `awakeMinutes includes OUT_OF_BED stages`() {
        val session = makeSession(
            SleepStageType.AWAKE to 0L..10L,
            SleepStageType.OUT_OF_BED to 10L..25L,
            SleepStageType.LIGHT to 25L..240L,
        )
        assertTrue("awakeMinutes should include both AWAKE and OUT_OF_BED", session.awakeMinutes >= 25)
    }

    // 11: sleepOnset skips OUT_OF_BED, advances to first real-sleep stage
    @Test fun `sleepOnset skips OUT_OF_BED`() {
        val session = makeSession(
            SleepStageType.OUT_OF_BED to 0L..15L,
            SleepStageType.LIGHT to 15L..240L,
        )
        val onset = session.sleepOnset
        assertTrue("sleepOnset should not be null", onset != null)
        if (onset != null) {
            assertTrue("sleepOnset should be at/after the LIGHT stage starts",
                onset.epochSecond >= session.startTime.plusSeconds(15 * 60).epochSecond)
        }
    }

    // 12: totalSleepMinutes includes SLEEPING_UNSPECIFIED (Garmin single-stage)
    @Test fun `totalSleepMinutes includes SLEEPING_UNSPECIFIED`() {
        val session = makeSession(
            SleepStageType.SLEEPING_UNSPECIFIED to 0L..480L,
        )
        assertEquals(480L, session.totalSleepMinutes)
    }
}
