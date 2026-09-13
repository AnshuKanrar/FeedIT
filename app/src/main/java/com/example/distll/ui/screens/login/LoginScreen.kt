package com.example.distll.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distll.auth.MockAuthManager
import com.example.feedit.ui.theme.FeedITTheme
import com.example.feedit.ui.theme.LocalAppColors

/**
 * One-time entry screen - app wordmark, a name field (feeds Profile's
 * display name), and two purely cosmetic "Connect" toggles (Reddit,
 * Instagram - no real OAuth, see MockAuthManager). Continuing lands on
 * Home; the login route is then popped off the back stack for good.
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onContinue: (name: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {

        Text(
            text = "FeedIT",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            color = LocalAppColors.current.primary,
        )
        Text(
            text = "your feed, on your terms",
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            color = LocalAppColors.current.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Enter your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LocalAppColors.current.primary,
                focusedLabelColor = LocalAppColors.current.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        ConnectRow(platform = "reddit", label = "Reddit")
        Spacer(Modifier.height(12.dp))
        ConnectRow(platform = "instagram", label = "Instagram")

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { onContinue(name.ifBlank { "Guest" }) },
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalAppColors.current.primary,
                contentColor = LocalAppColors.current.primaryButtonTextColor,
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Aesthetic only: toggles MockAuthManager's local flag, no real OAuth, no
// network call - "button tap, connect, that's it."
@Composable
private fun ConnectRow(platform: String, label: String) {
    var connected by remember { mutableStateOf(MockAuthManager.isConnected(platform)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LocalAppColors.current.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge)

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (connected) LocalAppColors.current.wellbeingPositive else LocalAppColors.current.primary)
                .clickable {
                    connected = !connected
                    if (connected) MockAuthManager.connect(platform) else MockAuthManager.disconnect(platform)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (connected) "Connected" else "Connect",
                color = LocalAppColors.current.primaryButtonTextColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    FeedITTheme(dynamicColor = false) {
        LoginScreen(onContinue = {})
    }
}
