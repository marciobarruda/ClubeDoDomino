package com.marcioarruda.clubedodomino.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// "Mesa de Dominó" light theme — warm cream background, deep green primary.
private val LightColorScheme = lightColorScheme(
    primary          = DominoGreen,
    secondary        = DominoOrange,
    tertiary         = DominoPurple,
    background       = DominoBg,
    surface          = DominoSurface,
    onPrimary        = DominoOnDark,
    onSecondary      = Color.White,
    onTertiary       = Color.White,
    onBackground     = DominoLight,
    onSurface        = DominoLight,
    error            = DominoError,
    onError          = Color.White,
    surfaceVariant   = Color(0xFFF2EADB),
    onSurfaceVariant = DominoMuted,
)

@Composable
fun ClubeDoDominoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
