package com.example.distll.data.model

import com.google.gson.annotations.SerializedName

/**
 * One post as returned by POST /feed - content AND classification together,
 * since the server now owns the raw post pool (dummy_posts.json) instead
 * of the app carrying any hardcoded content.
 */
data class FeedPost(
    val postId: String,
    val text: String,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("should_blur")
    val shouldBlur: Boolean,
    val tags: List<String>,
    @SerializedName("wellbeing_score")
    val wellbeingScore: Double?,
    val reason: String?,
)

/** POST /feed response - matches backend/models.py's FeedResponse exactly. */
data class FeedResponse(
    val posts: List<FeedPost>,
    @SerializedName("has_more")
    val hasMore: Boolean,
)
