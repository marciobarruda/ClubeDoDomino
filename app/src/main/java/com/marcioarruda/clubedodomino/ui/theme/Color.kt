
package com.marcioarruda.clubedodomino.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Royal Club Palette
val DominoGold = Color(0xFFFFD700)
val RoyalGold = DominoGold // Alias for compatibility
val RoyalDarkBlue = Color(0xFF0D1B2A)
val RoyalOrange = Color(0xFFE76F51)
val RoyalLightText = Color(0xFFFFFFFF)
val RoyalSubtleText = Color(0xFFB0B0B0)

// Glassmorphism Effect
val GlassyColor = Color.White.copy(alpha = 0.1f)

@JvmField
val GlassmorphismBrush = Brush.verticalGradient(
    colors = listOf(
        GlassyColor.copy(alpha = 0.3f),
        GlassyColor.copy(alpha = 0.2f)
    )
)
