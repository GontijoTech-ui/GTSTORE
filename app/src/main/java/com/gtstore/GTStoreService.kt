package com.gtstore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class GTStoreService : Service() {

    companion object {

        const val CHANNEL_ID =
            "GTSTORE_SERVER_CHANNEL"

        const val NOTIFICATION_ID =
            1001

        const val ACTION_START =
            "com.gtstore.action.START_SERVER"

        const val ACTION_STOP =
            "com.gtstore.action.STOP_SERVER"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )

                stopSelf()

                return START_NOT_STICKY
            }

            ACTION_START,
            null -> {

                val notification =
                    buildNotification()

                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }
        }

        /*
         * Se o Android encerrar o processo do serviço,
         * solicita que ele seja recriado posteriormente.
         */
        return START_STICKY
    }

    private fun buildNotification(): Notification {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "GTSTORE"
                )
                .setContentText(
                    "Servidor em segundo plano ativo"
                )
                .setSmallIcon(
                    android.R.drawable.stat_sys_upload
                )
                .setOngoing(true)
                .setCategory(
                    Notification.CATEGORY_SERVICE
                )
                .build()

        } else {

            Notification.Builder(this)
                .setContentTitle(
                    "GTSTORE"
                )
                .setContentText(
                    "Servidor em segundo plano ativo"
                )
                .setSmallIcon(
                    android.R.drawable.stat_sys_upload
                )
                .setOngoing(true)
                .setCategory(
                    Notification.CATEGORY_SERVICE
                )
                .build()
        }
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Servidor GTSTORE",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Notificação do servidor GTSTORE em segundo plano"

            channel.setShowBadge(false)

            val notificationManager =
                getSystemService(
                    NotificationManager::class.java
                )

            notificationManager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
