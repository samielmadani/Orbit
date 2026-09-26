package com.samielmadani.orbit.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.samielmadani.orbit.model.DiscoveredDevice
import com.samielmadani.orbit.model.ManifestItem
import com.samielmadani.orbit.model.ManifestPayload
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.model.TransferItem
import com.samielmadani.orbit.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

sealed class NearbyEvent {
    data class IncomingRequest(
        val endpointId: String,
        val deviceName: String,
        val authenticationToken: String
    ) : NearbyEvent()
    data class IncomingTransferReady(val batch: TransferBatch) : NearbyEvent()
    data class ConnectionAccepted(val endpointId: String, val deviceName: String) : NearbyEvent()
    data class ConnectionRejected(val endpointId: String) : NearbyEvent()
    data class TransferFinished(val batch: TransferBatch) : NearbyEvent()
    data class Error(val message: String) : NearbyEvent()
}

class NearbyManager(private val context: Context) {

    companion object {
        private const val TAG = "OrbitNearby"
        const val SERVICE_ID = "com.samielmadani.orbit"
        val STRATEGY: Strategy = Strategy.P2P_STAR

        // Control Payload Prefixes
        private const val CTRL_PAUSE = "ORBIT_CTRL:PAUSE"
        private const val CTRL_RESUME = "ORBIT_CTRL:RESUME"
        private const val CTRL_CANCEL = "ORBIT_CTRL:CANCEL"
        private const val CTRL_ACCEPT = "ORBIT_CTRL:ACCEPT"
        private const val CTRL_COMPLETE = "ORBIT_CTRL:COMPLETE"
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    val recentDevicesStore = RecentDevicesStore(context)

    // Device state
    val localDeviceName: String by lazy {
        val model = Build.MODEL ?: "Android Device"
        if (model.startsWith("Pixel", ignoreCase = true) || model.startsWith("Galaxy", ignoreCase = true)) {
            model
        } else {
            "Orbit Device ($model)"
        }
    }

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _activeBatch = MutableStateFlow<TransferBatch?>(null)
    val activeBatch: StateFlow<TransferBatch?> = _activeBatch.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _events = MutableSharedFlow<NearbyEvent>()
    val events: SharedFlow<NearbyEvent> = _events.asSharedFlow()

    // Internal tracking
    private var connectedEndpointId: String? = null
    private var connectedDeviceName: String? = null
    private var pendingManifest: ManifestPayload? = null

    // Speed calculation metrics
    private var lastSpeedCalculationTime: Long = 0L
    private var lastBytesTransferred: Long = 0L
    private var currentSpeedBps: Long = 0L

    // Reconnection handling
    private var reconnectJob: Job? = null

    // Active payload tracking: payloadId -> (file, item)
    private val incomingFilePayloads = mutableMapOf<Long, Pair<File, ManifestItem>>()
    private val outgoingPayloads = mutableMapOf<Long, TransferItem>()
    private val outgoingItemsAwaitingApproval = mutableMapOf<String, List<TransferItem>>()

    // -------------------------------------------------------------
    // Lifecycle & Discovery
    // -------------------------------------------------------------

    fun startNearby() {
        startAdvertising()
        startDiscovery()
    }

    fun stopNearby() {
        stopDiscovery()
        stopAdvertising()
        disconnectAll()
    }

    fun startAdvertising() {
        if (_isAdvertising.value) return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started as $localDeviceName")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed", e)
            _isAdvertising.value = false
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        if (_isScanning.value) return
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
            _isScanning.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isScanning.value = false
    }

    // -------------------------------------------------------------
    // Connection Lifecycle
    // -------------------------------------------------------------

