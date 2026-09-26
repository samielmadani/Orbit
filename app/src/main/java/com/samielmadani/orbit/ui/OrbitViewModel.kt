package com.samielmadani.orbit.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samielmadani.orbit.data.NearbyEvent
import com.samielmadani.orbit.data.NearbyManager
import com.samielmadani.orbit.data.RecentDevice
import com.samielmadani.orbit.model.DiscoveredDevice
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.model.TransferItem
import com.samielmadani.orbit.model.TransferStatus
import com.samielmadani.orbit.service.OrbitTransferService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrbitViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OrbitPermissions"
    }

    private val context: Context get() = getApplication<Application>().applicationContext
    val nearbyManager = NearbyManager(context)

    val localDeviceName: String = nearbyManager.localDeviceName
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = nearbyManager.discoveredDevices
    val activeBatch: StateFlow<TransferBatch?> = nearbyManager.activeBatch
    val isScanning: StateFlow<Boolean> = nearbyManager.isScanning
    val isAdvertising: StateFlow<Boolean> = nearbyManager.isAdvertising

    private val _recentDevices = MutableStateFlow<List<RecentDevice>>(emptyList())
    val recentDevices: StateFlow<List<RecentDevice>> = _recentDevices.asStateFlow()

    private val _incomingRequest = MutableStateFlow<TransferBatch?>(null)
    val incomingRequest: StateFlow<TransferBatch?> = _incomingRequest.asStateFlow()

    private val _showPermissionSheet = MutableStateFlow(false)
    val showPermissionSheet: StateFlow<Boolean> = _showPermissionSheet.asStateFlow()

    private val _showSendTextDialog = MutableStateFlow(false)
    val showSendTextDialog: StateFlow<Boolean> = _showSendTextDialog.asStateFlow()

    // Staged items awaiting recipient selection (or vice versa)
    private var stagedItemsToSend = mutableListOf<TransferItem>()
    private var selectedTargetDevice: DiscoveredDevice? = null

    init {
        loadRecents()
        observeNearbyEvents()
    }

    private fun loadRecents() {
        _recentDevices.value = nearbyManager.recentDevicesStore.getRecentDevices()
    }

    private fun observeNearbyEvents() {
        viewModelScope.launch {
            nearbyManager.events.collect { event ->
                when (event) {
                    is NearbyEvent.IncomingRequest -> {
                        Unit
                    }
                    is NearbyEvent.IncomingTransferReady -> {
                        _incomingRequest.value = event.batch
                        OrbitTransferService.postIncomingNotification(context, event.batch)
                    }
                    is NearbyEvent.ConnectionAccepted -> {
                        // If we have staged items and this endpoint matches, dispatch
                        if (stagedItemsToSend.isNotEmpty()) {
                            nearbyManager.sendItemsToEndpoint(
                                endpointId = event.endpointId,
                                targetDeviceName = event.deviceName,
                                items = stagedItemsToSend.toList()
                            )
                            stagedItemsToSend.clear()
                            OrbitTransferService.startService(context)
                        }
                    }
                    is NearbyEvent.TransferFinished -> {
                        loadRecents()
                        OrbitTransferService.stopService(context)
                    }
                    else -> Unit
                }
            }
        }

        // Keep foreground service notification in sync with active batch
        viewModelScope.launch {
            activeBatch.collect { batch ->
                if (batch != null && batch.status !in setOf(
                        TransferStatus.COMPLETED,
                        TransferStatus.FAILED,
                        TransferStatus.CANCELLED,
                        TransferStatus.STORAGE_ERROR
                    )) {
                    OrbitTransferService.updateTransferNotification(context, batch)
                }
            }
        }
    }

    fun hasRequiredPermissions(): Boolean {
        val missingPermissions = getRequiredPermissionsList().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            Log.i(TAG, "[$localDeviceName] All required runtime permissions are granted")
        } else {
            Log.w(TAG, "[$localDeviceName] Missing runtime permissions: ${missingPermissions.joinToString()}")
        }
        return missingPermissions.isEmpty()
    }

    fun getRequiredPermissionsList(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return list
    }

    fun requestStartScanning() {
        if (hasRequiredPermissions()) {
            nearbyManager.startNearby()
        } else {
            _showPermissionSheet.value = true
        }
    }

    fun onPermissionsResult(permissionResults: Map<String, Boolean>) {
        _showPermissionSheet.value = false
        Log.i(TAG, "[$localDeviceName] Permission request result: ${permissionResults.entries.joinToString { "${it.key}=${it.value}" }}")
        if (hasRequiredPermissions()) {
            Log.i(TAG, "[$localDeviceName] Permission gate passed; starting Nearby discovery and advertising")
            nearbyManager.startNearby()
        } else {
            Log.w(TAG, "[$localDeviceName] Permission gate failed; Nearby discovery and advertising not started")
        }
    }

    fun dismissPermissionSheet() {
        _showPermissionSheet.value = false
    }

    fun openSendTextDialog() {
        _showSendTextDialog.value = true
    }

    fun closeSendTextDialog() {
        _showSendTextDialog.value = false
    }

    // -------------------------------------------------------------
    // Device Selection & Sending
    // -------------------------------------------------------------

    fun onDeviceSelected(device: DiscoveredDevice) {
        selectedTargetDevice = device
        if (stagedItemsToSend.isNotEmpty()) {
            // Already picked files, send immediately!
            sendStagedToDevice(device)
        }
        // If no files yet, caller UI will trigger file picker
    }

    fun onRecentDeviceSelected(recent: RecentDevice) {
        // Find if device is currently discovered in orbit
        val liveDevice = discoveredDevices.value.firstOrNull { it.deviceName.equals(recent.name, ignoreCase = true) }
        if (liveDevice != null) {
            onDeviceSelected(liveDevice)
        } else {
            // Device not in range currently, initiate search
            requestStartScanning()
        }
    }

    fun stageUrisForSending(uris: List<Uri>, targetDevice: DiscoveredDevice? = null) {
        val items = uris.mapNotNull { uriToTransferItem(it) }
        if (items.isEmpty()) return

        val target = targetDevice ?: selectedTargetDevice
        if (target != null) {
            stagedItemsToSend = items.toMutableList()
            sendStagedToDevice(target)
        } else {
            stagedItemsToSend = items.toMutableList()
        }
    }

    fun sendTextContent(text: String, targetDevice: DiscoveredDevice? = null) {
        closeSendTextDialog()
        val textItem = TransferItem(
            name = "Note (${text.take(16)}…)",
            sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
            mimeType = "text/plain",
            textContent = text
        )

        val target = targetDevice ?: selectedTargetDevice ?: discoveredDevices.value.firstOrNull()
        if (target != null) {
            stagedItemsToSend = mutableListOf(textItem)
            sendStagedToDevice(target)
        } else {
            stagedItemsToSend = mutableListOf(textItem)
        }
    }

    private fun sendStagedToDevice(device: DiscoveredDevice) {
        nearbyManager.initiateConnection(device)
    }

    private fun uriToTransferItem(uri: Uri): TransferItem? {
        val resolver = context.contentResolver
        var name = "orbit_file"
        var size: Long = 0L
        val mimeType = resolver.getType(uri) ?: "*/*"

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) name = cursor.getString(nameIdx)
                if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
            }
        }

        return TransferItem(
            name = name,
            sizeBytes = size,
            mimeType = mimeType,
            uriString = uri.toString(),
            relativePath = name
        )
    }

    // -------------------------------------------------------------
    // Controls: Pause, Resume, Cancel
    // -------------------------------------------------------------

    fun pauseTransfer() {
        nearbyManager.pauseTransfer()
    }

    fun resumeTransfer() {
        nearbyManager.resumeTransfer()
    }

    fun cancelTransfer() {
        nearbyManager.cancelTransfer()
        OrbitTransferService.stopService(context)
    }

    fun acceptIncoming() {
        _incomingRequest.value?.let { batch ->
            nearbyManager.acceptIncomingTransfer(batch)
            _incomingRequest.value = null
            OrbitTransferService.cancelIncomingNotification(context)
            OrbitTransferService.startService(context)
        }
    }

    fun rejectIncoming() {
        _incomingRequest.value?.let { batch ->
            nearbyManager.declineIncomingTransfer(batch)
            _incomingRequest.value = null
            OrbitTransferService.cancelIncomingNotification(context)
        }
    }

    fun dismissCompletion() {
        nearbyManager.disconnectAll()
    }

    override fun onCleared() {
        super.onCleared()
        nearbyManager.stopNearby()
    }
}

