package com.example.distll.ui.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.distll.data.model.MoodResponse
import com.example.distll.data.repository.MoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val loading: Boolean = true,
    val mood: MoodResponse? = null,
    val error: String? = null,
)

/** Owns Analysis screen state - calls MoodRepository, which hits main.py's GET /session/mood/{userId}. */
class AnalysisViewModel(
    private val repository: MoodRepository = MoodRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState(loading = true)
            repository.getMood(userId)
                .onSuccess { mood -> _uiState.value = AnalysisUiState(loading = false, mood = mood) }
                .onFailure { e -> _uiState.value = AnalysisUiState(loading = false, error = e.message ?: "unknown error") }
        }
    }
}
