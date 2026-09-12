package com.example.distll.auth

/** Fake/local token shape - no real auth server involved. */
data class PlatformToken(
    val platform: String,
    val connected: Boolean = false,
)

/** Holds the mocked per-platform connection state in memory. */
class TokenStore {
    private val tokens = linkedMapOf(
        "reddit" to PlatformToken("reddit"),
        "youtube" to PlatformToken("youtube"),
    )

    fun isConnected(platform: String): Boolean = tokens[platform]?.connected == true

    fun setConnected(platform: String, connected: Boolean) {
        tokens[platform] = (tokens[platform] ?: PlatformToken(platform)).copy(connected = connected)
    }

    fun allTokens(): List<PlatformToken> = tokens.values.toList()
}
