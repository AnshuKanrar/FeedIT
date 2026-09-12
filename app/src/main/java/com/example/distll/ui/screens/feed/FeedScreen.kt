package com.example.distll.ui.screens.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

// TEMPORARY: assets/dummy_raw_posts.json is the intended source of raw
// posts but isn't populated yet, so this list stands in for it. Replace
// with the asset-loaded posts once that file has real content. This is
// NOT backend/fallback_demo/mock_feed.json - that one is pre-labeled and
// browser-only, and must never feed the live pipeline (see ARCHITECTURE.md).
private val temporaryRawPosts = listOf(
    Post(id = "p1", text = "just had the best coffee of my life"),
    Post(id = "p2", text = "IPL cricket match tonight was intense and thrilling"),
    Post(id = "p3", text = "exam stress is really getting to me this week"),
)

/** Pure view - reads FeedViewModel's state and renders it. No network/business logic here. */
@Composable
fun FeedScreen(
    userId: String,
    blockedTerms: List<String> = emptyList(),
    viewModel: FeedViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId, blockedTerms) {
        viewModel.loadFeed(userId, blockedTerms, temporaryRawPosts)
    }

    when {
        uiState.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed to load feed: ${uiState.error}")
        }
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.results, key = { it.postId }) { result ->
                val post = temporaryRawPosts.find { it.id == result.postId }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = post?.text.orEmpty())
                        if (result.shouldBlur) {
                            Text("Blurred - ${result.reason ?: result.tags.joinToString()}")
                        }
                        result.wellbeingScore?.let { Text("Wellbeing: $it") }
                        if (result.tags.isNotEmpty()) {
                            Text("Tags: ${result.tags.joinToString()}")
                        }
                    }
                }
            }
        }
    }
}

// Canned BackendApi so the preview renders without a real network call.
private class PreviewBackendApi : BackendApi {
    override suspend fun classify(request: ClassifyRequest): List<ClassificationResult> = listOf(
        ClassificationResult(postId = "p1", shouldBlur = false, tags = listOf("joy"), wellbeingScore = 72.5, reason = null),
        ClassificationResult(postId = "p2", shouldBlur = true, tags = listOf("user-blocked"), wellbeingScore = null, reason = "exact_match"),
        ClassificationResult(postId = "p3", shouldBlur = true, tags = listOf("sadness", "insult"), wellbeingScore = -40.0, reason = null),
    )
}

@Preview(showBackground = true)
@Composable
private fun FeedScreenPreview() {
    val previewViewModel = remember { FeedViewModel(repository = FeedRepository(api = PreviewBackendApi())) }
    FeedScreen(userId = "preview-user", viewModel = previewViewModel)
}
