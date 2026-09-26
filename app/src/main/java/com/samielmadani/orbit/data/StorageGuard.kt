package com.samielmadani.orbit.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

sealed class StorageCheckResult {
    data class Sufficient(val availableBytes: Long) : StorageCheckResult()
    data class Insufficient(
        val availableBytes: Long,
        val requiredBytes: Long,
        val missingBytes: Long
    ) : StorageCheckResult() {
        fun formatErrorMessage(): String {
            val missingMb = missingBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)
            val requiredMb = requiredBytes / (1024 * 1024)
            return "Not enough device storage. Requires ${requiredMb} MB, but only ${availableMb} MB is free (${missingMb} MB missing)."
        }
    }
}

object StorageGuard {
    private const val SAFETY_BUFFER_BYTES = 100L * 1024L * 1024L // 100 MB buffer

    fun getOrbitDownloadDirectory(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val orbitDir = File(downloads, "Orbit")
        if (!orbitDir.exists()) {
            orbitDir.mkdirs()
        }
        return orbitDir
    }

    fun checkStorageAvailability(targetDirectory: File, requiredBytes: Long): StorageCheckResult {
        return try {
            val stat = StatFs(targetDirectory.absolutePath)
            val availableBytes = stat.availableBytes
            val totalNeeded = requiredBytes + SAFETY_BUFFER_BYTES

            if (availableBytes >= totalNeeded) {
                StorageCheckResult.Sufficient(availableBytes)
            } else {
                StorageCheckResult.Insufficient(
                    availableBytes = availableBytes,
                    requiredBytes = requiredBytes,
                    missingBytes = totalNeeded - availableBytes
                )
            }
        } catch (e: Exception) {
            // If stat fails, check targetDirectory.usableSpace
            val usable = targetDirectory.usableSpace
            if (usable >= (requiredBytes + SAFETY_BUFFER_BYTES)) {
                StorageCheckResult.Sufficient(usable)
            } else {
                StorageCheckResult.Insufficient(
                    availableBytes = usable,
                    requiredBytes = requiredBytes,
                    missingBytes = (requiredBytes + SAFETY_BUFFER_BYTES) - usable
                )
            }
        }
    }
}

