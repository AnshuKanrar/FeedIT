package com.example.distll.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.distll.auth.UserSession
import com.example.distll.settings.SettingsStore
import com.example.distll.ui.components.BottomNavBar
import com.example.distll.ui.components.NavigationContents
import com.example.distll.ui.components.TopHeaderBar
import com.example.feedit.ui.theme.FeedITTheme

// TEMPORARY: no auth is wired up yet (MockAuthManager only flips a local
// connected flag), so there's no real signed-in user id. Replace once
// real login exists.
private const val TEMPORARY_USER_ID = "demo-user"

/**
 * The whole app's navigation, in one place. Home is the start destination
 * and one of three bottom-nav tabs (Home/Analysis/Settings); Profile is
 * reachable only from TopHeaderBar's button and isn't part of the tab set.
 *
 * Bottom-tab selection is derived from the current destination's hierarchy
 * (the standard Navigation-Compose pattern) rather than tracked in separate
 * state, so it can never drift out of sync with what's actually on screen.
 */
@Composable
fun FeedITApp() {
    val context = LocalContext.current
    // Restored once per process, synchronously, before the nav graph is
    // even built - so a returning user's start destination is correct on
    // the very first frame, never a flash of the login screen.
    val startDestination = remember {
        UserSession.restore(context)
        SettingsStore.restore(context)
        if (UserSession.isLoggedIn) Routes.HOME else Routes.LOGIN
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isLoginScreen = currentDestination?.route == Routes.LOGIN

    Scaffold(
        topBar = {
            if (!isLoginScreen) {
                TopHeaderBar(
                    onProfileClick = {
                        navController.navigate(Routes.PROFILE) { launchSingleTop = true }
                    }
                )
            }
        },
        bottomBar = {
            if (!isLoginScreen) {
                val selectedTab = NavigationContents.entries.find { tab ->
                    currentDestination?.hierarchy?.any { it.route == tab.route } == true
                } ?: NavigationContents.HOME

                BottomNavBar(
                    selectedItem = selectedTab,
                    onItemSelected = { tab -> navController.navigateToTab(tab.route) },
                )
            }
        },
    ) { innerPadding ->
        FeedITNavHost(
            navController = navController,
            userId = TEMPORARY_USER_ID,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** Standard bottom-nav behavior: single top, per-tab state restored, never stacks duplicate tabs. */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedITAppPreview() {
    FeedITTheme {
        FeedITApp()
    }
}
