package com.example.distll.data.model

/**
 * Raw post as sent to the backend for classification - id + text only.
 * No should_blur/tags/wellbeing_score here; those come back from /classify.
 */
data class Post(
    val id: String,
    val text: String,
)
