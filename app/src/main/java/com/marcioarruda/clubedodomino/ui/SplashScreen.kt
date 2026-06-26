package com.marcioarruda.clubedodomino.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioarruda.clubedodomino.ui.theme.DominoBg
import com.marcioarruda.clubedodomino.ui.theme.DominoCyan
import com.marcioarruda.clubedodomino.ui.theme.DominoGreen
import com.marcioarruda.clubedodomino.ui.theme.DominoMuted
import com.marcioarruda.clubedodomino.ui.theme.DominoOrange
import com.marcioarruda.clubedodomino.ui.theme.DominoYellow
import kotlinx.coroutines.delay

@Composable
fun SplashScreen() {
    val scaleAnim  = remember { Animatable(0.5f) }
    val alphaAnim  = remember { Animatable(0f) }
    val textAlpha  = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, tween(700, easing = EaseOutBack))
        alphaAnim.animateTo(1f, tween(500))
        delay(200)
        textAlpha.animateTo(1f, tween(600, easing = EaseOutCubic))
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071020), DominoBg, Color(0xFF0D1E1A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glow halo behind tile
        Box(
            modifier = Modifier
                .size(260.dp)
                .alpha(glowAlpha * alphaAnim.value)
                .background(
                    Brush.radialGradient(
                        listOf(DominoGreen.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Domino tile artwork
            Canvas(
                modifier = Modifier
                    .size(220.dp, 110.dp)
                    .scale(scaleAnim.value)
                    .alpha(alphaAnim.value)
            ) {
                drawDominoTile(this, leftDots = 6, rightDots = 2)
            }

            Spacer(Modifier.height(40.dp))

            // Club name
            Column(
                modifier = Modifier.alpha(textAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CLUBE DO",
                    color = DominoMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
                Text(
                    text = "DOMINÓ",
                    color = DominoGreen,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "EMPREL",
                    color = DominoYellow,
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
                                    0 -> DominoGreen
                                    1 -> DominoCyan
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
                color = DominoMuted,
                fontSize = 12.sp,
                modifier = Modifier.alpha(textAlpha.value * 0.7f)
            )
        }

        // Decorative small tiles in corners
        Canvas(
            modifier = Modifier
                .size(80.dp, 40.dp)
                .align(Alignment.TopStart)
                .padding(24.dp)
                .alpha(alphaAnim.value * 0.35f)
        ) {
            drawDominoTile(this, leftDots = 3, rightDots = 1, tileColor = DominoGreen.copy(alpha = 0.6f), dotColor = Color(0xFF0B1426))
        }

        Canvas(
            modifier = Modifier
                .size(80.dp, 40.dp)
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .alpha(alphaAnim.value * 0.35f)
        ) {
            drawDominoTile(this, leftDots = 4, rightDots = 5, tileColor = DominoOrange.copy(alpha = 0.5f), dotColor = Color(0xFF0B1426))
        }
    }
}

private fun drawDominoTile(
    scope: DrawScope,
    leftDots: Int,
    rightDots: Int,
    tileColor: Color = Color.White,
    dotColor: Color = Color(0xFF0B1426)
) {
    val w = scope.size.width
    val h = scope.size.height
    val r = h * 0.14f
    val dotR = h * 0.085f
    val pad = h * 0.18f

    // Tile shadow
    scope.drawRoundRect(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(8f, 8f),
        size = Size(w, h),
        cornerRadius = CornerRadius(r)
    )
    // Tile body
    scope.drawRoundRect(
        color = tileColor,
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(r)
    )
    // Divider
    scope.drawLine(
        color = dotColor.copy(alpha = 0.25f),
        start = Offset(w / 2f, pad * 0.6f),
        end = Offset(w / 2f, h - pad * 0.6f),
        strokeWidth = 2f
    )

    // Draw dots for each half
    drawDots(scope, leftDots, 0f, w / 2f, h, dotR, pad, dotColor)
    drawDots(scope, rightDots, w / 2f, w, h, dotR, pad, dotColor)
}

private fun drawDots(
    scope: DrawScope,
    count: Int,
    xStart: Float,
    xEnd: Float,
    h: Float,
    dotR: Float,
    pad: Float,
    dotColor: Color
) {
    val cx = (xStart + xEnd) / 2f
    val cy = h / 2f
    val off = (xEnd - xStart) * 0.26f
    val vOff = h * 0.28f

    val positions: List<Offset> = when (count) {
        1 -> listOf(Offset(cx, cy))
        2 -> listOf(Offset(cx - off * 0.5f, cy - vOff * 0.5f), Offset(cx + off * 0.5f, cy + vOff * 0.5f))
        3 -> listOf(Offset(cx - off * 0.6f, cy - vOff * 0.6f), Offset(cx, cy), Offset(cx + off * 0.6f, cy + vOff * 0.6f))
        4 -> listOf(Offset(cx - off * 0.5f, cy - vOff * 0.5f), Offset(cx + off * 0.5f, cy - vOff * 0.5f), Offset(cx - off * 0.5f, cy + vOff * 0.5f), Offset(cx + off * 0.5f, cy + vOff * 0.5f))
        5 -> listOf(Offset(cx - off * 0.5f, cy - vOff * 0.5f), Offset(cx + off * 0.5f, cy - vOff * 0.5f), Offset(cx, cy), Offset(cx - off * 0.5f, cy + vOff * 0.5f), Offset(cx + off * 0.5f, cy + vOff * 0.5f))
        6 -> listOf(Offset(cx - off * 0.5f, cy - vOff * 0.7f), Offset(cx + off * 0.5f, cy - vOff * 0.7f), Offset(cx - off * 0.5f, cy), Offset(cx + off * 0.5f, cy), Offset(cx - off * 0.5f, cy + vOff * 0.7f), Offset(cx + off * 0.5f, cy + vOff * 0.7f))
        else -> emptyList()
    }

    positions.forEach { pos ->
        scope.drawCircle(color = dotColor.copy(alpha = 0.15f), radius = dotR * 1.3f, center = pos)
        scope.drawCircle(color = dotColor, radius = dotR, center = pos)
    }
}
