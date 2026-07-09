package com.runconnect.app.data.db

import androidx.room.TypeConverter
import com.runconnect.app.domain.model.LapData
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

class Converters {

    private val moshi: Moshi = Moshi.Builder()
        .add(InstantAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val lapListAdapter = moshi.adapter<List<LapData>>(
        Types.newParameterizedType(List::class.java, LapData::class.java)
    )

    @TypeConverter
    fun lapsFromJson(json: String): List<LapData> =
        lapListAdapter.fromJson(json) ?: emptyList()

    @TypeConverter
    fun lapsToJson(laps: List<LapData>): String =
        lapListAdapter.toJson(laps)

    private class InstantAdapter {
        @ToJson fun toJson(instant: Instant): Long = instant.epochSecond
        @FromJson fun fromJson(value: Long): Instant = Instant.ofEpochSecond(value)
    }
}
