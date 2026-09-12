package com.example.distll.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.example.distll.auth.MockAuthManager
import com.example.distll.auth.PlatformToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileUiState(
    val userId: String = "",
    val connectedPlatforms: List<PlatformToken> = emptyList(),
)

/**
 * Owns the profile screen's state. There's no real backend profile
 * endpoint yet, so this reads the only real user-related state the app
 * currently has: the mocked per-platform connection flags in
 * MockAuthManager.
 */
class ProfileViewModel(
    private val authManager: MockAuthManager = MockAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun load(userId: String) {
        _uiState.value = ProfileUiState(
            userId = userId,
            connectedPlatforms = authManager.connectedPlatforms(),
        )
    }

    fun toggleConnection(platform: String) {
        if (authManager.isConnected(platform)) {
            authManager.disconnect(platform)
        } else {
            authManager.connect(platform)
        }
        _uiState.value = _uiState.value.copy(connectedPlatforms = authManager.connectedPlatforms())
    }
}
