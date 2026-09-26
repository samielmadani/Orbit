package com.samielmadani.orbit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OrbitApplication : Application() {

    companion object {
        const val TRANSFER_CHANNEL_ID = "orbit_transfer_channel"
        const val DISCOVERY_CHANNEL_ID = "orbit_discovery_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val transferChannel = NotificationChannel(
                TRANSFER_CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW // Low importance so it doesn't sound continuously during progress updates
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }

            val discoveryChannel = NotificationChannel(
                DISCOVERY_CHANNEL_ID,
                "Orbit Connection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming file transfer requests and connection alerts"
                setShowBadge(true)
            }

            notificationManager?.createNotificationChannels(listOf(transferChannel, discoveryChannel))
        }
    }
}
