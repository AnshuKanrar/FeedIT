package com.example.distll.ui.screens.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.distll.data.model.ClassificationResult
import com.example.distll.data.model.FeedPost
import com.example.distll.data.model.FeedResponse
import com.example.distll.data.model.MoodResponse
import com.example.distll.data.repository.FeedRepository
import com.example.distll.network.BackendApi
import com.example.distll.network.ClassifyRequest
import com.example.distll.network.FeedRequest
import com.example.distll.ui.components.PostCard

// How close to the bottom (in item count) we trigger the next page load.
private const val LOAD_MORE_THRESHOLD = 3

/** Pure view - reads FeedViewModel's state and renders it. No network/business logic here. */
@Composable
fun FeedScreen(
    userId: String,
    blockedTerms: List<String> = emptyList(),
    viewModel: FeedViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(userId, blockedTerms) {
        viewModel.start(userId, blockedTerms)
    }

    // Instagram-style infinite scroll: ask for the next page once the user
    // has scrolled near the end of what's already loaded.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val postCount = viewModel.uiState.value.posts.size
                if (lastVisibleIndex != null && lastVisibleIndex >= postCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadNextPage()
                }
            }
    }

    when {
        uiState.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed to load feed: ${uiState.error}")
        }
        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.posts, key = { it.postId }) { post ->
                PostCard(post = post)
            }
            if (uiState.loadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

// Canned BackendApi so the preview renders without a real network call.
private class PreviewBackendApi : BackendApi {
    override suspend fun classify(request: ClassifyRequest): List<ClassificationResult> =
        request.posts.mapIndexed { index, post ->
            ClassificationResult(
                postId = post.id,
                shouldBlur = index % 3 == 1,
                tags = if (index % 3 == 1) listOf("sadness", "insult") else listOf("joy"),
                wellbeingScore = if (index % 3 == 1) -20.0 else 65.0,
                reason = null,
            )
        }

    // Real sample captions/images pulled from backend/dummy_posts.json (with the
    // real ML API down, this is what lets rendering get tested without it).
    override suspend fun getFeed(request: FeedRequest): FeedResponse {
        val samples = listOf(
            FeedPost(
                postId = "3",
                text = "missing Divya more than i thought i would",
                imageUrl = "https://picsum.photos/400/300?random=3",
                shouldBlur = false,
                tags = listOf("sadness"),
                wellbeingScore = 42.0,
                reason = null,
            ),
            FeedPost(
                postId = "5",
                text = "you're definitely not annoying, i mean it, you're actually kind of impressive tbh 😍",
                imageUrl = "https://picsum.photos/400/300?random=5",
                shouldBlur = false,
                tags = listOf("joy"),
                wellbeingScore = 78.0,
                reason = null,
            ),
            FeedPost(
                postId = "6",
                text = "feeling really low today, not sure why 😡",
                imageUrl = "https://picsum.photos/400/300?random=6",
                shouldBlur = true,
                tags = listOf("sadness", "insult"),
                wellbeingScore = -35.0,
                reason = null,
            ),
            FeedPost(
                postId = "1",
                text = "i'm just looking out for you, but maybe Aditya doesn't actually care about you like you think",
                imageUrl = null,
                shouldBlur = false,
                tags = listOf("sadness"),
                wellbeingScore = 12.0,
                reason = null,
            ),
            FeedPost(
                postId = "2",
                text = "grateful for little things like the neighbor's dog honestly",
                imageUrl = null,
                shouldBlur = false,
                tags = listOf("joy"),
                wellbeingScore = 80.5,
                reason = null,
            ),
            FeedPost(
                postId = "4",
                text = "Naina surprised me with the sweetest gesture today, i'm still smiling",
                imageUrl = null,
                shouldBlur = false,
                tags = listOf("joy"),
                wellbeingScore = 85.0,
                reason = null,
            ),
        )
        val page = samples.drop(request.offset).take(request.limit)
        return FeedResponse(posts = page, hasMore = request.offset + request.limit < samples.size)
    }

    override suspend fun getMood(userId: String) = MoodResponse(
        mood = "neutral",
        valence = 0.0,
        arousal = 0.0,
        confidence = 0.0,
        trend = "stable",
        sessionMinutes = 0.0,
        history = emptyList(),
    )

    override suspend fun getAttention(userId: String) = com.example.distll.data.model.AttentionResponse(
        scrollsLastMinute = 0,
        avgScrollsPerMinute = 0.0,
        sessionMinutes = 0.0,
        trend = "stable",
        perMinuteCounts = emptyList(),
    )
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    val previewViewModel = remember { FeedViewModel(repository = FeedRepository(api = PreviewBackendApi())) }
    FeedScreen(userId = "preview-user", viewModel = previewViewModel)
}
