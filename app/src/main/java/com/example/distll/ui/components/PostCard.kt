package com.example.distll.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.distll.data.model.FeedPost
import com.example.feedit.ui.theme.FeedITTheme
import com.example.feedit.ui.theme.LocalAppColors

/** One post in the feed: its text (actually blurred + tap-to-reveal if flagged), tag chips, wellbeing/blocked status, and a like/comment/share row. */
@Composable
fun PostCard(
    post: FeedPost,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable(post.postId) { mutableStateOf(false) }
    val isHidden = post.shouldBlur && !revealed

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // NOTE: Modifier.blur() only renders on API 31+. Below that
                // this content stays fully readable - there's no opaque
                // fallback here on purpose, per design. minSdk is 27.
                val contentModifier = if (isHidden) Modifier.blur(14.dp) else Modifier

                Column(modifier = contentModifier) {
                    post.imageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    val textTopPadding = if (post.imageUrl != null) Modifier.padding(top = 8.dp) else Modifier
                    if (isHidden) {
                        // Modifier.blur() reliably blurs images but not text
                        // glyphs on every device/GPU - don't gamble on that
                        // for actually hiding content. Redact it instead.
                        RedactedTextPlaceholder(modifier = textTopPadding)
                    } else {
                        Text(
                            text = post.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = LocalAppColors.current.textPrimary,
                            modifier = textTopPadding,
                        )
                    }
                }

                if (isHidden) {
                    Text(
                        text = "Tap to reveal",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(50))
                            .background(LocalAppColors.current.bottomNavBar)
                            .clickable { revealed = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            if (post.tags.isNotEmpty() || post.wellbeingScore != null || post.shouldBlur) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        post.tags.forEach { tag -> TagChip(tag) }
                    }

                    if (post.wellbeingScore != null) {
                        WellbeingLabel(post.wellbeingScore)
                    } else if (post.shouldBlur) {
                        Text(
                            text = "blocked by your settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalAppColors.current.textSecondary,
                        )
                    }
                }
            }

            PostActionsRow(modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun AsyncImage(
    model: String,
    contentDescription: Nothing?,
    contentScale: ContentScale,
    modifier: Modifier
) {
    TODO("Not yet implemented")
}

// Guaranteed-to-hide stand-in for real text: a couple of muted bars, the
// standard "redacted content" look, so nothing about the real caption can
// leak through regardless of whether blur renders correctly on this device.
@Composable
private fun RedactedTextPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LocalAppColors.current.textSecondary.copy(alpha = 0.3f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LocalAppColors.current.textSecondary.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    val tint = when (tag) {
        "user-blocked" -> LocalAppColors.current.textSecondary
        "joy" -> LocalAppColors.current.wellbeingPositive
        else -> LocalAppColors.current.wellbeingNegative
    }
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun WellbeingLabel(score: Double) {
    Row {
        Text(
            text = "wellbeing ",
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textSecondary,
        )
        Text(
            text = formatWellbeing(score),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary,
        )
    }
}

private fun formatWellbeing(score: Double): String {
    val rounded = kotlin.math.round(score * 10) / 10.0
    val body = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    return if (rounded >= 0) "+$body" else body
}

// Aesthetic only, for now: Like toggles a local visual state; Comment and
// Share are no-ops (no comment feature, no real share sheet) - just the
// icons, as requested.
@Composable
private fun PostActionsRow(modifier: Modifier = Modifier) {
    var liked by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconButton(onClick = { liked = !liked }) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = if (liked) LocalAppColors.current.wellbeingNegative else LocalAppColors.current.textSecondary,
            )
        }
        IconButton(onClick = { /* comments not implemented yet */ }) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Comment",
                tint = LocalAppColors.current.textSecondary,
            )
        }
        IconButton(onClick = { /* sharing not implemented - aesthetic only, for now */ }) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = LocalAppColors.current.textSecondary,
            )
        }
    }
}

@Preview(showBackground = true, name = "Positive")
@Composable
private fun PostCardPreview() {
    FeedITTheme(dynamicColor = false) {
        PostCard(
            post = FeedPost(
                postId = "p1",
                text = "have a nice day",
                imageUrl = "https://picsum.photos/400/300?random=1",
                shouldBlur = false,
                tags = emptyList(),
                wellbeingScore = 71.0,
                reason = null,
            ),
        )
    }
}

@Preview(showBackground = true, name = "Blurred - tap to reveal")
@Composable
private fun PostCardBlurredPreview() {
    FeedITTheme(dynamicColor = false) {
        PostCard(
            post = FeedPost(
                postId = "p2",
                text = "exam stress is really getting to me this week",
                imageUrl = "https://picsum.photos/400/300?random=2",
                shouldBlur = true,
                tags = listOf("toxic", "insult"),
                wellbeingScore = -62.5,
                reason = "similarity_match:exam stress",
            ),
        )
    }
}

@Preview(showBackground = true, name = "User-blocked")
@Composable
private fun PostCardBlockedPreview() {
    FeedITTheme(dynamicColor = false) {
        PostCard(
            post = FeedPost(
                postId = "p3",
                text = "IPL cricket match tonight was intense",
                imageUrl = null,
                shouldBlur = true,
                tags = listOf("user-blocked"),
                wellbeingScore = null,
                reason = "exact_match",
            ),
        )
    }
}
