package com.samielmadani.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.samielmadani.orbit.model.DiscoveredDevice
import com.samielmadani.orbit.ui.theme.AuroraViolet
import com.samielmadani.orbit.ui.theme.CyanGlow
import com.samielmadani.orbit.ui.theme.CyanSecondary
import com.samielmadani.orbit.ui.theme.ElectricCyan
import com.samielmadani.orbit.ui.theme.NeonEmerald
import com.samielmadani.orbit.ui.theme.SpaceBackground
import com.samielmadani.orbit.ui.theme.SurfaceElevated
import com.samielmadani.orbit.ui.theme.SurfaceHighlight
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun OrbitalRadarCanvas(
    modifier: Modifier = Modifier,
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    onDeviceSelected: (DiscoveredDevice) -> Unit
) {
    // Continuous rotation clock for harmonic drift
    val infiniteTransition = rememberInfiniteTransition(label = "OrbitInfinite")
    val orbitTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 200).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(200_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitTime"
    )

    // Pulse wave expanding from center
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    // Floating breathing motion for center device
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    // Cache computed positions for tap detection
    val devicePositions = remember { mutableMapOf<String, Offset>() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(devices) {
                    detectTapGestures { tapOffset ->
                        // Check if tap hit any device pod
                        for (device in devices) {
                            val pos = devicePositions[device.endpointId] ?: continue
                            val dist = hypot(tapOffset.x - pos.x, tapOffset.y - pos.y)
                            if (dist <= 65f) { // Generous 65px radius hit target for easy tapping
                                onDeviceSelected(device)
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 20f

            val baseRadii = floatArrayOf(
                size.width * 0.28f,
                size.width * 0.42f,
                size.width * 0.56f
            )
            val pitch = 0.42f // 3D Isometric inclination

            // 1. Draw Concentric Orbital Trajectory Rings
            baseRadii.forEachIndexed { index, rx ->
                val ry = rx * pitch
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 16f), 0f)
                val alpha = if (index == 1) 0.12f else 0.07f

                drawOval(
                    color = Color.White.copy(alpha = alpha),
                    topLeft = Offset(cx - rx, cy - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 1.5f, pathEffect = dashEffect)
                )
            }

            // 2. Animated Signal Pulse Waves (when scanning)
            if (isScanning) {
                val maxRadius = baseRadii.last()
                val currentPulseR = maxRadius * pulseProgress
                val pulseRy = currentPulseR * pitch
                val pulseAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.35f

                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = pulseAlpha),
                            ElectricCyan.copy(alpha = 0f)
                        ),
                        center = Offset(cx, cy),
                        radius = currentPulseR
                    ),
                    topLeft = Offset(cx - currentPulseR, cy - pulseRy),
                    size = Size(currentPulseR * 2f, pulseRy * 2f)
                )

                drawOval(
                    color = ElectricCyan.copy(alpha = pulseAlpha),
                    topLeft = Offset(cx - currentPulseR, cy - pulseRy),
                    size = Size(currentPulseR * 2f, pulseRy * 2f),
                    style = Stroke(width = 2f)
                )
            }

            // 3. Draw Discovered Orbiting Device Pods
            devicePositions.clear()
            devices.forEachIndexed { idx, dev ->
                val trackRadius = baseRadii[dev.trackIndex.coerceIn(0, baseRadii.size - 1)]
                val trackRy = trackRadius * pitch
                val angle = dev.initialAngle + (orbitTime * dev.orbitalSpeed)

                val px = cx + cos(angle) * trackRadius
                val py = cy + sin(angle) * trackRy

                devicePositions[dev.endpointId] = Offset(px, py)

                // Faint connector line to center
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.08f),
                    start = Offset(cx, cy),
                    end = Offset(px, py),
                    strokeWidth = 1f
                )

                drawPeerDevicePod(
                    px = px,
                    py = py,
                    device = dev
                )
            }

            // 4. Draw Signature Stylized Isometric Device at Center
            drawCenterDeviceIsometric(
                cx = cx,
                cy = cy + floatY
            )
        }
    }
}

