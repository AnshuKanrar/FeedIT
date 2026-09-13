package com.example.distll.data.repository

import com.example.distll.data.model.ClassificationResult
import com.example.distll.data.model.FeedResponse
import com.example.distll.data.model.Post
import com.example.distll.network.BackendApi
import com.example.distll.network.ClassifyRequest
import com.example.distll.network.FeedRequest
import com.example.distll.network.RetrofitClient

/**
 * Calls the live backend pipeline (main.py). classifyPosts() sends specific
 * post text (kept for classifying arbitrary/client-supplied text); getFeed()
 * asks the server for its own paginated post pool (dummy_posts.json), already
 * classified - the app carries no post content of its own.
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

    suspend fun getFeed(
        userId: String,
        blockedTerms: List<String>,
        offset: Int,
        limit: Int,
        blurThreshold: Float = 0.5f,
        similarityThreshold: Float = 0.3f,
    ): Result<FeedResponse> = runCatching {
        api.getFeed(
            FeedRequest(
                userId = userId,
                blockedTerms = blockedTerms,
                offset = offset,
                limit = limit,
                blurThreshold = blurThreshold,
                similarityThreshold = similarityThreshold,
            )
        )
    }
}
