package com.samielmadani.orbit.model

data class TransferBatch(
    val batchId: String,
    val targetDeviceName: String,
    val targetEndpointId: String,
    val isOutgoing: Boolean,
    val items: List<TransferItem>,
    val totalBytes: Long,
    val bytesTransferred: Long = 0L,
    val status: TransferStatus = TransferStatus.CONNECTING,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val currentFileName: String = items.firstOrNull()?.name ?: "",
    val currentItemIndex: Int = 0,
    val errorMessage: String? = null,
    val startTimeMs: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    fun formattedSpeed(): String {
        if (speedBytesPerSec <= 0) return "0 MB/s"
        val mbPerSec = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) {
            String.format("%.1f MB/s", mbPerSec)
        } else {
            val kbPerSec = speedBytesPerSec.toDouble() / 1024.0
            String.format("%.0f KB/s", kbPerSec)
        }
    }

    fun formattedEta(): String {
        if (etaSeconds <= 0 || speedBytesPerSec <= 0) return "Estimating…"
        if (etaSeconds < 60) return "${etaSeconds}s"
        val minutes = etaSeconds / 60
        val seconds = etaSeconds % 60
        return if (minutes < 60) {
            "${minutes}m ${seconds}s"
        } else {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            "${hours}h ${remMinutes}m"
        }
    }

    fun formattedTransferred(): String {
        return "${formatBytes(bytesTransferred)} / ${formatBytes(totalBytes)}"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
    }
}

