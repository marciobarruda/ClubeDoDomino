package com.marcioarruda.clubedodomino.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = RoyalGold,
    secondary = RoyalOrange,
    background = RoyalDarkBlue,
    surface = RoyalDarkBlue,
    onPrimary = RoyalDarkBlue,
    onSecondary = RoyalLightText,
    onBackground = RoyalLightText,
    onSurface = RoyalLightText,
)

// Force dark background even in "Light" scheme to match the app's specific design
private val LightColorScheme = lightColorScheme(
    primary = RoyalGold,
    secondary = RoyalOrange,
    background = RoyalDarkBlue, 
    surface = RoyalDarkBlue,
    onPrimary = RoyalDarkBlue,
    onSecondary = RoyalLightText,
    onBackground = RoyalLightText,
    onSurface = RoyalLightText,
)

@Composable
fun ClubeDoDominoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Optionally force DarkColorScheme always if the app is designed purely for dark mode
    val colorScheme = if (true) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Use background color for status bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false // White text on status bar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
