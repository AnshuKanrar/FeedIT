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
import com.example.distll.data.model.Post
import com.example.distll.data.repository.FeedRepository
import com.example.distll.network.BackendApi
import com.example.distll.network.ClassifyRequest
import com.example.distll.ui.components.PostCard

// How close to the bottom (in item count) we trigger the next page load.
private const val LOAD_MORE_THRESHOLD = 3

// TEMPORARY: assets/dummy_raw_posts.json is the intended source of raw
// posts but isn't populated yet, so this larger stand-in list is here
// purely so infinite scroll/paging has enough content to actually
// demonstrate. Replace with the asset-loaded posts once that file has
// real content. This is NOT backend/fallback_demo/mock_feed.json - that
// one is pre-labeled and browser-only, and must never feed the live
// pipeline (see ARCHITECTURE.md).
private val temporaryRawPosts = (1..40).map { i ->
    val samples = listOf(
        "just had the best coffee of my life",
        "IPL cricket match tonight was intense and thrilling",
        "exam stress is really getting to me this week",
        "finally finished my side project after months",
        "why is everyone in my group chat so quiet lately",
        "rainy days like this make me want to just nap all day",
        "can't believe how good that new restaurant downtown is",
        "feeling really burnt out from work this week",
        "my plant finally grew a new leaf, small wins",
        "traffic today was absolutely brutal",
        "nobody actually cares what happens to me anyway",
        "you're all pathetic and this whole group is a joke",
        "got a promotion today, still can't believe it",
        "missing my family a lot this week",
        "why does everyone online have to be so toxic",
        "watched the sunset from the terrace, felt so peaceful",
        "failed my driving test again, feeling like a failure",
        "adopted a puppy today, best decision ever",
        "everyone at this party is fake and I hate it here",
        "grateful for the small things today",
    )
    Post(id = "p$i", text = "${samples[(i - 1) % samples.size]} (#$i)")
}

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
        viewModel.start(userId, blockedTerms, temporaryRawPosts)
    }

    // Instagram-style infinite scroll: ask for the next page once the user
    // has scrolled near the end of what's already loaded.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val resultCount = viewModel.uiState.value.results.size
                if (lastVisibleIndex != null && lastVisibleIndex >= resultCount - LOAD_MORE_THRESHOLD) {
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
            items(uiState.results, key = { it.postId }) { result ->
                val post = temporaryRawPosts.find { it.id == result.postId } ?: Post(id = result.postId, text = "")
                PostCard(post = post, result = result)
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

    override suspend fun getMood(userId: String) = com.example.distll.data.model.MoodResponse(
        mood = "neutral",
        valence = 0.0,
        arousal = 0.0,
        confidence = 0.0,
        trend = "stable",
        sessionMinutes = 0.0,
        history = emptyList(),
    )
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    val previewViewModel = remember { FeedViewModel(repository = FeedRepository(api = PreviewBackendApi())) }
    FeedScreen(userId = "preview-user", viewModel = previewViewModel)
}
