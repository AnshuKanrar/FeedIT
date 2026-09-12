package com.example.distll.network

import com.example.distll.data.model.ClassificationResult
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
)

interface BackendApi {
    @POST("classify")
    suspend fun classify(@Body request: ClassifyRequest): List<ClassificationResult>

    @GET("session/mood/{userId}")
    suspend fun getMood(@Path("userId") userId: String): MoodResponse
}
