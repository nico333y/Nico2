package com.nico2.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val VoidBlack = Color(0xFF020304)
private val PearlBlack = Color(0xFF0A0E12)
private val Graphite = Color(0xFF171D23)
private val Gold = Color(0xFFFFBE45)
private val BrightGold = Color(0xFFFFE7A0)
private val DeepGold = Color(0xFF9D6112)

enum class OrbVisualState {
    Calm,
    Connecting,
    Listening,
    Thinking,
    Responding
}

@Composable
fun PremiumLivingOrb(
    modifier: Modifier = Modifier,
    state: OrbVisualState = OrbVisualState.Calm,
    reducedMotion: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "premium_orb_transition")

    val animation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbVisualState.Calm -> 7800
                    OrbVisualState.Connecting -> 4200
                    OrbVisualState.Listening -> 2800
                    OrbVisualState.Thinking -> 2200
                    OrbVisualState.Responding -> 1500
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_animation"
    )

    Canvas(modifier = modifier) {
        val phase = if (reducedMotion) 0f else animation
        val center = this.center
        val minSize = size.minDimension
        val coreRadius = minSize * 0.18f

        val intensity = when (state) {
            OrbVisualState.Calm -> 0.82f
            OrbVisualState.Connecting -> 0.98f
            OrbVisualState.Listening -> 1.08f
            OrbVisualState.Thinking -> 1.16f
            OrbVisualState.Responding -> 1.28f
        }

        val breathing = if (reducedMotion) {
            1f
        } else {
            1f + sin(phase * Math.PI * 2).toFloat() * 0.035f
        }

        val radius = coreRadius * intensity * breathing

        drawAtmosphere(
            center = center,
            radius = radius,
            phase = phase,
            intensity = intensity
        )

        drawEnergyOrbit(
            center = center,
            radius = radius * 1.85f,
            phase = phase * 1.00f,
            horizontalScale = 1.00f,
            verticalScale = 0.34f,
            rotation = -16f,
            strokeWidth = radius * 0.043f,
            alpha = 0.78f
        )

        drawEnergyOrbit(
            center = center,
            radius = radius * 2.12f,
            phase = -phase * 0.72f,
            horizontalScale = 0.78f,
            verticalScale = 0.48f,
            rotation = 52f,
            strokeWidth = radius * 0.032f,
            alpha = 0.53f
        )

        drawEnergyOrbit(
            center = center,
            radius = radius * 2.32f,
            phase = phase * 0.44f,
            horizontalScale = 0.66f,
            verticalScale = 0.62f,
            rotation = -62f,
            strokeWidth = radius * 0.024f,
            alpha = 0.34f
        )

        if (!reducedMotion) {
            drawParticles(
                center = center,
                radius = radius,
                phase = phase,
                intensity = intensity
            )
        }

        drawLiquidCore(
            center = center,
            radius = radius
        )

        drawInternalEnergy(
            center = center,
            radius = radius,
            phase = phase,
            state = state
        )

        drawRotatingHighlight(
            center = center,
            radius = radius,
            phase = phase
        )
    }
}

private fun DrawScope.drawAtmosphere(
    center: Offset,
    radius: Float,
    phase: Float,
    intensity: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                BrightGold.copy(alpha = 0.17f * intensity),
                Gold.copy(alpha = 0.07f * intensity),
                Color.Transparent
            ),
            center = center,
            radius = radius * 3.4f
        ),
        radius = radius * 3.4f,
        center = center
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Gold.copy(alpha = 0.12f),
                Color.Transparent
            ),
            center = Offset(
                center.x + sin(phase * Math.PI * 2).toFloat() * radius * 0.4f,
                center.y
            ),
            radius = radius * 2.5f
        ),
        radius = radius * 2.5f,
        center = center
    )
}

private fun DrawScope.drawLiquidCore(
    center: Offset,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF2A333B),
                Graphite,
                PearlBlack,
                VoidBlack
            ),
            center = Offset(
                center.x - radius * 0.25f,
                center.y - radius * 0.32f
            ),
            radius = radius * 1.55f
        ),
        radius = radius,
        center = center
    )

    drawCircle(
        color = Color(0x33000000),
        radius = radius * 0.94f,
        center = Offset(
            center.x + radius * 0.10f,
            center.y + radius * 0.14f
        )
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.13f),
        radius = radius * 0.09f,
        center = Offset(
            center.x - radius * 0.36f,
            center.y - radius * 0.43f
        )
    )
}

