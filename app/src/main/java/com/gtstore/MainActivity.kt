package com.gtstore

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var storeService: GTStoreService? = null
    private var isBound = false

    private lateinit var tvStatus: TextView
    private lateinit var tvServerAddress: TextView
    private lateinit var tvConnections: TextView
    private lateinit var tvSelectedFolder: TextView
    private lateinit var btnToggleServer: Button
    private lateinit var btnSelectFolder: Button
    private lateinit var btnRescanPkgs: Button

    private val FOLDER_PICKER_REQUEST = 1002

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUiStatus()
            handler.postDelayed(this, 2000)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GTStoreService.LocalBinder
            storeService = binder.getService()
            isBound = true
            updateUiStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            storeService = null
            isBound = false
            updateUiStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        loadSavedFolder()
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, GTStoreService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        handler.post(updateRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateRunnable)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvServerAddress = findViewById(R.id.tvServerAddress)
        tvConnections = findViewById(R.id.tvConnections)
        tvSelectedFolder = findViewById(R.id.tvSelectedFolder)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        btnRescanPkgs = findViewById(R.id.btnRescanPkgs)
    }

    private fun setupListeners() {
        btnSelectFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, FOLDER_PICKER_REQUEST)
        }

        btnToggleServer.setOnClickListener {
            val prefs = getSharedPreferences("ps4_rpi_prefs", Context.MODE_PRIVATE)
            val folderUri = prefs.getString("pkg_folder_uri", null)

            if (folderUri.isNullOrEmpty()) {
                Toast.makeText(this@MainActivity, "Selecione primeiro uma pasta com ficheiros PKG!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val status = storeService?.getStatus()
            if (status?.running == true) {
                GTStoreService.stopService(this@MainActivity)
            } else {
                GTStoreService.startService(this@MainActivity)
            }
            updateUiStatus()
        }

        btnRescanPkgs.setOnClickListener {
            if (storeService?.getStatus()?.running == true) {
                Toast.makeText(this@MainActivity, "A reindexar ficheiros PKG...", Toast.LENGTH_SHORT).show()
                GTStoreService.startService(this@MainActivity)
            } else {
                Toast.makeText(this@MainActivity, "Inicie o servidor para reindexar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FOLDER_PICKER_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val prefs = getSharedPreferences("ps4_rpi_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("pkg_folder_uri", uri.toString()).apply()

                tvSelectedFolder.text = "Pasta: ${uri.path}"
                Toast.makeText(this, "Pasta configurada com sucesso!", Toast.LENGTH_SHORT).show()

                storeService?.let { service ->
                    if (service.getStatus().running) {
                        GTStoreService.startService(this)
                    }
                }
            }
        }
    }

    private fun loadSavedFolder() {
        val prefs = getSharedPreferences("ps4_rpi_prefs", Context.MODE_PRIVATE)
        val folderUri = prefs.getString("pkg_folder_uri", null)

        if (!folderUri.isNullOrEmpty()) {
            val uri = Uri.parse(folderUri)
            tvSelectedFolder.text = "Pasta: ${uri.path}"
        } else {
            tvSelectedFolder.text = "Nenhuma pasta selecionada"
        }
    }

    private fun updateUiStatus() {
        val status = storeService?.getStatus()

        if (status != null && status.running) {
            tvStatus.text = "Estado: ATIVO"
            btnToggleServer.text = "Parar Servidor"

            val ip = status.localAddress ?: "Sem rede Wi-Fi"
            tvServerAddress.text = "Endereço: http://$ip:${status.port}"
            tvConnections.text = "Conexões ativas: ${status.activeConnections}"
        } else {
            tvStatus.text = "Estado: INATIVO"
            btnToggleServer.text = "Iniciar Servidor"
            tvServerAddress.text = "Endereço: --"
            tvConnections.text = "Conexões ativas: 0"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}
