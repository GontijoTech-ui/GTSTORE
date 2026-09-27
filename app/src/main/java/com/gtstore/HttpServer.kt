package com.gtstore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HttpServer(
    private val context: Context,
    private val port: Int = 8080
) {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_HEADER_SIZE = 64 * 1024
    }

    private var serverSocket: ServerSocket? = null

    @Volatile
    var running = false
        private set

    private val executor = Executors.newCachedThreadPool()

    private val activeConnections =
        AtomicInteger(0)

    val localAddress: String
        get() =
            getWifiIpv4Address()?.hostAddress
                ?: "0.0.0.0"

    data class ServerStatus(
        val running: Boolean,
        val port: Int,
        val localAddress: String,
        val url: String,
        val activeConnections: Int
    )

    data class PackageInfo(
        val id: Int,
        val name: String,
        val fileName: String,
        val size: Long,
        val modified: Long,
        val type: String,
        val uri: Uri
    )

    fun start() {

        if (running) {
            return
        }

        executor.execute {

            try {

                val address =
                    getWifiIpv4Address()

                serverSocket =
                    if (address != null) {
                        ServerSocket(
                            port,
                            50,
                            address
                        )
                    } else {
                        ServerSocket(port)
                    }

                running = true

                while (running) {

                    try {

                        val socket =
                            serverSocket?.accept()

                        if (socket != null) {

                            executor.execute {
                                handleClient(socket)
                            }
                        }

                    } catch (_: Exception) {

                        if (!running) {
                            break
                        }
                    }
                }

            } catch (_: Exception) {

                running = false

            } finally {

                try {
                    serverSocket?.close()
                } catch (_: Exception) {
                }

                serverSocket = null
                running = false
            }
        }
    }

    fun stop() {

        running = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
    }

    fun isRunning(): Boolean {
        return running
    }

    fun getStatus(): ServerStatus {

        val address =
            getWifiIpv4Address()?.hostAddress
                ?: "0.0.0.0"

        return ServerStatus(
            running = running,
            port = port,
            localAddress = address,
            url = "http://$address:$port",
            activeConnections =
                activeConnections.get()
        )
    }

    private fun handleClient(
        socket: Socket
    ) {

        activeConnections.incrementAndGet()

        socket.use { client ->

            try {

                client.soTimeout = 30_000

                val input =
                    BufferedReader(
                        InputStreamReader(
                            client.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    )

                val output =
                    client.getOutputStream()

                val requestLine =
                    input.readLine()
                        ?: return

                if (
                    requestLine.length >
                    MAX_HEADER_SIZE
                ) {

                    sendError(
                        output,
                        431,
                        "Request Header Fields Too Large"
                    )

                    return
                }

                val parts =
                    requestLine.split(" ")

                if (parts.size < 2) {

                    sendError(
                        output,
                        400,
                        "Bad Request"
                    )

                    return
                }

                val method =
                    parts[0].uppercase()

                val target =
                    parts[1]

                val headers =
                    HashMap<String, String>()

                while (true) {

                    val line =
                        input.readLine()
                            ?: break

                    if (line.isEmpty()) {
                        break
                    }

                    val separator =
                        line.indexOf(':')

                    if (separator > 0) {

                        val name =
                            line.substring(
                                0,
                                separator
                            )
                                .trim()
                                .lowercase()

                        val value =
                            line.substring(
                                separator + 1
                            )
                                .trim()

                        headers[name] =
                            value
                    }
                }

                when (method) {

                    "GET" -> {

                        handleRequest(
                            target = target,
                            headers = headers,
                            output = output,
                            headOnly = false
                        )
                    }

                    "HEAD" -> {

                        handleRequest(
                            target = target,
                            headers = headers,
                            output = output,
                            headOnly = true
                        )
                    }

                    "OPTIONS" -> {

                        sendOptions(
                            output
                        )
                    }

                    else -> {

                        sendError(
                            output,
                            405,
                            "Method Not Allowed",
                            mapOf(
                                "Allow" to
                                    "GET, HEAD, OPTIONS"
                            )
                        )
                    }
                }

            } catch (_: Exception) {

                try {

                    sendError(
                        client.getOutputStream(),
                        500,
                        "Internal Server Error"
                    )

                } catch (_: Exception) {
                }

            } finally {

                activeConnections.decrementAndGet()
            }
        }
    }

    private fun handleRequest(
        target: String,
        headers: Map<String, String>,
        output: OutputStream,
        headOnly: Boolean
    ) {

        val uri =
            Uri.parse(target)

        val path =
            uri.path ?: "/"

        when {

            path == "/" -> {

                sendHomePage(
                    output,
                    headOnly
                )
            }

            path == "/ps4" -> {

                sendHomePage(
                    output,
                    headOnly
                )
            }

            path == "/api/status" -> {

                sendStatusJson(
                    output,
                    headOnly
                )
            }

            path == "/api/packages" -> {

                sendPackagesJson(
                    output,
                    headOnly
                )
            }

            path == "/download" -> {

                val id =
                    uri.getQueryParameter("id")
                        ?.toIntOrNull()

                if (id == null) {

                    sendError(
                        output,
                        400,
                        "Invalid package id"
                    )

                    return
                }

                servePackage(
                    packageId = id,
                    headers = headers,
                    output = output,
                    headOnly = headOnly
                )
            }

            path == "/pkg" -> {

                val id =
                    uri.getQueryParameter("id")
                        ?.toIntOrNull()

                if (
