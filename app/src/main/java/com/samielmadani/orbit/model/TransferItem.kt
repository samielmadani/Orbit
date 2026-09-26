package com.samielmadani.orbit.model

import java.util.UUID

data class TransferItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sizeBytes: Long,
    val mimeType: String = "*/*",
    val uriString: String? = null,
    val textContent: String? = null,
    val isFolder: Boolean = false,
    val relativePath: String = name
) {
    val isText: Boolean get() = textContent != null
    
    fun formattedSize(): String {
        if (isText) return "${textContent?.length ?: 0} chars"
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = sizeBytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
    }
}
