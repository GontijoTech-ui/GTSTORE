package com.gtstore

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ServerStatus(
    val running: Boolean,
    val port: Int,
    val localAddress: String,
    val activeConnections: Int
)

class HttpServer(
    private val port: Int = 8080
) {

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    private val running = AtomicBoolean(false)

    private val activeConnections = AtomicInteger(0)

    fun start(): Boolean {

        if (running.get()) {
            return true
        }

        return try {

            val socket = ServerSocket(
                port,
                50,
                InetAddress.getByName("0.0.0.0")
            )

            socket.reuseAddress = true

            serverSocket = socket

            running.set(true)

            serverThread = Thread {

                while (running.get()) {

                    try {

                        val client = socket.accept()

                        activeConnections.incrementAndGet()

                        Thread {

                            handleClient(client)

                        }.apply {

                            name = "GTSTORE-HTTP-CLIENT"

                            start()
                        }

                    } catch (_: Exception) {

                        if (running.get()) {
                            // Falha durante accept.
                        }
                    }
                }

            }.apply {

                name = "GTSTORE-HTTP-SERVER"

                start()
            }

            true

        } catch (_: Exception) {

            running.set(false)

            serverSocket = null

            false
        }
    }

    fun stop() {

        running.set(false)

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null

        try {
            serverThread?.interrupt()
        } catch (_: Exception) {
        }

        serverThread = null

        activeConnections.set(0)
    }

    fun isRunning(): Boolean {
        return running.get()
    }

    fun getStatus(): ServerStatus {

        return ServerStatus(
            running = running.get(),
            port = port,
            localAddress = getLocalIpAddress(),
            activeConnections = activeConnections.get()
        )
    }

    private fun handleClient(
        socket: Socket
    ) {

        try {

            socket.use {

                socket.soTimeout = 15000

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    )

                val requestLine =
                    reader.readLine()
                        ?: return@use

                while (true) {

                    val line =
                        reader.readLine()
                            ?: break

                    if (line.isEmpty()) {
                        break
                    }
                }

                val parts =
                    requestLine.split(" ")

                val method =
                    parts.getOrNull(0)
                        ?: ""

                val path =
