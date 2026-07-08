package com.runconnect.app.domain.model

import java.time.LocalDate

data class DailyStats(
    val date: LocalDate,
    val steps: Long = 0L,
    val activeCaloriesKcal: Double = 0.0,
    val restingHeartRate: Int? = null,
    val hrvRmssd: Double? = null,
)
