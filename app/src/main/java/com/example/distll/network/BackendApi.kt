package com.example.distll.network

import com.example.distll.data.model.ClassificationResult
import com.example.distll.data.model.FeedResponse
import com.example.distll.data.model.MoodResponse
import com.example.distll.data.model.Post
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Request body for POST /classify - matches backend/models.py's
 * ClassifyRequest exactly: { user_id, blocked_terms, posts: [{id, text}] }
 */
data class ClassifyRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("blocked_terms")
    val blockedTerms: List<String>,
    val posts: List<Post>,
    @SerializedName("blur_threshold")
    val blurThreshold: Float = 0.5f,
    @SerializedName("similarity_threshold")
    val similarityThreshold: Float = 0.3f,
)

/**
 * Request body for POST /feed - matches backend/models.py's FeedRequest
 * exactly: { user_id, blocked_terms, offset, limit }. No post content is
 * sent - the server owns the raw post pool (dummy_posts.json).
 */
data class FeedRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("blocked_terms")
    val blockedTerms: List<String>,
    val offset: Int,
    val limit: Int,
    @SerializedName("blur_threshold")
    val blurThreshold: Float = 0.5f,
    @SerializedName("similarity_threshold")
    val similarityThreshold: Float = 0.3f,
)

interface BackendApi {
    @POST("classify")
    suspend fun classify(@Body request: ClassifyRequest): List<ClassificationResult>

    @POST("feed")
    suspend fun getFeed(@Body request: FeedRequest): FeedResponse

    @GET("session/mood/{userId}")
    suspend fun getMood(@Path("userId") userId: String): MoodResponse
}
