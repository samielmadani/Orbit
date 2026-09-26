package com.samielmadani.orbit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.samielmadani.orbit.ui.theme.AuroraViolet
import com.samielmadani.orbit.ui.theme.ElectricCyan
import com.samielmadani.orbit.ui.theme.NeonEmerald
import com.samielmadani.orbit.ui.theme.SpaceBackground
import com.samielmadani.orbit.ui.theme.SurfaceElevated
import kotlin.random.Random

private class StreamParticle(
    var t: Float,
    val speed: Float,
    val offset: Float,
    val size: Float,
    val color: Color
)

@Composable
fun TransferStreamArc(
    modifier: Modifier = Modifier,
    isOutgoing: Boolean,
    localDeviceName: String,
    targetDeviceName: String,
    isPaused: Boolean,
    speedBytesPerSec: Long
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TransferArcTransition")
    val timeTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "timeTick"
    )

    // Generate stream particles
    val particles = remember {
        List(55) {
            StreamParticle(
                t = Random.nextFloat(),
                speed = 0.007f + Random.nextFloat() * 0.012f,
                offset = (Random.nextFloat() - 0.5f) * 24f,
                size = 2.5f + Random.nextFloat() * 3.5f,
                color = if (Random.nextBoolean()) ElectricCyan else AuroraViolet
            )
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            val w = size.width
            val h = size.height

            val leftX = w * 0.22f
            val rightX = w * 0.78f
            val nodeY = h * 0.62f

            val (sourceX, destX) = if (isOutgoing) Pair(leftX, rightX) else Pair(rightX, leftX)

            val controlX = w / 2f
            val controlY = nodeY - 140f

            // 1. Draw connecting arc trajectory
            val arcPath = Path().apply {
                moveTo(sourceX, nodeY)
                quadraticTo(controlX, controlY, destX, nodeY)
            }

            drawPath(
                path = arcPath,
                color = ElectricCyan.copy(alpha = 0.18f),
                style = Stroke(width = 4f)
            )

            // 2. Draw animated particles along Bezier curve
            val activeParticleCount = (8 + speedBytesPerSec / 64_000L).toInt().coerceIn(8, particles.size)
            val speedFactor = (0.7f + speedBytesPerSec / 1_000_000f).coerceIn(0.7f, 3f)
            particles.take(activeParticleCount).forEach { p ->
                if (!isPaused) {
                    p.t += p.speed * speedFactor
                    if (p.t > 1f) p.t = 0f
                }

                val u = 1f - p.t
                val tt = p.t * p.t
                val uu = u * u

                // Quadratic Bezier formula
                val bx = uu * sourceX + 2f * u * p.t * controlX + tt * destX
                val by = uu * nodeY + 2f * u * p.t * controlY + tt * nodeY
                val tailT = (p.t - p.speed * speedFactor * 2.2f).coerceAtLeast(0f)
                val tailU = 1f - tailT
                val tailX = tailU * tailU * sourceX + 2f * tailU * tailT * controlX + tailT * tailT * destX
                val tailY = tailU * tailU * nodeY + 2f * tailU * tailT * controlY + tailT * tailT * nodeY

                drawLine(
                    color = p.color.copy(alpha = 0.45f),
                    start = Offset(tailX, tailY + p.offset),
                    end = Offset(bx, by + p.offset),
                    strokeWidth = (p.size * 0.8f).coerceAtLeast(1.2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f + (timeTick * 0.25f)),
                    radius = p.size * (0.85f + timeTick * 0.3f),
                    center = Offset(bx, by + p.offset)
                )
            }

            // 3. Draw Endpoint Devices
            drawEndpointDevice(leftX, nodeY, if (isOutgoing) localDeviceName else targetDeviceName, isOutgoing, !isOutgoing, timeTick)
            drawEndpointDevice(rightX, nodeY, if (isOutgoing) targetDeviceName else localDeviceName, !isOutgoing, isOutgoing, timeTick)
        }
    }
}

private fun DrawScope.drawEndpointDevice(
    cx: Float,
    cy: Float,
    name: String,
    isSender: Boolean,
    isReceiving: Boolean,
    pulse: Float
) {
    val accent = if (isReceiving) NeonEmerald else if (isSender) ElectricCyan else AuroraViolet

    // Base shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(cx - 36f, cy + 28f),
        size = Size(72f, 20f)
    )

    // Glowing aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = if (isReceiving) 0.42f + pulse * 0.2f else 0.35f), Color.Transparent),
            center = Offset(cx, cy),
            radius = 55f
        ),
        radius = 55f,
        center = Offset(cx, cy)
    )

    // Mini isometric phone body
    val w = 48f
    val h = 76f
    val corner = CornerRadius(12f, 12f)

    drawRoundRect(
        color = SurfaceElevated,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        cornerRadius = corner
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 2f)
    )

    // Screen
    drawRoundRect(
        color = SpaceBackground,
        topLeft = Offset(cx - w / 2f + 4f, cy - h / 2f + 4f),
        size = Size(w - 8f, h - 8f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Center pulse dot
    drawCircle(
        color = accent,
        radius = 4f,
        center = Offset(cx, cy)
    )
    if (isReceiving) {
        drawCircle(
            color = accent.copy(alpha = 0.25f + pulse * 0.35f),
            radius = 11f + pulse * 4f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
    }

    // Text Label below
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        drawText(name.take(16), cx, cy + 62f, paint)
    }
}

