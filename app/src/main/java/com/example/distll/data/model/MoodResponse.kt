package com.example.distll.data.model

import com.google.gson.annotations.SerializedName

/** One point of GET /session/mood/{userId}'s history - matches backend's MoodHistoryPoint exactly. */
data class MoodHistoryPoint(
    val t: Double,
    val valence: Double,
    val arousal: Double,
)

/** GET /session/mood/{userId} response - matches backend/models.py's MoodResponse exactly. */
data class MoodResponse(
    val mood: String,
    val valence: Double,
    val arousal: Double,
    val confidence: Double,
    val trend: String,
    @SerializedName("session_minutes")
    val sessionMinutes: Double,
    val history: List<MoodHistoryPoint>,
)
