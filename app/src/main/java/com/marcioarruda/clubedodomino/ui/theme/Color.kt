
package com.marcioarruda.clubedodomino.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Playful "Game felt green" table palette
val DominoGreen   = Color(0xFF00E676)  // Primary - vibrant game green
val DominoOrange  = Color(0xFFFF7043)  // Secondary - gaming orange/coral
val DominoYellow  = Color(0xFFFFD700)  // Gold - highlights & awards
val DominoPurple  = Color(0xFF9575CD)  // Tertiary - rankings/special
val DominoCyan    = Color(0xFF00E5FF)  // Accent - highlights
val DominoBg      = Color(0xFF0E3821)  // Background - Deep gaming table felt green
val DominoSurface = Color(0xFF154C2E)  // Card surfaces - Medium table felt green
val DominoError   = Color(0xFFFF5252)  // Error states
val DominoLight   = Color(0xFFF1F8E9)  // Light ivory text
val DominoMuted   = Color(0xFF81C784)  // Muted light green text

// Backward-compat aliases used throughout the codebase
val DominoGold     = DominoYellow
val RoyalGold      = DominoYellow
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
    colors = listOf(Color(0xFF1B5E20), Color(0xFF0C381E))
)

