package com.gtstore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GTStoreService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): GTStoreService = this@GTStoreService
    }

    private val binder = LocalBinder()
    private var httpServer: HttpServer? = null

    override fun onCreate() {
        super.onCreate()
        httpServer = HttpServer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
            else -> {
                if (httpServer?.isRunning != true) {
                    startServer()
                }
            }
        }
        return START_STICKY
    }

    fun startServer() {
        if (httpServer?.isRunning == true) return

        createNotificationChannel()
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        httpServer?.start()
    }

    fun stopServer() {
        if (httpServer?.isRunning != true) return

        httpServer?.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    fun getStatus(): HttpServer.ServerStatus {
        return httpServer?.getStatus() ?: HttpServer.ServerStatus(
            running = false,
            isRunning = false,
            port = 8080,
            localAddress = null,
            activeConnections = 0
        )
    }

    override fun onDestroy() {
        httpServer?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GTStore PS4 Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servidor de pacotes PKG para PS4 rodando em segundo plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingLaunchIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GTStoreService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val localIp = httpServer?.getWifiIpv4Address() ?: "IP Indisponível"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GTStore Server Ativo")
            .setContentText("Servidor rodando em http://$localIp:8080")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(pendingLaunchIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Parar Servidor",
                pendingStopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "gtstore_server_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.gtstore.action.START"
        const val ACTION_STOP = "com.gtstore.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, GTStoreService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GTStoreService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
