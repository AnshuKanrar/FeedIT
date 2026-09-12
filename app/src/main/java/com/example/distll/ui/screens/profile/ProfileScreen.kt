package com.example.distll.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.distll.auth.PlatformToken
import com.example.feedit.ui.theme.FeedITTheme

/** Pure view - reads ProfileViewModel's state and renders it. */
@Composable
fun ProfileScreen(
    userId: String,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        Text("User ID: ${uiState.userId}", style = MaterialTheme.typography.bodyLarge)

        Text("Connected accounts", style = MaterialTheme.typography.titleMedium)
        uiState.connectedPlatforms.forEach { platform ->
            PlatformRow(
                platform = platform,
                onToggle = { viewModel.toggleConnection(platform.platform) },
            )
        }
    }
}

@Composable
private fun PlatformRow(platform: PlatformToken, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(platform.platform.replaceFirstChar { it.uppercase() })
            Button(onClick = onToggle) {
                Text(if (platform.connected) "Disconnect" else "Connect")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    FeedITTheme(dynamicColor = false) {
        ProfileScreen(userId = "demo-user")
    }
}
