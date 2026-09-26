package com.samielmadani.orbit

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.samielmadani.orbit.model.DiscoveredDevice
import com.samielmadani.orbit.model.TransferStatus
import com.samielmadani.orbit.ui.OrbitViewModel
import com.samielmadani.orbit.ui.screens.CompletionScreen
import com.samielmadani.orbit.ui.screens.DiscoveryScreen
import com.samielmadani.orbit.ui.screens.PermissionSheet
import com.samielmadani.orbit.ui.screens.SendTextDialog
import com.samielmadani.orbit.ui.screens.TransferScreen
import com.samielmadani.orbit.ui.theme.OrbitTheme
import com.samielmadani.orbit.ui.theme.SpaceBackground

class MainActivity : ComponentActivity() {

    private val viewModel: OrbitViewModel by viewModels()

    // Permissions launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsResult(results)
    }

    // Document file picker launcher
    private var pendingTargetForPicker: DiscoveredDevice? = null
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.stageUrisForSending(uris, pendingTargetForPicker)
            pendingTargetForPicker = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Handle incoming share intents from other apps
        handleIncomingIntent(intent)

        setContent {
            OrbitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SpaceBackground
                ) {
                    OrbitAppContent(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            val perms = viewModel.getRequiredPermissionsList().toTypedArray()
                            permissionLauncher.launch(perms)
                        },
                        onPickFiles = { targetDevice ->
                            pendingTargetForPicker = targetDevice
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenDownloads = {
                            openDownloadsFolder()
                        }
                    )
                }
            }
        }

        // Auto-request scanning if permissions already satisfied
        if (viewModel.hasRequiredPermissions()) {
            viewModel.requestStartScanning()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                if (uri != null) {
                    viewModel.stageUrisForSending(listOf(uri))
                    Toast.makeText(this, "File queued in Orbit — tap any nearby device to send", Toast.LENGTH_SHORT).show()
                } else if (!text.isNullOrBlank()) {
                    viewModel.sendTextContent(text)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    viewModel.stageUrisForSending(uris)
                    Toast.makeText(this, "${uris.size} files queued in Orbit — select a peer", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openDownloadsFolder() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + "/Orbit"),
                "resource/folder"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Files saved in Downloads/Orbit", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun OrbitAppContent(
    viewModel: OrbitViewModel,
    onRequestPermissions: () -> Unit,
    onPickFiles: (DiscoveredDevice?) -> Unit,
    onOpenDownloads: () -> Unit
) {
    val activeBatch by viewModel.activeBatch.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val recentDevices by viewModel.recentDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val incomingRequest by viewModel.incomingRequest.collectAsState()
    val showPermissionSheet by viewModel.showPermissionSheet.collectAsState()
    val showSendTextDialog by viewModel.showSendTextDialog.collectAsState()

    // Smooth state transitions between Discovery, Transfer, and Completion
    AnimatedContent(
        targetState = activeBatch?.status,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(
                initialScale = 0.96f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )) togetherWith (fadeOut(tween(140)) + scaleOut(targetScale = 0.985f, animationSpec = tween(140)))
        },
        label = "OrbitScreenTransition"
    ) { status ->
        when (status) {
            TransferStatus.COMPLETED -> {
                CompletionScreen(
                    batch = activeBatch!!,
                    onOpenFolderClick = onOpenDownloads,
                    onDoneClick = { viewModel.dismissCompletion() }
                )
            }
            TransferStatus.TRANSFERRING,
            TransferStatus.CONNECTING,
            TransferStatus.WAITING_CONFIRMATION,
            TransferStatus.PAUSED,
            TransferStatus.RECONNECTING,
            TransferStatus.STORAGE_ERROR,
            TransferStatus.FAILED -> {
                TransferScreen(
                    batch = activeBatch!!,
                    localDeviceName = viewModel.localDeviceName,
                    onPauseClick = { viewModel.pauseTransfer() },
                    onResumeClick = { viewModel.resumeTransfer() },
                    onCancelClick = { viewModel.cancelTransfer() }
                )
            }
            else -> {
                DiscoveryScreen(
                    localDeviceName = viewModel.localDeviceName,
                    devices = discoveredDevices,
                    recentDevices = recentDevices,
                    isScanning = isScanning,
                    isReceivingReady = isScanning && isAdvertising,
                    incomingRequest = incomingRequest,
                    onDeviceSelected = { device ->
                        viewModel.onDeviceSelected(device)
                        onPickFiles(device)
                    },
                    onRecentDeviceClick = { recent ->
                        viewModel.onRecentDeviceSelected(recent)
                        onPickFiles(null)
                    },
                    onPickFilesClick = {
                        onPickFiles(null)
                    },
                    onSendTextClick = {
                        viewModel.openSendTextDialog()
                    },
                    onAcceptIncoming = { viewModel.acceptIncoming() },
                    onRejectIncoming = { viewModel.rejectIncoming() }
                )
            }
        }
    }

    // Permission bottom sheet
    if (showPermissionSheet) {
        PermissionSheet(
            onGrantClick = {
                viewModel.dismissPermissionSheet()
                onRequestPermissions()
            },
            onDismiss = { viewModel.dismissPermissionSheet() }
        )
    }

    // Send Text / Link Dialog
    if (showSendTextDialog) {
        SendTextDialog(
            onDismiss = { viewModel.closeSendTextDialog() },
            onSendText = { text -> viewModel.sendTextContent(text) }
        )
    }
}
