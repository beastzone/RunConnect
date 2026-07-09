package com.runconnect.app.data.db.mappers

import com.runconnect.app.data.db.entities.SleepSessionEntity
import com.runconnect.app.data.db.entities.SleepStageEntity
import com.runconnect.app.domain.model.DataSource
import com.runconnect.app.domain.model.SleepSession
import com.runconnect.app.domain.model.SleepStage
import com.runconnect.app.domain.model.SleepStageType
import java.time.Instant

fun SleepSession.toEntity(syncedAtEpoch: Long): SleepSessionEntity = SleepSessionEntity(
    id = id,
    startTimeEpoch = startTime.epochSecond,
    endTimeEpoch = endTime.epochSecond,
    dataOriginPackage = dataOriginPackage,
    syncedAtEpoch = syncedAtEpoch,
)

fun SleepSessionEntity.toDomain(stages: List<SleepStage>): SleepSession = SleepSession(
    id = id,
    startTime = Instant.ofEpochSecond(startTimeEpoch),
    endTime = Instant.ofEpochSecond(endTimeEpoch),
    stages = stages,
    source = DataSource.HEALTH_CONNECT,
    dataOriginPackage = dataOriginPackage,
    heartRateSamples = emptyList(),
    hrvSamples = emptyList(),
    respirationSamples = emptyList(),
    spo2Samples = emptyList(),
)

fun SleepStage.toEntity(sessionId: String): SleepStageEntity = SleepStageEntity(
    sessionId = sessionId,
    startTimeEpoch = startTime.epochSecond,
    endTimeEpoch = endTime.epochSecond,
    stageType = type.name,
)

fun SleepStageEntity.toDomain(): SleepStage = SleepStage(
    startTime = Instant.ofEpochSecond(startTimeEpoch),
    endTime = Instant.ofEpochSecond(endTimeEpoch),
    type = runCatching { SleepStageType.valueOf(stageType) }.getOrDefault(SleepStageType.UNKNOWN),
)
