package com.gtstore

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
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

    private val running =
        AtomicBoolean(false)

    private val activeConnections =
        AtomicInteger(0)

    fun start(): Boolean {

        if (running.get()) {
            return true
        }

        return try {

            val socket =
                ServerSocket(port)

            serverSocket = socket

            running.set(true)

            serverThread =
                Thread {

                    while (running.get()) {

                        try {

                            val client =
                                socket.accept()

                            activeConnections.incrementAndGet()

                            Thread {

                                handleClient(client)

                            }.start()

                        } catch (_: Exception) {

                            if (running.get()) {
                                // Erro durante accept.
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
            activeConnections =
                activeConnections.get()
        )
    }

    private fun handleClient(
        socket: Socket
    ) {

        try {

            socket.use {

                socket.soTimeout = 10000

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    )

                val writer =
                    PrintWriter(
                        socket.getOutputStream(),
                        true
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
                    parts.getOrNull(1)
                        ?: "/"

                when {

                    method != "GET" -> {

                        sendResponse(
                            writer = writer,
                            status = "405 Method Not Allowed",
                            contentType =
                                "text/plain; charset=utf-8",
                            body =
                                "Método não permitido."
                        )
                    }

                    path == "/" -> {

                        sendResponse(
                            writer = writer,
                            status = "200 OK",
                            contentType =
                                "text/html; charset=utf-8",
                            body =
                                buildHomePage()
                        )
                    }

                    path == "/api/status" -> {

                        sendResponse(
                            writer = writer,
                            status = "200 OK",
                            contentType =
                                "application/json; charset=utf-8",
                            body =
                                buildStatusJson()
                        )
                    }

                    path == "/favicon.ico" -> {

                        sendResponse(
                            writer = writer,
                            status = "204 No Content",
                            contentType =
                                "text/plain",
                            body = ""
                        )
                    }

                    else -> {

                        sendResponse(
                            writer = writer,
                            status = "404 Not Found",
                            contentType =
                                "text/plain; charset=utf-8",
                            body =
                                "GTSTORE: página não encontrada."
                        )
                    }
                }
            }

        } catch (_: Exception) {

        } finally {

            activeConnections.decrementAndGet()

            if (
                activeConnections.get() < 0
            ) {
                activeConnections.set(0)
            }
        }
    }

    private fun sendResponse(
        writer: PrintWriter,
        status: String,
        contentType: String,
        body: String
    ) {

        val bodyBytes =
            body.toByteArray(
                StandardCharsets.UTF_8
            )

        writer.print(
            "HTTP/1.1 $status\r\n"
        )

        writer.print(
            "Content-Type: $contentType\r\n"
        )

        writer.print(
            "Content-Length: ${bodyBytes.size}\r\n"
        )

        writer.print(
            "Connection: close\r\n"
        )

        writer.print(
            "Cache-Control: no-store\r\n"
        )

        writer.print(
            "\r\n"
        )

        writer.print(body)

        writer.flush()
    }

    private fun buildHomePage(): String {

        val ip =
            getLocalIpAddress()

        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport"
                    content="width=device-width, initial-scale=1.0">
                <title>GTSTORE</title>
            </head>
            <body>
                <h1>GTSTORE</h1>

                <p>Servidor HTTP funcionando.</p>

                <p>
                    Endereço:
                    http://$ip:$port
                </p>

                <p>
                    <a href="/api/status">
                        Ver status da API
                    </a>
                </p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildStatusJson(): String {

        val connections =
            activeConnections.get()

        return """
            {
                "server": "GTSTORE",
                "running": ${running.get()},
                "port": $port,
                "local_address": "${getLocalIpAddress()}",
                "active_connections": $connections
            }
        """.trimIndent()
    }

    private fun getLocalIpAddress(): String {

        return try {

            val interfaces =
                Collections.list(
                    NetworkInterface.getNetworkInterfaces()
                )

            for (networkInterface in interfaces) {

                if (
                    !networkInterface.isUp ||
                    networkInterface.isLoopback
                ) {
                    continue
                }

                val addresses =
                    Collections.list(
                        networkInterface.inetAddresses
                    )

                for (address in addresses) {

                    if (
                        address is Inet4Address &&
                        !address.isLoopbackAddress
                    ) {

                        return address.hostAddress
                            ?: "0.0.0.0"
                    }
                }
            }

            "0.0.0.0"

        } catch (_: SocketException) {

            "0.0.0.0"
        }
    }
}
