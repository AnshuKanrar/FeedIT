package com.example.distll.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "feedit_session"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_LOGGED_IN = "logged_in"

/**
 * The entered display name + whether login has happened - persisted to
 * SharedPreferences (not real auth, no backend account, just enough so the
 * one-time login screen actually stays "seen" across app restarts). Compose
 * state so ProfileScreen recomposes the moment this changes.
 */
object UserSession {
    var displayName by mutableStateOf("Guest")
        private set
    var isLoggedIn by mutableStateOf(false)
        private set

    /** Call once at app startup, before deciding the nav start destination. */
    fun restore(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)
        displayName = prefs.getString(KEY_DISPLAY_NAME, "Guest") ?: "Guest"
    }

    fun login(context: Context, name: String) {
        displayName = name
        isLoggedIn = true
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_NAME, name)
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
    }
}
