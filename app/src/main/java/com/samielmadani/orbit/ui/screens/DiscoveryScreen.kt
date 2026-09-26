package com.samielmadani.orbit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samielmadani.orbit.data.RecentDevice
import com.samielmadani.orbit.model.DiscoveredDevice
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.ui.components.GlassCard
import com.samielmadani.orbit.ui.components.OrbitPillButton
import com.samielmadani.orbit.ui.components.OrbitalRadarCanvas
import com.samielmadani.orbit.ui.theme.AuroraViolet
import com.samielmadani.orbit.ui.theme.ElectricCyan
import com.samielmadani.orbit.ui.theme.NeonEmerald
import com.samielmadani.orbit.ui.theme.SolarAmber
import com.samielmadani.orbit.ui.theme.SpaceBackground
import com.samielmadani.orbit.ui.theme.SurfaceBorder
import com.samielmadani.orbit.ui.theme.SurfaceCard
import com.samielmadani.orbit.ui.theme.SurfaceElevated
import com.samielmadani.orbit.ui.theme.SurfaceHighlight
import com.samielmadani.orbit.ui.theme.TextMuted
import com.samielmadani.orbit.ui.theme.TextPrimary
import com.samielmadani.orbit.ui.theme.TextSecondary

@Composable
fun DiscoveryScreen(
    localDeviceName: String,
    devices: List<DiscoveredDevice>,
    recentDevices: List<RecentDevice>,
    isScanning: Boolean,
    isReceivingReady: Boolean,
    incomingRequest: TransferBatch?,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onRecentDeviceClick: (RecentDevice) -> Unit,
    onPickFilesClick: () -> Unit,
    onSendTextClick: () -> Unit,
    onAcceptIncoming: () -> Unit,
    onRejectIncoming: () -> Unit
) {
    val receivePulse by rememberInfiniteTransition(label = "ReceiveReadyPulse").animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "receivePulse"
    )

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
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Orbit",
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isReceivingReady) NeonEmerald else TextMuted)
                        )
                    }
                    Text(
                        text = localDeviceName,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Scanning Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isReceivingReady) NeonEmerald.copy(alpha = 0.15f) else SurfaceCard,
                    border = BorderStroke(1.dp, if (isReceivingReady) NeonEmerald.copy(alpha = 0.55f) else SurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (isReceivingReady) NeonEmerald else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                isReceivingReady -> "Ready to receive"
                                isScanning -> "Finding devices"
                                else -> "Not visible"
                            },
                            color = if (isReceivingReady) NeonEmerald else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Orbital Radar Canvas (Signature Experience)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                OrbitalRadarCanvas(
                    modifier = Modifier.fillMaxSize(),
                    devices = devices,
                    isScanning = isScanning,
                    isReceivingMode = isReceivingReady,
                    onDeviceSelected = onDeviceSelected
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isReceivingReady) NeonEmerald.copy(alpha = 0.13f) else SurfaceCard.copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.dp,
                        if (isReceivingReady) NeonEmerald.copy(alpha = 0.48f) else SurfaceBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(if (isReceivingReady) NeonEmerald.copy(alpha = receivePulse) else TextMuted)
                        )
                        Column {
                            Text(
                                text = if (isReceivingReady) "Your device is visible" else "Nearby visibility is off",
                                color = if (isReceivingReady) NeonEmerald else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isReceivingReady) "Ready to receive files and text" else "Allow nearby access to receive transfers",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Looking for nearby Orbit devices…",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Controls Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                backgroundColor = SurfaceElevated.copy(alpha = 0.95f),
                cornerRadius = 28.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // One-Tap Repeat Send Bar (Recents)
                    if (recentDevices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = AuroraViolet,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Recently in Orbit (1-Tap Send)",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentDevices.forEach { recent ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onRecentDeviceClick(recent) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = SurfaceHighlight,
                                        border = BorderStroke(1.dp, AuroraViolet.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Devices,
                                                contentDescription = null,
                                                tint = AuroraViolet,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = recent.name,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OrbitPillButton(
                            text = "Send Files",
                            icon = Icons.Default.FolderOpen,
                            onClick = onPickFilesClick,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )

                        OrbitPillButton(
                            text = "Send Text",
                            icon = Icons.Default.Link,
                            onClick = onSendTextClick,
                            isPrimary = false,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }
            }
        }

        IncomingTransferOverlay(
            request = incomingRequest,
            onAccept = onAcceptIncoming,
            onDecline = onRejectIncoming
        )
    }
}

