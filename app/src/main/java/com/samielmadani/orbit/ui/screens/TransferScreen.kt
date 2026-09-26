package com.samielmadani.orbit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.model.TransferStatus
import com.samielmadani.orbit.ui.components.GlanceableMetricBadge
import com.samielmadani.orbit.ui.components.GlassCard
import com.samielmadani.orbit.ui.components.OrbitPillButton
import com.samielmadani.orbit.ui.components.TransferStreamArc
import com.samielmadani.orbit.ui.theme.AuroraViolet
import com.samielmadani.orbit.ui.theme.ElectricCyan
import com.samielmadani.orbit.ui.theme.ErrorRed
import com.samielmadani.orbit.ui.theme.NeonEmerald
import com.samielmadani.orbit.ui.theme.SolarAmber
import com.samielmadani.orbit.ui.theme.SpaceBackground
import com.samielmadani.orbit.ui.theme.SurfaceBorder
import com.samielmadani.orbit.ui.theme.SurfaceCard
import com.samielmadani.orbit.ui.theme.SurfaceElevated
import com.samielmadani.orbit.ui.theme.TextMuted
import com.samielmadani.orbit.ui.theme.TextPrimary
import com.samielmadani.orbit.ui.theme.TextSecondary

@Composable
fun TransferScreen(
    batch: TransferBatch,
    localDeviceName: String,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val isPaused = batch.status == TransferStatus.PAUSED
    val isReconnecting = batch.status == TransferStatus.RECONNECTING
    val isStorageError = batch.status == TransferStatus.STORAGE_ERROR

    val headerStatusText = when (batch.status) {
        TransferStatus.CONNECTING -> "Upgrading to Wi-Fi Direct…"
        TransferStatus.WAITING_CONFIRMATION -> "Awaiting confirmation…"
        TransferStatus.TRANSFERRING -> if (batch.isOutgoing) "Sending to ${batch.targetDeviceName}" else "Receiving from ${batch.targetDeviceName}"
        TransferStatus.PAUSED -> "Transfer Paused"
        TransferStatus.RECONNECTING -> "Connection lost — Reconnecting…"
        TransferStatus.STORAGE_ERROR -> "Storage Limit Reached"
        TransferStatus.FAILED -> "Transfer Failed"
        TransferStatus.COMPLETED -> "Transfer Complete!"
        else -> "Orbit Transfer"
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
            // Top Status Label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = headerStatusText,
                    color = when {
                        isStorageError -> ErrorRed
                        isReconnecting -> SolarAmber
                        isPaused -> SolarAmber
                        else -> ElectricCyan
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = batch.currentFileName.ifEmpty { "${batch.items.size} item(s)" },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Hero Glanceable Readout Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Giant glanceable percentage
                Text(
                    text = "${batch.progressPercent}%",
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 76.sp,
                    letterSpacing = (-2).sp,
                    lineHeight = 80.sp
                )

                // High-contrast Speed & ETA Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlanceableMetricBadge(
                        value = batch.formattedSpeed(),
                        accentColor = ElectricCyan
                    )
                    GlanceableMetricBadge(
                        value = "ETA ${batch.formattedEta()}",
                        accentColor = Color.White
                    )
                }

                // Subtitle bytes transferred
                Text(
                    text = batch.formattedTransferred(),
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Clean minimal progress line
                LinearProgressIndicator(
                    progress = { batch.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ElectricCyan,
                    trackColor = SurfaceCard
                )
            }

            // Center Dynamic Transfer Arc Visualization
            TransferStreamArc(
                modifier = Modifier.fillMaxWidth(),
                isOutgoing = batch.isOutgoing,
                localDeviceName = localDeviceName,
                targetDeviceName = batch.targetDeviceName,
                isPaused = isPaused || isReconnecting || isStorageError
            )

            // Reconnection / Storage Alert Banner
            AnimatedVisibility(visible = isReconnecting || isStorageError) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isStorageError) ErrorRed.copy(alpha = 0.15f) else SolarAmber.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isStorageError) ErrorRed else SolarAmber)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isStorageError) Icons.Default.Warning else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isStorageError) ErrorRed else SolarAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isStorageError) {
                                batch.errorMessage ?: "Insufficient device storage to complete transfer."
                            } else {
                                "Device walked out of range. Retaining progress for 60 seconds…"
                            },
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Actions: Large 56dp+ Tap Targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isPaused) {
                    OrbitPillButton(
                        text = "Resume",
                        icon = Icons.Default.PlayArrow,
                        onClick = onResumeClick,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OrbitPillButton(
                        text = "Pause",
                        icon = Icons.Default.Pause,
                        onClick = onPauseClick,
                        isPrimary = false,
                        modifier = Modifier.weight(1f)
                    )
                }

                OrbitPillButton(
                    text = "Cancel",
                    icon = Icons.Default.Close,
                    onClick = onCancelClick,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

