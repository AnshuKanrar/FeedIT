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
    val loadingMore: Boolean = false,
    val results: List<ClassificationResult> = emptyList(),
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Owns feed state and drives FeedRepository page by page (Instagram-style
 * infinite scroll) - FeedScreen only ever reads [uiState] and calls
 * [loadNextPage] when the user scrolls near the bottom.
 *
 * No backend change needed for this: /classify already classifies
 * whatever posts it's given, and SessionTracker keeps accumulating the
 * same user's session state correctly across separate sequential calls.
 */
class FeedViewModel(
    private val repository: FeedRepository = FeedRepository(),
    private val pageSize: Int = 10,
) : ViewModel() {

    private var userId: String = ""
    private var blockedTerms: List<String> = emptyList()
    private var allPosts: List<Post> = emptyList()
    private var nextIndex = 0
    private var isFetching = false

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    /** Call once per feed session (e.g. from a LaunchedEffect keyed on these params). */
    fun start(userId: String, blockedTerms: List<String>, allPosts: List<Post>) {
        if (this.userId == userId && this.blockedTerms == blockedTerms && this.allPosts == allPosts) return
        this.userId = userId
        this.blockedTerms = blockedTerms
        this.allPosts = allPosts
        this.nextIndex = 0
        this.isFetching = false
        _uiState.value = FeedUiState(loading = true)
        loadNextPage()
    }

    fun loadNextPage() {
        if (isFetching || _uiState.value.endReached) return

        val page = allPosts.drop(nextIndex).take(pageSize)
        if (page.isEmpty()) {
            _uiState.value = _uiState.value.copy(loading = false, loadingMore = false, endReached = true)
            return
        }

        isFetching = true
        val isFirstPage = _uiState.value.results.isEmpty()
        _uiState.value = _uiState.value.copy(loading = isFirstPage, loadingMore = !isFirstPage)

        viewModelScope.launch {
            repository.classifyPosts(userId, blockedTerms, page)
                .onSuccess { newResults ->
                    nextIndex += page.size
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadingMore = false,
                        results = _uiState.value.results + newResults,
                        endReached = nextIndex >= allPosts.size,
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
