package com.vanta.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vanta.R
import com.vanta.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service to keep Vanta running when the screen is off.
 * 
 * Required for:
 * - Continuous camera/microphone access
 * - Reliable WebSocket connection
 * - Battery optimization bypass
 */
@AndroidEntryPoint
class VantaService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vanta_service_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vanta Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Vanta is running in the background"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vanta Active")
            .setContentText("Tap to open")
            .setSmallIcon(R.drawable.ic_vanta_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
