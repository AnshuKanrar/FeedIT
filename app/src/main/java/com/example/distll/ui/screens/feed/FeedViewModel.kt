package com.example.distll.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.distll.data.model.ClassificationResult
import com.example.distll.data.model.Post
import com.example.distll.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val loading: Boolean = true,
    val results: List<ClassificationResult> = emptyList(),
    val error: String? = null,
)

/** Owns feed state and drives FeedRepository - FeedScreen only ever reads [uiState]. */
class FeedViewModel(
    private val repository: FeedRepository = FeedRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    fun loadFeed(userId: String, blockedTerms: List<String>, posts: List<Post>) {
        viewModelScope.launch {
            _uiState.value = FeedUiState(loading = true)
            repository.classifyPosts(userId, blockedTerms, posts)
                .onSuccess { results -> _uiState.value = FeedUiState(loading = false, results = results) }
                .onFailure { e -> _uiState.value = FeedUiState(loading = false, error = e.message ?: "unknown error") }
        }
    }
}
