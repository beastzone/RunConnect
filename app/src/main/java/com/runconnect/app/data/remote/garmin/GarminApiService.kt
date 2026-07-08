package com.runconnect.app.data.remote.garmin

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GarminApiService {

    @GET("activitylist-service/activities/search/activities")
    suspend fun getActivities(
        @Query("start") start: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("activityType") activityType: String? = null,
    ): List<GarminActivitySummary>

    @GET("activity-service/activity/{activityId}/details")
    suspend fun getActivityDetails(
        @Path("activityId") activityId: Long,
        @Query("maxChartSize") maxChartSize: Int = 2000,
    ): GarminActivityDetail

    @GET("activity-service/activity/{activityId}/splits")
    suspend fun getActivitySplits(
        @Path("activityId") activityId: Long,
    ): GarminActivityDetail
}
