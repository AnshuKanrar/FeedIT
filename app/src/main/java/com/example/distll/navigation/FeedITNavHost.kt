package com.example.distll.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.distll.auth.UserSession
import com.example.distll.settings.SettingsStore
import com.example.distll.ui.screens.analysis.AnalysisScreen
import com.example.distll.ui.screens.feed.FeedScreen
import com.example.distll.ui.screens.login.LoginScreen
import com.example.distll.ui.screens.profile.ProfileScreen
import com.example.distll.ui.screens.settings.SettingsScreen

/**
 * Login is a one-time screen - continuing persists UserSession (see
 * UserSession.login) and pops Login off the back stack for good, so it
 * won't reappear on this or future app launches. Home/Analysis/Settings
 * are the bottom-nav tabs, Profile is header-only.
 */
@Composable
fun FeedITNavHost(
    navController: NavHostController,
    userId: String,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            val context = LocalContext.current
            LoginScreen(
                onContinue = { name ->
                    UserSession.login(context, name)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) { FeedScreen(userId = userId, blockedTerms = SettingsStore.blockedTerms) }
        composable(Routes.ANALYSIS) { AnalysisScreen(userId = userId) }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.PROFILE) { ProfileScreen(userId = userId) }
    }
}
