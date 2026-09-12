package com.example.distll.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopHeaderBar(
                onProfileClick = {
                    navController.navigate(Routes.PROFILE) { launchSingleTop = true }
                }
            )
        },
        bottomBar = {
            val selectedTab = NavigationContents.entries.find { tab ->
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            } ?: NavigationContents.HOME

            BottomNavBar(
                selectedItem = selectedTab,
                onItemSelected = { tab -> navController.navigateToTab(tab.route) },
            )
        },
    ) { innerPadding ->
        FeedITNavHost(
            navController = navController,
            userId = TEMPORARY_USER_ID,
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
