package com.example.distll.data.repository

import com.example.distll.data.model.AttentionResponse
import com.example.distll.data.model.MoodResponse
import com.example.distll.network.BackendApi
import com.example.distll.network.RetrofitClient

/** Calls the live backend pipeline's session-analytics endpoints for the Analysis screen. */
class MoodRepository(
    private val api: BackendApi = RetrofitClient.backendApi,
) {
    suspend fun getMood(userId: String): Result<MoodResponse> = runCatching {
        api.getMood(userId)
    }

    suspend fun getAttention(userId: String): Result<AttentionResponse> = runCatching {
        api.getAttention(userId)
    }
}
