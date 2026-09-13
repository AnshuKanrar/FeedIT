package com.example.distll.auth

/**
 * Flips a local "connected" flag for Reddit/Instagram - no real OAuth flow,
 * no network call, no token exchange (see ARCHITECTURE.md "Mocked login").
 */
object MockAuthManager {
    private val tokenStore = TokenStore()

    fun isConnected(platform: String): Boolean = tokenStore.isConnected(platform)

    fun connect(platform: String) = tokenStore.setConnected(platform, true)

    fun disconnect(platform: String) = tokenStore.setConnected(platform, false)

    fun connectedPlatforms(): List<PlatformToken> = tokenStore.allTokens()
}
