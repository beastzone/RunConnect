package com.runconnect.app.data.remote.garmin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GarminActivitySummary(
    @Json(name = "activityId") val activityId: Long,
    @Json(name = "activityName") val activityName: String,
    @Json(name = "startTimeGMT") val startTimeGmt: String,
    @Json(name = "activityType") val activityType: GarminActivityType?,
    @Json(name = "distance") val distance: Double?,
    @Json(name = "duration") val duration: Double?,
    @Json(name = "movingDuration") val movingDuration: Double?,
    @Json(name = "elevationGain") val elevationGain: Double?,
    @Json(name = "averageHR") val averageHr: Int?,
    @Json(name = "maxHR") val maxHr: Int?,
    @Json(name = "averageSpeed") val averageSpeed: Double?,
    @Json(name = "calories") val calories: Int?,
    @Json(name = "steps") val steps: Int?,
)

@JsonClass(generateAdapter = true)
data class GarminActivityType(
    @Json(name = "typeKey") val typeKey: String,
    @Json(name = "typeId") val typeId: Int,
)

@JsonClass(generateAdapter = true)
data class GarminActivityDetail(
    @Json(name = "activityId") val activityId: Long,
    @Json(name = "activitySummary") val summary: GarminDetailSummary?,
    @Json(name = "geoPolylineDTO") val geoPolyline: GarminGeoPolyline?,
    @Json(name = "splitSummaries") val splitSummaries: List<GarminSplitSummary>?,
)

@JsonClass(generateAdapter = true)
data class GarminDetailSummary(
    @Json(name = "BeginTimestamp") val beginTimestamp: GarminMetricValue?,
    @Json(name = "SumElapsedDuration") val elapsedDuration: GarminMetricValue?,
    @Json(name = "SumDistance") val distance: GarminMetricValue?,
    @Json(name = "WeightedMeanSpeed") val avgSpeed: GarminMetricValue?,
    @Json(name = "MaximumSpeed") val maxSpeed: GarminMetricValue?,
    @Json(name = "WeightedMeanHeartRate") val avgHr: GarminMetricValue?,
    @Json(name = "MaximumHeartRate") val maxHr: GarminMetricValue?,
    @Json(name = "SumElevationGain") val elevationGain: GarminMetricValue?,
    @Json(name = "SumCalories") val calories: GarminMetricValue?,
)

@JsonClass(generateAdapter = true)
data class GarminMetricValue(
    @Json(name = "value") val value: Double?,
    @Json(name = "uom") val unit: String?,
)

@JsonClass(generateAdapter = true)
data class GarminGeoPolyline(
    @Json(name = "polyline") val polyline: List<GarminCoordinate>?,
)

@JsonClass(generateAdapter = true)
data class GarminCoordinate(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double,
)

@JsonClass(generateAdapter = true)
data class GarminSplitSummary(
    @Json(name = "distance") val distance: Double?,
    @Json(name = "duration") val duration: Double?,
    @Json(name = "averageHR") val avgHr: Double?,
    @Json(name = "averageSpeed") val avgSpeed: Double?,
    @Json(name = "noOfSplits") val noOfSplits: Int?,
    @Json(name = "splitType") val splitType: String?,
)

@JsonClass(generateAdapter = true)
data class GarminOAuthToken(
    val token: String,
    val tokenSecret: String,
)
