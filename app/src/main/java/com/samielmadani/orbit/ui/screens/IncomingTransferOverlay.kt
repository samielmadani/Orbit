package com.samielmadani.orbit.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.ui.theme.ElectricCyan
import com.samielmadani.orbit.ui.theme.NeonEmerald
import com.samielmadani.orbit.ui.theme.SpaceBackground
import com.samielmadani.orbit.ui.theme.SurfaceBorder
import com.samielmadani.orbit.ui.theme.SurfaceElevated
import com.samielmadani.orbit.ui.theme.TextPrimary
import com.samielmadani.orbit.ui.theme.TextSecondary

@Composable
fun IncomingTransferOverlay(
	request: TransferBatch?,
	onAccept: () -> Unit,
	onDecline: () -> Unit
) {
	AnimatedContent(
		targetState = request,
		modifier = Modifier.fillMaxSize(),
		transitionSpec = {
			if (targetState != null) {
				(fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.78f, stiffness = 360f))) togetherWith
					(fadeOut(tween(150)) + scaleOut(targetScale = 0.98f, animationSpec = tween(150)))
			} else {
				(fadeIn(tween(150)) + scaleIn(initialScale = 0.98f, animationSpec = tween(150))) togetherWith
					(fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = spring(stiffness = 420f)))
			}
		},
		label = "IncomingTransferOverlay"
	) { visibleRequest ->
		if (visibleRequest != null) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color(0xE808090E))
					.padding(24.dp),
				contentAlignment = Alignment.Center
			) {
				Surface(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 760.dp),
					shape = RoundedCornerShape(24.dp),
					color = SurfaceElevated,
					border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.25f))
				) {
					Column(
						modifier = Modifier
							.verticalScroll(rememberScrollState())
							.padding(horizontal = 24.dp, vertical = 28.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(16.dp)
					) {
						Icon(
							imageVector = Icons.Default.Sensors,
							contentDescription = null,
							tint = ElectricCyan,
							modifier = Modifier.size(36.dp)
						)
						Text(
							text = "Incoming transfer",
							color = TextSecondary,
							fontSize = 14.sp,
							fontWeight = FontWeight.SemiBold
						)
						Text(
							text = "${visibleRequest.targetDeviceName} wants to share",
							color = TextPrimary,
							fontSize = 25.sp,
							fontWeight = FontWeight.Bold,
							textAlign = TextAlign.Center
						)
						Text(
							text = if (visibleRequest.items.isEmpty()) {
								"Wants to share files or text with you."
							} else {
								"${visibleRequest.items.size} ${if (visibleRequest.items.size == 1) "item" else "items"} · ${visibleRequest.formattedTransferred().substringAfter("/").trim()} total"
							},
							color = TextSecondary,
							fontSize = 16.sp,
							textAlign = TextAlign.Center
						)
						visibleRequest.items.take(3).forEach { item ->
							Column(
								modifier = Modifier.fillMaxWidth(),
								verticalArrangement = Arrangement.spacedBy(4.dp)
							) {
								Text(
									text = item.name,
									color = TextPrimary,
									fontSize = 15.sp,
									fontWeight = FontWeight.SemiBold
								)
								Text(
									text = item.textContent?.take(96) ?: item.formattedSize(),
									color = TextSecondary,
									fontSize = 14.sp
								)
							}
						}
						if (visibleRequest.items.size > 3) {
							Text(
								text = "+ ${visibleRequest.items.size - 3} more items",
								color = TextSecondary,
								fontSize = 14.sp
							)
						}
						Spacer(Modifier.height(4.dp))
						Button(
							onClick = onAccept,
							modifier = Modifier
								.fillMaxWidth()
								.height(60.dp),
							shape = RoundedCornerShape(16.dp),
							colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
						) {
							Text("Accept & receive", color = SpaceBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
						}
						OutlinedButton(
							onClick = onDecline,
							modifier = Modifier
								.fillMaxWidth()
								.height(56.dp),
							shape = RoundedCornerShape(16.dp),
							border = BorderStroke(1.dp, SurfaceBorder)
						) {
							Text("Decline", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
						}
					}
				}
			}
		}
	}
}
