package com.example.feedit.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for FeedIT - what each color is USED for, not just
 * its hex value. FeedIT-specific components (BottomNavBar, PostCard,
 * wellbeing indicators) should read colors from here via
 * LocalAppColors.current.xxx instead of hardcoding hex values, so the whole
 * app can be re-themed by editing only LightAppColors/DarkAppColors below.
 *
 * Standard Material3 widgets (Card, TextField, Scaffold, ...) keep using
 * MaterialTheme.colorScheme as normal - this sits alongside it, it doesn't
 * replace it.
 */
data class AppColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val primaryButtonTextColor: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val bottomNavBar: Color,
    val bottomNavButtonColor: Color,
    val wellbeingPositive: Color,
    val wellbeingNegative: Color,
    val wellbeingWarning: Color,
    val blurOverlay: Color,
)

val LightAppColors = AppColors(
    background = Cream50,
    surface = Color.White,
    primary = Teal700,
    primaryButtonTextColor = Color.White,
    secondary = Coral500,
    textPrimary = Charcoal900,
    textSecondary = Charcoal700,
    border = Sand300,
    bottomNavBar = DockLight,
    bottomNavButtonColor = Teal700,
    wellbeingPositive = Green500,
    wellbeingNegative = Red500,
    wellbeingWarning = Amber500,
    blurOverlay = Charcoal900.copy(alpha = 0.6f),
)

val DarkAppColors = AppColors(
    background = Ink900,
    surface = Ink800,
    primary = Teal300,
    primaryButtonTextColor = Ink900,
    secondary = Coral300,
    textPrimary = Bone100,
    textSecondary = Fog300,
    border = Ink700,
    bottomNavBar = DockDark,
    bottomNavButtonColor = Teal300,
    wellbeingPositive = Green300,
    wellbeingNegative = Red300,
    wellbeingWarning = Amber300,
    blurOverlay = DockDark.copy(alpha = 0.75f),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
