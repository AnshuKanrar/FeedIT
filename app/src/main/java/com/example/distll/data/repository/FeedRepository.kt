package com.example.distll.data.repository

import com.example.distll.data.model.ClassificationResult
import com.example.distll.data.model.Post
import com.example.distll.network.BackendApi
import com.example.distll.network.ClassifyRequest
import com.example.distll.network.RetrofitClient

/**
 * Calls the live backend pipeline (main.py's POST /classify) and hands
 * back the real should_blur/tags/wellbeing_score/reason per post.
 */
class FeedRepository(
    private val api: BackendApi = RetrofitClient.backendApi,
) {
    suspend fun classifyPosts(
        userId: String,
        blockedTerms: List<String>,
        posts: List<Post>,
    ): Result<List<ClassificationResult>> = runCatching {
        api.classify(ClassifyRequest(userId = userId, blockedTerms = blockedTerms, posts = posts))
    }
}
