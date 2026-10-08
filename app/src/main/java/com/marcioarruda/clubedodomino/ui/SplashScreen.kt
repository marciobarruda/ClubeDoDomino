package com.marcioarruda.clubedodomino.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioarruda.clubedodomino.R
import com.marcioarruda.clubedodomino.ui.theme.DominoGreen
import com.marcioarruda.clubedodomino.ui.theme.DominoGreenAlt
import com.marcioarruda.clubedodomino.ui.theme.DominoOnDark
import com.marcioarruda.clubedodomino.ui.theme.DominoOnDarkMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOrange
import com.marcioarruda.clubedodomino.ui.theme.DominoYellow
import kotlinx.coroutines.delay

@Composable
fun SplashScreen() {
    val scaleAnim  = remember { Animatable(0.8f) }
    val alphaAnim  = remember { Animatable(0f) }
    val rotationAnim = remember { Animatable(-6f) }
    val textAlpha  = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, tween(700, easing = EaseOutBack))
        alphaAnim.animateTo(1f, tween(500))
        rotationAnim.animateTo(0f, tween(700, easing = EaseOutCubic))
        delay(200)
        textAlpha.animateTo(1f, tween(600, easing = EaseOutCubic))
    }

    // Subtle continuous pulse while the loading screen is visible.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "glow"
    )
    val dotPulse by pulse.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot"
    )
    val tilePulse by pulse.animateFloat(
        initialValue = 0.98f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseOutCubic), RepeatMode.Reverse),
        label = "tilePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B2615), DominoGreen, DominoGreenAlt)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glow halo behind the artwork
        Box(
            modifier = Modifier
                .size(260.dp)
                .alpha(glowAlpha * alphaAnim.value)
                .background(
                    Brush.radialGradient(
                        listOf(DominoYellow.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Domino pieces artwork (real image, fade-in + scale-up + gentle rotation settle,
            // followed by a subtle continuous pulse while loading)
            Image(
                painter = painterResource(R.drawable.domino_pieces),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .scale(scaleAnim.value * tilePulse)
                    .rotate(rotationAnim.value)
                    .alpha(alphaAnim.value)
            )

            Spacer(Modifier.height(40.dp))

            // Club name
            Column(
                modifier = Modifier.alpha(textAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CLUBE DO",
                    color = DominoOnDarkMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
                Text(
                    text = "DOMINÓ",
                    color = DominoYellow,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "EMPREL",
                    color = DominoOnDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(Modifier.height(60.dp))

            // Animated loading dots
            Row(
                modifier = Modifier.alpha(textAlpha.value),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 1, 2).forEach { idx ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (idx == 1) dotPulse else (1f - dotPulse * 0.5f))
                            .background(
                                when (idx) {
                                    0 -> DominoYellow
                                    1 -> DominoOnDark
                                    else -> DominoOrange
                                },
                                CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Carregando...",
                color = DominoOnDarkMuted,
                fontSize = 12.sp,
                modifier = Modifier.alpha(textAlpha.value * 0.7f)
            )
        }
    }
}
