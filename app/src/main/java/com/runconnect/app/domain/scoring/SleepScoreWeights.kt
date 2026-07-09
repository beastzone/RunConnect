package com.runconnect.app.domain.scoring

object SleepScoreWeights {
    const val MODEL_VERSION = 1

    private val nominal = mapOf(
        "duration" to 0.35,
        "efficiency" to 0.25,
        "continuity" to 0.15,
        "consistency" to 0.10,
        "stages" to 0.10,
        "recovery" to 0.05,
    )

    fun redistribute(available: Set<String>): Map<String, Double> {
        val kept = nominal.filterKeys { it in available }
        val total = kept.values.sum()
        return if (total == 0.0) kept else kept.mapValues { it.value / total }
    }

    fun nominalWeight(key: String): Double = nominal[key] ?: 0.0
}
