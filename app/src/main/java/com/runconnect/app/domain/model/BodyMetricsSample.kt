package com.runconnect.app.domain.model

import java.time.Instant

data class BodyMetricsSample(
    val timestamp: Instant,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
)
