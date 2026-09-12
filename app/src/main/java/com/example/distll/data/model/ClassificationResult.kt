package com.example.distll.data.model

import com.google.gson.annotations.SerializedName

/**
 * One entry of the POST /classify response - matches
 * backend/models.py's ClassifyResult exactly:
 *   { postId, should_blur, tags, wellbeing_score, reason }
 */
data class ClassificationResult(
    val postId: String,
    @SerializedName("should_blur")
    val shouldBlur: Boolean,
    val tags: List<String>,
    @SerializedName("wellbeing_score")
    val wellbeingScore: Double?,
    val reason: String?,
)
