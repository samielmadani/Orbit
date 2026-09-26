package com.samielmadani.orbit.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.samielmadani.orbit.MainActivity
import com.samielmadani.orbit.OrbitApplication
import com.samielmadani.orbit.R
import com.samielmadani.orbit.model.TransferBatch
import com.samielmadani.orbit.model.TransferStatus

class OrbitTransferService : Service() {

    companion object {
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "ACTION_START_TRANSFER"
        const val ACTION_UPDATE_PROGRESS = "ACTION_UPDATE_PROGRESS"
        const val ACTION_PAUSE = "ACTION_PAUSE_TRANSFER"
        const val ACTION_RESUME = "ACTION_RESUME_TRANSFER"
        const val ACTION_CANCEL = "ACTION_CANCEL_TRANSFER"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"

        // Broadcast actions sent back to ViewModel/Activity
        const val BROADCAST_TRANSFER_ACTION = "com.samielmadani.orbit.TRANSFER_ACTION"
        const val EXTRA_ACTION_TYPE = "extra_action_type"

        fun startService(context: Context) {
            val intent = Intent(context, OrbitTransferService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OrbitTransferService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orbit:TransferWakeLock").apply {
            this?.setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                wakeLock?.acquire(3600_000L) // Safe 1-hour max lock
                startForegroundWithNotification("Orbit Transfer", "Transfer active", 0, "0 MB/s", "Estimating…", false)
            }
            ACTION_PAUSE -> {
                sendActionBroadcast(ACTION_PAUSE)
            }
            ACTION_RESUME -> {
                sendActionBroadcast(ACTION_RESUME)
            }
            ACTION_CANCEL -> {
                sendActionBroadcast(ACTION_CANCEL)
                stopForegroundService()
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun sendActionBroadcast(actionType: String) {
        val intent = Intent(BROADCAST_TRANSFER_ACTION).apply {
            putExtra(EXTRA_ACTION_TYPE, actionType)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    fun updateProgress(batch: TransferBatch) {
        val title = if (batch.isOutgoing) "Sending to ${batch.targetDeviceName}" else "Receiving from ${batch.targetDeviceName}"
        val isPaused = batch.status == TransferStatus.PAUSED
        val subtitle = if (isPaused) "Paused (${batch.progressPercent}%)" else "${batch.progressPercent}% · ${batch.formattedSpeed()} · ETA ${batch.formattedEta()}"

        val notification = buildTransferNotification(
            title = title,
            content = subtitle,
            progress = batch.progressPercent,
            isPaused = isPaused
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundWithNotification(
        title: String,
        content: String,
        progress: Int,
        speed: String,
        eta: String,
        isPaused: Boolean
    ) {
        val notification = buildTransferNotification(title, content, progress, isPaused)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun buildTransferNotification(
        title: String,
        content: String,
        progress: Int,
        isPaused: Boolean
    ): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause / Resume Intent
        val pauseActionIntent = Intent(this, OrbitTransferService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel Intent
        val cancelActionIntent = Intent(this, OrbitTransferService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            2,
            cancelActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseTitle = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, OrbitApplication.TRANSFER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseTitle, pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopForegroundService() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
