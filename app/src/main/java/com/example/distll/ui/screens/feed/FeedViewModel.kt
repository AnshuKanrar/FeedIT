package com.example.distll.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.distll.data.model.FeedPost
import com.example.distll.data.repository.FeedRepository
import com.example.distll.data.repository.MoodRepository
import com.example.distll.settings.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// How often we check the session mood for the "your feed is going sad" prompt.
// A real 15 minutes, as asked - shorten this for a live demo if needed.
private const val SAD_MODE_CHECK_INTERVAL_MS = 15 * 60 * 1000L

// Tags that count as "negative/red" for Happy Mode filtering - matches the
// same tags PostCard tints red (see TagChip in PostCard.kt).
private val NEGATIVE_TAGS = setOf("toxic", "obscene", "threat", "insult", "sadness")

data class FeedUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val posts: List<FeedPost> = emptyList(),
    val endReached: Boolean = false,
    val error: String? = null,
    val happyModeEnabled: Boolean = false,
    val showSadModePrompt: Boolean = false,
)

/**
 * Owns feed state and pages through the server's own post pool
 * (backend/dummy_posts.json via POST /feed) - the app holds no post
 * content of its own, only what the server has already sent it.
 *
 * Also runs a repeating timer that checks the session's mood
 * (GET /session/mood/{userId}) - if it comes back "sad", it offers Happy
 * Mode, which filters every negative/toxic/blurred post out of the feed
 * entirely (not just hides it) from then on.
 */
class FeedViewModel(
    private val repository: FeedRepository = FeedRepository(),
    private val moodRepository: MoodRepository = MoodRepository(),
    private val pageSize: Int = 10,
) : ViewModel() {

    private var userId: String = ""
    private var blockedTerms: List<String> = emptyList()
    private var offset = 0
    private var isFetching = false
    private var happyModeEnabled = false
    private var moodCheckJob: Job? = null

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    /** Call once per feed session (e.g. from a LaunchedEffect keyed on these params). */
    fun start(userId: String, blockedTerms: List<String>) {
        if (this.userId == userId && this.blockedTerms == blockedTerms && _uiState.value.posts.isNotEmpty()) return
        this.userId = userId
        this.blockedTerms = blockedTerms
        this.offset = 0
        this.isFetching = false
        this.happyModeEnabled = false
        _uiState.value = FeedUiState(loading = true)
        loadNextPage()
        startMoodWatch()
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
                    val newPosts = if (happyModeEnabled) response.posts.filterNot(::isNegative) else response.posts
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadingMore = false,
                        posts = _uiState.value.posts + newPosts,
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

    /** User tapped "Turn on Happy Mode" - strip every negative post already loaded, and every future page too. */
    fun enableHappyMode() {
        happyModeEnabled = true
        _uiState.value = _uiState.value.copy(
            happyModeEnabled = true,
            showSadModePrompt = false,
            posts = _uiState.value.posts.filterNot(::isNegative),
        )
    }

    /** User tapped "Not now" - just close the prompt, keep checking again next interval. */
    fun dismissSadModePrompt() {
        _uiState.value = _uiState.value.copy(showSadModePrompt = false)
    }

    private fun startMoodWatch() {
        moodCheckJob?.cancel()
        moodCheckJob = viewModelScope.launch {
            while (true) {
                delay(SAD_MODE_CHECK_INTERVAL_MS)
                if (happyModeEnabled) continue
                moodRepository.getMood(userId).onSuccess { mood ->
                    if (mood.mood == "sad") {
                        _uiState.value = _uiState.value.copy(showSadModePrompt = true)
                    }
                }
            }
        }
    }

    private fun isNegative(post: FeedPost): Boolean =
        post.shouldBlur ||
            (post.wellbeingScore != null && post.wellbeingScore < 0) ||
            post.tags.any { it in NEGATIVE_TAGS }
}
