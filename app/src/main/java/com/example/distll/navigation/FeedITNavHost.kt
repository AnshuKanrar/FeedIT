package com.example.distll.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.distll.ui.screens.analysis.AnalysisScreen
import com.example.distll.ui.screens.feed.FeedScreen
import com.example.distll.ui.screens.profile.ProfileScreen
import com.example.distll.ui.screens.settings.SettingsScreen

/** Home is the start destination; Analysis/Settings are the other bottom-nav tabs, Profile is header-only. */
@Composable
fun FeedITNavHost(
    navController: NavHostController,
    userId: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) { FeedScreen(userId = userId) }
        composable(Routes.ANALYSIS) { AnalysisScreen(userId = userId) }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.PROFILE) { ProfileScreen(userId = userId) }
    }
}
