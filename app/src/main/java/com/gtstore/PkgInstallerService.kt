package com.teu.pacote.gtstore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class PkgInstallerService : Service() {
    private var server: Ps4PkgServer? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("GTSTORE_CHANNEL", "Instalação PS4", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, "GTSTORE_CHANNEL")
            .setContentTitle("GTStore")
            .setContentText("A enviar ficheiros para o PS4...")
            .setSmallIcon(android.R.drawable.stat_sys_upload) 
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server == null) {
            try {
                // Caminho enviado pela UI da app
                val pkgFilePath = intent?.getStringExtra("PKG_PATH") ?: return START_NOT_STICKY
                val selectedFile = File(pkgFilePath)
                
                // O servidor usa a diretoria do PKG
                val pkgDir = selectedFile.parentFile ?: getExternalFilesDir(null)!!
                val payloadBytes = assets.open("payload").readBytes()
                
                server = Ps4PkgServer(pkgDir, payloadBytes, 8080) { msg ->
                    println("GTSTORE_LOG: $msg") // Logs de diagnóstico
                }
                server?.start()
                
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