/**
 * Draws the user's central phone as a stylized 3D isometric illustration with neon aura.
 */
private fun DrawScope.drawCenterDeviceIsometric(cx: Float, cy: Float) {
    // Ambient radial glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(ElectricCyan.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(cx, cy),
            radius = 120f
        ),
        radius = 120f,
        center = Offset(cx, cy)
    )

    // Soft drop shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.5f),
        topLeft = Offset(cx - 50f, cy + 38f),
        size = Size(100f, 32f)
    )

    // Device Body (Chassis)
    val phoneW = 66f
    val phoneH = 105f
    val corner = CornerRadius(16f, 16f)

    // Metallic beveled border
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF2E344E), Color(0xFF161928))
        ),
        topLeft = Offset(cx - phoneW / 2f, cy - phoneH / 2f),
        size = Size(phoneW, phoneH),
        cornerRadius = corner
    )
    drawRoundRect(
        color = ElectricCyan.copy(alpha = 0.6f),
        topLeft = Offset(cx - phoneW / 2f, cy - phoneH / 2f),
        size = Size(phoneW, phoneH),
        cornerRadius = corner,
        style = Stroke(width = 2f)
    )

    // Screen Glass
    val screenInset = 4f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), SpaceBackground)
        ),
        topLeft = Offset(cx - phoneW / 2f + screenInset, cy - phoneH / 2f + screenInset),
        size = Size(phoneW - screenInset * 2f, phoneH - screenInset * 2f),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // Screen Orbit Core Glyph
    drawCircle(
        color = ElectricCyan.copy(alpha = 0.2f),
        radius = 14f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = ElectricCyan,
        radius = 5f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color.White,
        radius = 2f,
        center = Offset(cx, cy)
    )
}

/**
 * Draws an orbiting peer device pod with high-contrast label tag.
 */
private fun DrawScope.drawPeerDevicePod(
    px: Float,
    py: Float,
    device: DiscoveredDevice
) {
    val podColor = if (device.isConnecting) AuroraViolet else ElectricCyan

    // Ambient glow under pod
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(podColor.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(px, py),
            radius = 42f
        ),
        radius = 42f,
        center = Offset(px, py)
    )

    // Pod chassis
    val w = 36f
    val h = 54f
    val corner = CornerRadius(10f, 10f)

    drawRoundRect(
        color = SurfaceElevated,
        topLeft = Offset(px - w / 2f, py - h / 2f),
        size = Size(w, h),
        cornerRadius = corner
    )
    drawRoundRect(
        color = podColor,
        topLeft = Offset(px - w / 2f, py - h / 2f),
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.5f)
    )

    // Pod screen
    drawRoundRect(
        color = SpaceBackground,
        topLeft = Offset(px - w / 2f + 3f, py - h / 2f + 3f),
        size = Size(w - 6f, h - 6f),
        cornerRadius = CornerRadius(7f, 7f)
    )

    // Inner status light
    drawCircle(
        color = podColor,
        radius = 3.5f,
        center = Offset(px, py)
    )

    // Device Label Badge (Draw text via native canvas)
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val text = device.deviceName
        val textWidth = paint.measureText(text)
        val badgeW = textWidth + 36f
        val badgeH = 46f
        val badgeY = py + 38f

        // Badge background capsule
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(220, 18, 20, 31)
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
        val strokePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(80, 255, 255, 255)
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
        }

        val rectF = android.graphics.RectF(px - badgeW / 2f, badgeY, px + badgeW / 2f, badgeY + badgeH)
        drawRoundRect(rectF, 23f, 23f, bgPaint)
        drawRoundRect(rectF, 23f, 23f, strokePaint)

        // Text string
        drawText(text, px, badgeY + 31f, paint)
    }
}

