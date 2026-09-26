package com.samielmadani.orbit.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.ui.components.GlassCard
import com.samielmadani.orbit.ui.components.OrbitPillButton
import com.samielmadani.orbit.ui.theme.ElectricCyan
import com.samielmadani.orbit.ui.theme.EmeraldGlow
import com.samielmadani.orbit.ui.theme.NeonEmerald
import com.samielmadani.orbit.ui.theme.SpaceBackground
import com.samielmadani.orbit.ui.theme.TextMuted
import com.samielmadani.orbit.ui.theme.TextPrimary
import com.samielmadani.orbit.ui.theme.TextSecondary

@Composable
fun CompletionScreen(
    batch: TransferBatch,
    onOpenFolderClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    // Spring bounce for container
    val bounceScale = remember { Animatable(0.2f) }
    // Draw-on progress for checkmark
    val checkmarkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        bounceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        checkmarkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(500)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Animated Emerald Checkmark & Glow
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(bounceScale.value),
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient radial emerald bloom
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(NeonEmerald.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width / 2f
                            ),
                            radius = size.width / 2f
                        )

                        // Emerald Outer Circle
                        drawCircle(
                            color = NeonEmerald,
                            radius = size.width * 0.38f,
                            style = Stroke(width = 4f)
                        )

                        // Dynamic Checkmark Draw-on
                        val progress = checkmarkProgress.value
                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        val p1 = Offset(cx - 24f, cy + 2f)
                        val p2 = Offset(cx - 6f, cy + 20f)
                        val p3 = Offset(cx + 26f, cy - 18f)

                        val checkPath = Path()
                        if (progress <= 0.4f) {
                            val subT = progress / 0.4f
                            val current = Offset(
                                p1.x + (p2.x - p1.x) * subT,
                                p1.y + (p2.y - p1.y) * subT
                            )
                            checkPath.moveTo(p1.x, p1.y)
                            checkPath.lineTo(current.x, current.y)
                        } else {
                            val subT = (progress - 0.4f) / 0.6f
                            val current = Offset(
                                p2.x + (p3.x - p2.x) * subT,
                                p2.y + (p3.y - p2.y) * subT
                            )
                            checkPath.moveTo(p1.x, p1.y)
                            checkPath.lineTo(p2.x, p2.y)
                            checkPath.lineTo(current.x, current.y)
                        }

                        drawPath(
                            path = checkPath,
                            color = NeonEmerald,
                            style = Stroke(
                                width = 7f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                Text(
                    text = "Transfer Complete",
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = if (batch.isOutgoing) "Sent cleanly to ${batch.targetDeviceName}" else "Received from ${batch.targetDeviceName}",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Summary Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Files", color = TextSecondary, fontSize = 14.sp)
                        Text(text = "${batch.items.size}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Transferred", color = TextSecondary, fontSize = 14.sp)
                        Text(text = batch.formattedTransferred().split("/").last().trim(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Storage Path", color = TextSecondary, fontSize = 14.sp)
                        Text(text = "Downloads/Orbit", color = ElectricCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!batch.isOutgoing) {
                    OrbitPillButton(
                        text = "Open in Files",
                        icon = Icons.Default.Folder,
                        onClick = onOpenFolderClick,
                        isPrimary = true
                    )
                }

                OrbitPillButton(
                    text = "Back to Orbit",
                    icon = Icons.Default.Sensors,
                    onClick = onDoneClick,
                    isPrimary = batch.isOutgoing
                )
            }
        }
    }
}
