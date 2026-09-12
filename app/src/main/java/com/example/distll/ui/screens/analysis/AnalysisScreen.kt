package com.example.distll.ui.screens.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.distll.data.model.MoodHistoryPoint
import com.example.distll.data.model.MoodResponse
import com.example.feedit.ui.theme.FeedITTheme
import com.example.feedit.ui.theme.LocalAppColors

/** Pure view - reads AnalysisViewModel's state (mood_predictor.py's output) and renders it. */
@Composable
fun AnalysisScreen(
    userId: String,
    modifier: Modifier = Modifier,
    viewModel: AnalysisViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when {
            uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to load mood: ${uiState.error}")
            }
            uiState.mood != null -> MoodCard(uiState.mood!!)
        }
    }
}

@Composable
private fun MoodCard(mood: MoodResponse, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Mood", style = MaterialTheme.typography.headlineMedium, color = LocalAppColors.current.textPrimary)
            Text(
                "How this session has felt so far",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.background)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(moodEmoji(mood.mood), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        mood.mood.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineMedium,
                        color = LocalAppColors.current.textPrimary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "${mood.sessionMinutes} minutes in this session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppColors.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatBox("Confidence", "${(mood.confidence * 100).toInt()}%", Modifier.weight(1f))
                StatBox("Valence", formatSigned(mood.valence), Modifier.weight(1f))
                StatBox("Arousal", formatSigned(mood.arousal), Modifier.weight(1f))
            }

            TrendRow(mood.trend, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.background)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "Valence over the session",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalAppColors.current.textSecondary,
                    )
                    MoodLineChart(
                        history = mood.history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(140.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LocalAppColors.current.background)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = LocalAppColors.current.textPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TrendRow(trend: String, modifier: Modifier = Modifier) {
    val (icon, color, label) = when (trend) {
        "improving" -> Triple(Icons.Filled.ArrowUpward, LocalAppColors.current.wellbeingPositive, "Improving over the session")
        "declining" -> Triple(Icons.Filled.ArrowDownward, LocalAppColors.current.wellbeingNegative, "Declining over the session")
        else -> Triple(Icons.Filled.ArrowForward, LocalAppColors.current.textSecondary, "Stable over the session")
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun MoodLineChart(history: List<MoodHistoryPoint>, modifier: Modifier = Modifier) {
    val lineColor = LocalAppColors.current.primary
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas

        val minV = history.minOf { it.valence }
        val maxV = history.maxOf { it.valence }
        val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
        val stepX = size.width / (history.size - 1)

        fun pointFor(index: Int): Offset {
            val normalized = ((history[index].valence - minV) / range).toFloat()
            return Offset(index * stepX, size.height - normalized * size.height)
        }

        for (i in 0 until history.size - 1) {
            drawLine(color = lineColor, start = pointFor(i), end = pointFor(i + 1), strokeWidth = 4f)
        }
        history.indices.forEach { i -> drawCircle(color = lineColor, radius = 6f, center = pointFor(i)) }
    }
}

private fun moodEmoji(mood: String): String = when (mood) {
    "happy" -> "😊"
    "calm" -> "😌"
    "sad" -> "😔"
    else -> "😐"
}

private fun formatSigned(value: Double): String =
    if (value >= 0) "+%.2f".format(value) else "%.2f".format(value)

@Preview(showBackground = true)
@Composable
private fun AnalysisScreenPreview() {
    FeedITTheme(dynamicColor = false) {
        MoodCard(
            mood = MoodResponse(
                mood = "sad",
                valence = -0.40,
                arousal = 0.20,
                confidence = 0.65,
                trend = "declining",
                sessionMinutes = 32.5,
                history = listOf(
                    MoodHistoryPoint(t = 0.0, valence = -0.1, arousal = 0.1),
                    MoodHistoryPoint(t = 600.0, valence = -0.25, arousal = 0.15),
                    MoodHistoryPoint(t = 1200.0, valence = -0.40, arousal = 0.2),
                ),
            ),
        )
    }
}