    fun initiateConnection(device: DiscoveredDevice) {
        val currentDevices = _discoveredDevices.value.map {
            if (it.endpointId == device.endpointId) it.copy(isConnecting = true) else it
        }
        _discoveredDevices.value = currentDevices

        connectionsClient.requestConnection(
            localDeviceName,
            device.endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection requested to ${device.deviceName}")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed requesting connection to ${device.deviceName}", e)
            resetConnectingState(device.endpointId)
        }
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Accepted connection to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed accepting connection to $endpointId", e)
            }
    }

    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        connectedEndpointId = null
    }

    fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        connectedDeviceName = null
        _activeBatch.value = null
    }

    private fun resetConnectingState(endpointId: String) {
        _discoveredDevices.value = _discoveredDevices.value.map {
            if (it.endpointId == endpointId) it.copy(isConnecting = false) else it
        }
    }

    // -------------------------------------------------------------
    // Data Transfer (Send)
    // -------------------------------------------------------------

    fun sendItemsToEndpoint(
        endpointId: String,
        targetDeviceName: String,
        items: List<TransferItem>
    ) {
        connectedEndpointId = endpointId
        connectedDeviceName = targetDeviceName
        recentDevicesStore.recordDevice(targetDeviceName, endpointId)

        val totalBytes = items.sumOf { it.sizeBytes }
        val batchId = UUID.randomUUID().toString()

        val batch = TransferBatch(
            batchId = batchId,
            targetDeviceName = targetDeviceName,
            targetEndpointId = endpointId,
            isOutgoing = true,
            items = items,
            totalBytes = totalBytes,
            status = TransferStatus.CONNECTING
        )
        _activeBatch.value = batch

        outgoingItemsAwaitingApproval[endpointId] = items

        // Share metadata first; file payloads wait for receiver approval.
        val manifestItems = items.map {
            ManifestItem(
                name = it.name,
                sizeBytes = it.sizeBytes,
                relativePath = it.relativePath,
                mimeType = it.mimeType,
                isText = it.isText,
                textContent = it.textContent
            )
        }
        val manifest = ManifestPayload(
            batchId = batchId,
            senderDeviceName = localDeviceName,
            totalFiles = items.size,
            totalBytes = totalBytes,
            items = manifestItems
        )

        val manifestPayload = Payload.fromBytes(manifest.toJsonString().toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, manifestPayload).addOnSuccessListener {
            Log.d(TAG, "Manifest payload dispatched")
        }.addOnFailureListener { e ->
            outgoingItemsAwaitingApproval.remove(endpointId)
            Log.e(TAG, "Failed sending manifest", e)
            _activeBatch.value = batch.copy(
                status = TransferStatus.FAILED,
                errorMessage = "Failed to initiate transfer: ${e.message}"
            )
        }
    }

    private fun sendPayloadFiles(endpointId: String, items: List<TransferItem>) {
        _activeBatch.value = _activeBatch.value?.copy(status = TransferStatus.TRANSFERRING)
        lastSpeedCalculationTime = System.currentTimeMillis()
        lastBytesTransferred = 0L

        items.forEach { item ->
            if (item.isText) {
                // Text items are already in manifest
                return@forEach
            }

            try {
                val uri = item.uriString?.let { Uri.parse(it) }
                if (uri != null) {
                    val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val filePayload = Payload.fromFile(pfd)
                        outgoingPayloads[filePayload.id] = item
                        connectionsClient.sendPayload(endpointId, filePayload)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending payload for item ${item.name}", e)
            }
        }
    }

    // -------------------------------------------------------------
    // Transfer Control (Pause / Resume / Cancel)
    // -------------------------------------------------------------

    fun pauseTransfer() {
        val batch = _activeBatch.value ?: return
        connectedEndpointId?.let { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(CTRL_PAUSE.toByteArray()))
        }
        _activeBatch.value = batch.copy(status = TransferStatus.PAUSED)
    }

    fun resumeTransfer() {
        val batch = _activeBatch.value ?: return
        connectedEndpointId?.let { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(CTRL_RESUME.toByteArray()))
        }
        _activeBatch.value = batch.copy(status = TransferStatus.TRANSFERRING)
    }

    fun cancelTransfer() {
        val batch = _activeBatch.value ?: return
        connectedEndpointId?.let { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(CTRL_CANCEL.toByteArray()))
        }
        outgoingPayloads.keys.forEach { payloadId ->
            connectionsClient.cancelPayload(payloadId)
        }
        _activeBatch.value = batch.copy(status = TransferStatus.CANCELLED)
    }

    fun acceptIncomingTransfer(batch: TransferBatch) {
        connectedEndpointId = batch.targetEndpointId
        connectedDeviceName = batch.targetDeviceName
        _activeBatch.value = batch.copy(status = TransferStatus.TRANSFERRING)
        connectionsClient.sendPayload(
            batch.targetEndpointId,
            Payload.fromBytes(CTRL_ACCEPT.toByteArray(Charsets.UTF_8))
        ).addOnFailureListener { error ->
            _activeBatch.value = _activeBatch.value?.copy(
                status = TransferStatus.FAILED,
                errorMessage = "Could not approve the incoming transfer: ${error.message}"
            )
        }
    }

    fun declineIncomingTransfer(batch: TransferBatch) {
        connectionsClient.sendPayload(
            batch.targetEndpointId,
            Payload.fromBytes(CTRL_CANCEL.toByteArray(Charsets.UTF_8))
        )
        connectionsClient.disconnectFromEndpoint(batch.targetEndpointId)
        pendingManifest = null
    }

    // -------------------------------------------------------------
    // Callbacks: Discovery
    // -------------------------------------------------------------

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            val recents = recentDevicesStore.getRecentDevices()
            val isRecent = recents.any { it.name.equals(info.endpointName, ignoreCase = true) }

            val currentList = _discoveredDevices.value.toMutableList()
            if (currentList.none { it.endpointId == endpointId }) {
                val newDevice = DiscoveredDevice(
                    endpointId = endpointId,
                    deviceName = info.endpointName,
                    trackIndex = (currentList.size % 3),
                    isRecent = isRecent
                )
                currentList.add(newDevice)
                _discoveredDevices.value = currentList
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            _discoveredDevices.value = _discoveredDevices.value.filter { it.endpointId != endpointId }
        }
    }

    // -------------------------------------------------------------
    // Callbacks: Lifecycle
    // -------------------------------------------------------------

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated: $endpointId (${info.endpointName})")
            connectedEndpointId = endpointId
            connectedDeviceName = info.endpointName

            acceptConnection(endpointId)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection established successfully to $endpointId")
                    reconnectJob?.cancel()
                    resetConnectingState(endpointId)
                    scope.launch {
                        _events.emit(NearbyEvent.ConnectionAccepted(endpointId, connectedDeviceName ?: "Device"))
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                    resetConnectingState(endpointId)
                    scope.launch { _events.emit(NearbyEvent.ConnectionRejected(endpointId)) }
                }
                else -> {
                    Log.w(TAG, "Connection failed: ${resolution.status.statusMessage}")
                    resetConnectingState(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            val batch = _activeBatch.value
            if (batch != null && batch.status == TransferStatus.TRANSFERRING) {
                // Connection dropped mid-transfer: enter RECONNECTING buffer window
                _activeBatch.value = batch.copy(status = TransferStatus.RECONNECTING)
                handleReconnectionWindow(endpointId)
            } else {
                connectedEndpointId = null
                connectedDeviceName = null
                resetConnectingState(endpointId)
            }
        }
    }

    private fun handleReconnectionWindow(endpointId: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Holding transfer state for 60s reconnection buffer...")
            // Attempt to re-discover and reconnect
            startDiscovery()
            delay(60_000L)
            // If still reconnecting after 60s, mark as failed
            val current = _activeBatch.value
            if (current != null && current.status == TransferStatus.RECONNECTING) {
                _activeBatch.value = current.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = "Connection lost. Peer device moved out of range."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Callbacks: Payloads
    // -------------------------------------------------------------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val message = String(bytes, Charsets.UTF_8)

                    when {
                        message == CTRL_ACCEPT -> {
                            val approvedItems = outgoingItemsAwaitingApproval.remove(endpointId)
                            if (approvedItems != null) {
                                sendPayloadFiles(endpointId, approvedItems)
                                if (approvedItems.all { it.isText }) {
                                    connectionsClient.sendPayload(
                                        endpointId,
                                        Payload.fromBytes(CTRL_COMPLETE.toByteArray(Charsets.UTF_8))
                                    )
                                    val completedBatch = _activeBatch.value?.copy(
                                        bytesTransferred = _activeBatch.value?.totalBytes ?: 0L,
                                        status = TransferStatus.COMPLETED,
                                        speedBytesPerSec = 0L,
                                        etaSeconds = 0L
                                    )
                                    _activeBatch.value = completedBatch
                                    if (completedBatch != null) {
                                        scope.launch { _events.emit(NearbyEvent.TransferFinished(completedBatch)) }
                                    }
                                }
                            }
                        }
                        message == CTRL_PAUSE -> {
                            _activeBatch.value = _activeBatch.value?.copy(status = TransferStatus.PAUSED)
                        }
                        message == CTRL_RESUME -> {
                            _activeBatch.value = _activeBatch.value?.copy(status = TransferStatus.TRANSFERRING)
                        }
                        message == CTRL_CANCEL -> {
                            outgoingItemsAwaitingApproval.remove(endpointId)
                            _activeBatch.value = _activeBatch.value?.copy(status = TransferStatus.CANCELLED)
                        }
                        message == CTRL_COMPLETE -> {
                            val completedBatch = _activeBatch.value?.copy(
                                bytesTransferred = _activeBatch.value?.totalBytes ?: 0L,
                                status = TransferStatus.COMPLETED,
                                speedBytesPerSec = 0L,
                                etaSeconds = 0L
                            )
                            _activeBatch.value = completedBatch
                            if (completedBatch != null) {
                                scope.launch { _events.emit(NearbyEvent.TransferFinished(completedBatch)) }
                            }
                        }
                        message.startsWith("{") && message.contains("batchId") -> {
                            // Incoming Manifest Payload!
                            handleIncomingManifest(endpointId, message)
                        }
                    }
                }
                Payload.Type.FILE -> {
                    // Incoming file payload
                    val payloadFile = payload.asFile() ?: return
                    val manifest = pendingManifest
                    val nextItem = manifest?.items?.firstOrNull { item ->
                        incomingFilePayloads.none { it.value.second.name == item.name }
                    }

                    val downloadDir = StorageGuard.getOrbitDownloadDirectory()
                    val targetFile = File(downloadDir, nextItem?.name ?: "orbit_${System.currentTimeMillis()}.bin")

                    if (nextItem != null) {
                        incomingFilePayloads[payload.id] = Pair(targetFile, nextItem)
                    }
                }
                Payload.Type.STREAM -> {
                    // Stream payloads can also be received directly
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val batch = _activeBatch.value ?: return

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val now = System.currentTimeMillis()
                    val dt = (now - lastSpeedCalculationTime) / 1000.0
                    val transferred = update.bytesTransferred
                    val total = if (batch.totalBytes > 0) batch.totalBytes else update.totalBytes

                    if (dt >= 0.5 && transferred > lastBytesTransferred) {
                        val instantSpeed = ((transferred - lastBytesTransferred) / dt).toLong()
                        currentSpeedBps = if (currentSpeedBps == 0L) instantSpeed else (currentSpeedBps * 0.7 + instantSpeed * 0.3).toLong()
                        lastSpeedCalculationTime = now
                        lastBytesTransferred = transferred
                    }

                    val remainingBytes = (total - transferred).coerceAtLeast(0)
                    val eta = if (currentSpeedBps > 0) remainingBytes / currentSpeedBps else 0L

                    _activeBatch.value = batch.copy(
                        bytesTransferred = transferred,
                        totalBytes = total,
                        speedBytesPerSec = currentSpeedBps,
                        etaSeconds = eta,
                        status = TransferStatus.TRANSFERRING
                    )
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    // File transfer succeeded
                    val incomingInfo = incomingFilePayloads[update.payloadId]
                    if (incomingInfo != null) {
                        val (targetFile, item) = incomingInfo
                        // Move or finalize payload file
                    }

                    // Check if entire batch is complete
                    val total = batch.totalBytes
                    val updatedBatch = batch.copy(
                        bytesTransferred = total,
                        status = TransferStatus.COMPLETED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                    _activeBatch.value = updatedBatch
                    scope.launch {
                        _events.emit(NearbyEvent.TransferFinished(updatedBatch))
                    }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    _activeBatch.value = batch.copy(
                        status = TransferStatus.FAILED,
                        errorMessage = "Transfer failed during stream transmission."
                    )
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    _activeBatch.value = batch.copy(status = TransferStatus.CANCELLED)
                }
            }
        }
    }

    private fun handleIncomingManifest(endpointId: String, manifestJson: String) {
        try {
            val manifest = ManifestPayload.fromJsonString(manifestJson)
            pendingManifest = manifest

            // 1. Storage Pre-Flight Check!
            val downloadDir = StorageGuard.getOrbitDownloadDirectory()
            val storageCheck = StorageGuard.checkStorageAvailability(downloadDir, manifest.totalBytes)

            when (storageCheck) {
                is StorageCheckResult.Insufficient -> {
                    Log.e(TAG, "Storage check failed: ${storageCheck.formatErrorMessage()}")
                    val failedBatch = TransferBatch(
                        batchId = manifest.batchId,
                        targetDeviceName = manifest.senderDeviceName,
                        targetEndpointId = endpointId,
                        isOutgoing = false,
                        items = manifest.items.map {
                            TransferItem(
                                name = it.name,
                                sizeBytes = it.sizeBytes,
                                mimeType = it.mimeType,
                                textContent = it.textContent,
                                relativePath = it.relativePath
                            )
                        },
                        totalBytes = manifest.totalBytes,
                        status = TransferStatus.STORAGE_ERROR,
                        errorMessage = storageCheck.formatErrorMessage()
                    )
                    _activeBatch.value = failedBatch
                    cancelTransfer()
                    return
                }
                is StorageCheckResult.Sufficient -> {
                    Log.d(TAG, "Storage check passed! Free bytes: ${storageCheck.availableBytes}")
                }
            }

            // Create incoming batch
            val incomingItems = manifest.items.map {
                TransferItem(
                    name = it.name,
                    sizeBytes = it.sizeBytes,
                    mimeType = it.mimeType,
                    textContent = it.textContent,
                    relativePath = it.relativePath
                )
            }

                    val batch = TransferBatch(
                batchId = manifest.batchId,
                targetDeviceName = manifest.senderDeviceName,
                targetEndpointId = endpointId,
                isOutgoing = false,
                items = incomingItems,
                totalBytes = manifest.totalBytes,
                        status = TransferStatus.WAITING_CONFIRMATION
            )
                    scope.launch { _events.emit(NearbyEvent.IncomingTransferReady(batch)) }
            recentDevicesStore.recordDevice(manifest.senderDeviceName, endpointId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing manifest", e)
        }
    }
}

