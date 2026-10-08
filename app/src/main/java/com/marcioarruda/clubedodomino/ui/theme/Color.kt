
package com.marcioarruda.clubedodomino.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Mesa de Dominó" palette — warm cream table felt, deep green cards, gold accents.
// Replaces the previous "game felt green" dark theme (v90 visual refresh).
val DominoGreen    = Color(0xFF143621)  // Primary — deep felt green (cards, header, nav)
val DominoGreenAlt = Color(0xFF1F5536)  // Lighter green accent (gradients, highlights)
val DominoOrange   = Color(0xFFD1573F)  // Secondary — warm coral/terracotta (debits, losses)
val DominoYellow   = Color(0xFFF2C230)  // Gold — highlights, Craque do dia, CTAs
val DominoPurple   = Color(0xFF7A6D96)  // Tertiary — reserved for special/neutral accents
val DominoCyan     = Color(0xFF1F8A5A)  // Accent — positive/wins highlight
val DominoBg       = Color(0xFFFBF4E4)  // Background — warm cream table felt
val DominoSurface  = Color(0xFFFFFFFF)  // Card surfaces — white
val DominoError    = Color(0xFFD1573F)  // Error states (same as secondary coral)
val DominoLight    = Color(0xFF1F3327)  // Primary text over light backgrounds (dark green-black)
val DominoMuted    = Color(0xFF8A6F3D)  // Muted/secondary text (warm brown-gold)

// Piorzinho / negative-highlight surface (used on dark cards, e.g. worst-of-day)
val DominoPiorBg     = Color(0xFF3A2A1E)
val DominoPiorAccent = Color(0xFFF2A65A)

// Text-on-dark tokens (used inside DominoGreen surfaces: header, nav, highlight cards)
val DominoOnDark      = Color(0xFFFBF4E4)
val DominoOnDarkMuted = Color(0xFFA9BFA9)

// Backward-compat aliases used throughout the codebase
val DominoGold      = DominoYellow
val RoyalGold       = DominoYellow
val RoyalDarkBlue   = DominoBg
val RoyalOrange     = DominoOrange
val RoyalLightText  = DominoLight
val RoyalSubtleText = DominoMuted

// Glass effect — kept for any remaining glassmorphism surfaces over dark cards
val GlassyColor = Color.White.copy(alpha = 0.08f)

@JvmField
val GlassmorphismBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.06f)
    )
)

val CardGradientBrush = Brush.linearGradient(
    colors = listOf(DominoGreenAlt, DominoGreen)
)
