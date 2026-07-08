package com.runconnect.app.domain.model

data class RacePrediction(
    val distanceLabel: String,
    val distanceMeters: Double,
    val predictedSeconds: Long,
    val predictedPaceSecondsPerKm: Double,
    val personalBestSeconds: Long? = null,
)

enum class RaceDistance(
    val label: String,
    val meters: Double,
) {
    ONE_MILE("1 Mile", 1609.344),
    FIVE_K("5K", 5000.0),
    TEN_K("10K", 10000.0),
    HALF_MARATHON("Half Marathon", 21097.5),
    MARATHON("Marathon", 42195.0);
}
