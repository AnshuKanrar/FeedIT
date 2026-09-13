package com.example.distll.ui.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.distll.data.model.AttentionResponse
import com.example.distll.data.model.MoodResponse
import com.example.distll.data.repository.MoodRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val loading: Boolean = true,
    val mood: MoodResponse? = null,
    val attention: AttentionResponse? = null,
    val error: String? = null,
)

/**
 * Owns Analysis screen state - calls MoodRepository, which hits main.py's
 * GET /session/mood/{userId} and GET /session/attention/{userId} (both
 * auto-fed server-side from the same /feed calls, no extra tracking calls
 * needed from here).
 */
class AnalysisViewModel(
    private val repository: MoodRepository = MoodRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState(loading = true)

            val moodDeferred = async { repository.getMood(userId) }
            val attentionDeferred = async { repository.getAttention(userId) }
            val moodResult = moodDeferred.await()
            val attentionResult = attentionDeferred.await()

            val error = moodResult.exceptionOrNull()?.message ?: attentionResult.exceptionOrNull()?.message
            _uiState.value = AnalysisUiState(
                loading = false,
                mood = moodResult.getOrNull(),
                attention = attentionResult.getOrNull(),
                error = error,
            )
        }
    }
}
