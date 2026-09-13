package com.example.distll.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.distll.settings.SettingsStore
import com.example.feedit.ui.theme.FeedITTheme
import com.example.feedit.ui.theme.LocalAppColors

/** Blocked terms (add/remove) and the two tunable pipeline thresholds - both persisted via SettingsStore. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var termInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = LocalAppColors.current.textPrimary)
        Text(
            "Tune what the feed hides from you",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        Text(
            "Blocked terms",
            style = MaterialTheme.typography.titleMedium,
            color = LocalAppColors.current.textPrimary,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = termInput,
                onValueChange = { termInput = it },
                placeholder = { Text("e.g. cricket") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LocalAppColors.current.primary),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    SettingsStore.addBlockedTerm(context, termInput)
                    termInput = ""
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalAppColors.current.primary,
                    contentColor = LocalAppColors.current.primaryButtonTextColor,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsStore.blockedTerms.forEach { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalAppColors.current.background)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(term, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Remove",
                    color = LocalAppColors.current.wellbeingNegative,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { SettingsStore.removeBlockedTerm(context, term) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        ThresholdSection(
            title = "Blur threshold",
            description = "How harmful before a post is blurred",
            value = SettingsStore.blurThreshold,
            onValueChange = { SettingsStore.setBlurThreshold(context, it) },
        )

        Spacer(Modifier.height(24.dp))

        ThresholdSection(
            title = "Similarity threshold",
            description = "How closely a post must match a blocked term",
            value = SettingsStore.similarityThreshold,
            onValueChange = { SettingsStore.setSimilarityThreshold(context, it) },
        )
    }
}

@Composable
private fun ThresholdSection(
    title: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = LocalAppColors.current.textPrimary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(description, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textSecondary)
        Text(
            String.format("%.2f", value),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textPrimary,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = LocalAppColors.current.primary,
            activeTrackColor = LocalAppColors.current.primary,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    FeedITTheme(dynamicColor = false) {
        SettingsScreen()
    }
}
