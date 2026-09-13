package com.example.distll.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.distll.data.model.FeedPost
import com.example.distll.data.repository.FeedRepository
import com.example.distll.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val posts: List<FeedPost> = emptyList(),
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Owns feed state and pages through the server's own post pool
 * (backend/dummy_posts.json via POST /feed) - the app holds no post
 * content of its own, only what the server has already sent it.
 */
class FeedViewModel(
    private val repository: FeedRepository = FeedRepository(),
    private val pageSize: Int = 10,
) : ViewModel() {

    private var userId: String = ""
    private var blockedTerms: List<String> = emptyList()
    private var offset = 0
    private var isFetching = false

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    /** Call once per feed session (e.g. from a LaunchedEffect keyed on these params). */
    fun start(userId: String, blockedTerms: List<String>) {
        if (this.userId == userId && this.blockedTerms == blockedTerms && _uiState.value.posts.isNotEmpty()) return
        this.userId = userId
        this.blockedTerms = blockedTerms
        this.offset = 0
        this.isFetching = false
        _uiState.value = FeedUiState(loading = true)
        loadNextPage()
    }

    fun loadNextPage() {
        if (isFetching || _uiState.value.endReached) return

        isFetching = true
        val isFirstPage = _uiState.value.posts.isEmpty()
        _uiState.value = _uiState.value.copy(loading = isFirstPage, loadingMore = !isFirstPage)

        viewModelScope.launch {
            repository.getFeed(
                userId, blockedTerms, offset, pageSize,
                blurThreshold = SettingsStore.blurThreshold,
                similarityThreshold = SettingsStore.similarityThreshold,
            )
                .onSuccess { response ->
                    offset += pageSize
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadingMore = false,
                        posts = _uiState.value.posts + response.posts,
                        endReached = !response.hasMore,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadingMore = false,
                        error = e.message ?: "unknown error",
                    )
                }
            isFetching = false
        }
    }
}