private fun DrawScope.drawInternalEnergy(
    center: Offset,
    radius: Float,
    phase: Float,
    state: OrbVisualState,
) {
    val stateAlpha = when (state) {
        OrbVisualState.Calm -> 0.48f
        OrbVisualState.Connecting -> 0.66f
        OrbVisualState.Listening -> 0.78f
        OrbVisualState.Thinking -> 0.90f
        OrbVisualState.Responding -> 1.00f
    }

    val liquidPath = Path().apply {
        moveTo(
            center.x - radius * 0.58f,
            center.y + radius * 0.08f
        )

        cubicTo(
            center.x - radius * 0.38f,
            center.y - radius * 0.40f,
            center.x - radius * 0.05f,
            center.y - radius * 0.12f,
            center.x + radius * 0.58f,
            center.y - radius * 0.26f
        )

        cubicTo(
            center.x + radius * 0.26f,
            center.y + radius * 0.16f,
            center.x - radius * 0.10f,
            center.y + radius * 0.47f,
            center.x - radius * 0.58f,
            center.y + radius * 0.08f
        )

        close()
    }

    drawPath(
        path = liquidPath,
        brush = Brush.linearGradient(
            colors = listOf(
                BrightGold.copy(alpha = stateAlpha),
                Gold.copy(alpha = stateAlpha * 0.72f),
                DeepGold.copy(alpha = stateAlpha * 0.35f),
                Color.Transparent
            )
        ),
        style = Stroke(
            width = radius * 0.055f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Gold.copy(alpha = stateAlpha),
                BrightGold.copy(alpha = stateAlpha),
                DeepGold.copy(alpha = stateAlpha * 0.8f),
                Color.Transparent
            )
        ),
        startAngle = -74f + phase * 360f,
        sweepAngle = 138f,
        useCenter = false,
        topLeft = Offset(
            center.x - radius * 1.08f,
            center.y - radius * 1.08f
        ),
        size = Size(
            radius * 2.16f,
            radius * 2.16f
        ),
        style = Stroke(
            width = radius * 0.036f,
            cap = StrokeCap.Round
        )
    )
}

private fun DrawScope.drawRotatingHighlight(
    center: Offset,
    radius: Float,
    phase: Float,
) {
    val angle = phase * Math.PI * 2
    val point = Offset(
        center.x + cos(angle).toFloat() * radius * 0.72f,
        center.y + sin(angle).toFloat() * radius * 0.72f
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                BrightGold.copy(alpha = 0.70f),
                Gold.copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = point,
            radius = radius * 0.34f
        ),
        radius = radius * 0.34f,
        center = point
    )
}

private fun DrawScope.drawEnergyOrbit(
    center: Offset,
    radius: Float,
    phase: Float,
    horizontalScale: Float,
    verticalScale: Float,
    rotation: Float,
    strokeWidth: Float,
    alpha: Float,
) {
    val path = Path()
    val samples = 90
    val rotationRad = Math.toRadians(rotation.toDouble())

    for (i in 0..samples) {
        val progress = i.toFloat() / samples
        val angle = progress * Math.PI * 2.0 + phase * Math.PI * 2.0

        val wobble = 1f + (
            sin(angle * 3.0 + phase * 5.0) * 0.075
        ).toFloat()

        val rawX = cos(angle).toFloat() * radius * horizontalScale * wobble
        val rawY = sin(angle).toFloat() * radius * verticalScale * wobble

        val x = rawX * cos(rotationRad).toFloat() -
            rawY * sin(rotationRad).toFloat()

        val y = rawX * sin(rotationRad).toFloat() +
            rawY * cos(rotationRad).toFloat()

        val point = Offset(
            center.x + x,
            center.y + y
        )

        if (i == 0) {
            path.moveTo(point.x, point.y)
        } else {
            path.lineTo(point.x, point.y)
        }
    }

    drawPath(
        path = path,
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Gold.copy(alpha = alpha),
                BrightGold.copy(alpha = alpha * 0.85f),
                DeepGold.copy(alpha = alpha * 0.75f),
                Color.Transparent
            )
        ),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawParticles(
    center: Offset,
    radius: Float,
    phase: Float,
    intensity: Float,
) {
    val count = 18

    repeat(count) { index ->
        val direction = if (index % 2 == 0) 1f else -1f
        val angle = (
            index * (Math.PI * 2.0 / count) +
                phase * direction * 1.1
            )

        val orbit = radius * (
            2.05f + (index % 5) * 0.17f
        )

        val point = Offset(
            center.x + cos(angle).toFloat() * orbit,
            center.y + sin(angle).toFloat() * orbit * 0.64f
        )

        drawCircle(
            color = Gold.copy(
                alpha = (0.13f + (index % 4) * 0.055f) * intensity
            ),
            radius = (1.2f + (index % 3)).dp.toPx(),
            center = point
        )
    }
}