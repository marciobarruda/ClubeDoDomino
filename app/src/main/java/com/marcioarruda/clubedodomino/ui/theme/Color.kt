
package com.marcioarruda.clubedodomino.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Playful "Game Night" Palette
val DominoGreen   = Color(0xFF00C896)  // Primary - vibrant teal-green
val DominoOrange  = Color(0xFFFF6B35)  // Secondary - warm coral/orange
val DominoYellow  = Color(0xFFFFD700)  // Gold - trophies & awards
val DominoPurple  = Color(0xFF8B5CF6)  // Tertiary - rankings/special
val DominoCyan    = Color(0xFF00D4E0)  // Accent - highlights
val DominoBg      = Color(0xFF0F2744)  // Background - dark navy blue (clearly blue)
val DominoSurface = Color(0xFF1B3A5C)  // Card surfaces - medium blue
val DominoError   = Color(0xFFFF5252)  // Error states
val DominoLight   = Color(0xFFF0F9FF)  // Light text
val DominoMuted   = Color(0xFF94A3B8)  // Muted/subtle text

// Backward-compat aliases used throughout the codebase
val DominoGold     = DominoYellow
val RoyalGold      = DominoGreen   // Primary buttons now use green
val RoyalDarkBlue  = DominoBg
val RoyalOrange    = DominoOrange
val RoyalLightText = DominoLight
val RoyalSubtleText = DominoMuted

// Glass effect
val GlassyColor = Color.White.copy(alpha = 0.08f)

@JvmField
val GlassmorphismBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.06f)
    )
)

val CardGradientBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF1E3D65), Color(0xFF122C4A))
)
