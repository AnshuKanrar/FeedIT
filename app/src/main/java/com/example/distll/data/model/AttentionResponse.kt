package com.example.distll.data.model

import com.google.gson.annotations.SerializedName

/** One minute's scroll count from GET /session/attention/{userId}. */
data class AttentionMinutePoint(
    val minute: Int,
    val count: Int,
)

/** GET /session/attention/{userId} response - matches backend/models.py's AttentionResponse exactly. */
data class AttentionResponse(
    @SerializedName("scrolls_last_minute")
    val scrollsLastMinute: Int,
    @SerializedName("avg_scrolls_per_minute")
    val avgScrollsPerMinute: Double,
    @SerializedName("session_minutes")
    val sessionMinutes: Double,
    val trend: String,
    @SerializedName("per_minute_counts")
    val perMinuteCounts: List<AttentionMinutePoint>,
)
