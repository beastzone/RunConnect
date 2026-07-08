package com.runconnect.app.domain.model

enum class InsightType { SLEEP, RECOVERY, TRAINING, CONSISTENCY }
enum class InsightPriority { HIGH, MEDIUM, LOW }

data class HealthInsight(
    val type: InsightType,
    val priority: InsightPriority,
    val title: String,
    val body: String,
    val actionHint: String? = null,
)
