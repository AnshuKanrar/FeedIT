package com.example.distll.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException

private const val PREFS_NAME = "feedit_settings"
private const val KEY_BLOCKED_TERMS = "blocked_terms"
private const val KEY_BLUR_THRESHOLD = "blur_threshold"
private const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"

/**
 * Persisted user settings that actually reach the backend: blocked_terms,
 * blur_threshold (feed_logic.SessionTracker), similarity_threshold
 * (content_blocking.is_blocked) - both already-tunable parameters on the
 * backend, just never exposed to the app until now.
 */
object SettingsStore {
    var blockedTerms by mutableStateOf<List<String>>(emptyList())
        private set
    var blurThreshold by mutableStateOf(0.5f)
        private set
    var similarityThreshold by mutableStateOf(0.3f)
        private set

    /** Call once at app startup, before the feed's first request. */
    fun restore(context: Context) {
        val prefs = prefs(context)
        blockedTerms = decodeTerms(prefs.getString(KEY_BLOCKED_TERMS, null))
        blurThreshold = prefs.getFloat(KEY_BLUR_THRESHOLD, 0.5f)
        similarityThreshold = prefs.getFloat(KEY_SIMILARITY_THRESHOLD, 0.3f)
    }

    fun addBlockedTerm(context: Context, term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty() || blockedTerms.any { it.equals(trimmed, ignoreCase = true) }) return
        blockedTerms = blockedTerms + trimmed
        prefs(context).edit().putString(KEY_BLOCKED_TERMS, encodeTerms(blockedTerms)).apply()
    }

    fun removeBlockedTerm(context: Context, term: String) {
        blockedTerms = blockedTerms - term
        prefs(context).edit().putString(KEY_BLOCKED_TERMS, encodeTerms(blockedTerms)).apply()
    }

    fun setBlurThreshold(context: Context, value: Float) {
        blurThreshold = value
        prefs(context).edit().putFloat(KEY_BLUR_THRESHOLD, value).apply()
    }

    fun setSimilarityThreshold(context: Context, value: Float) {
        similarityThreshold = value
        prefs(context).edit().putFloat(KEY_SIMILARITY_THRESHOLD, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encodeTerms(terms: List<String>): String {
        val array = JSONArray()
        terms.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeTerms(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
