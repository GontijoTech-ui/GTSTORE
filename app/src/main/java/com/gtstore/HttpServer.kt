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
                    parts.getOrNull(1)
                        ?.substringBefore("?")
                        ?: "/"

                when {

                    method != "GET" -> {

                        sendResponse(
                            socket.outputStream,
                            "405 Method Not Allowed",
                            "text/plain; charset=utf-8",
                            "Método não permitido."
                        )
                    }

                    path == "/" -> {

                        sendResponse(
                            socket.outputStream,
                            "200 OK",
                            "text/html; charset=utf-8",
                            buildHomePage()
                        )
                    }

                    path == "/api/status" -> {

                        sendResponse(
                            socket.outputStream,
                            "200 OK",
                            "application/json; charset=utf-8",
                            buildStatusJson()
                        )
                    }

                    path == "/favicon.ico" -> {

                        sendResponse(
                            socket.outputStream,
                            "204 No Content",
                            "text/plain; charset=utf-8",
                            ""
                        )
                    }

                    else -> {

                        sendResponse(
                            socket.outputStream,
                            "404 Not Found",
                            "text/plain; charset=utf-8",
                            "GTSTORE: página não encontrada."
                        )
                    }
                }
            }

        } catch (_: Exception) {

            // Cliente encerrou a conexão.

        } finally {

            activeConnections.decrementAndGet()

            if (activeConnections.get() < 0) {
                activeConnections.set(0)
            }
        }
    }

    private fun sendResponse(
        output: OutputStream,
        status: String,
        contentType: String,
        body: String
    ) {

        val bodyBytes =
            body.toByteArray(
                StandardCharsets.UTF_8
            )

        val headers =
            buildString {

                append("HTTP/1.1 ")
                append(status)
                append("\r\n")

                append("Content-Type: ")
                append(contentType)
                append("\r\n")

                append("Content-Length: ")
                append(bodyBytes.size)
                append("\r\n")

                append("Connection: close\r\n")

                append("Cache-Control: no-store\r\n")

                append("Access-Control-Allow-Origin: *\r\n")

                append("\r\n")
            }

        output.write(
            headers.toByteArray(
                StandardCharsets.UTF_8
            )
        )

        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }

        output.flush()
    }

    private fun buildHomePage(): String {

        val ip =
            getLocalIpAddress()

        return """
            <!DOCTYPE html>
            <html lang="pt-BR">

            <head>
                <meta charset="UTF-8">
                <meta
                    name="viewport"
                    content="width=device-width, initial-scale=1.0"
                >
                <title>GTSTORE</title>
            </head>

            <body>

                <h1>GTSTORE</h1>

                <p>
                    Servidor HTTP funcionando.
                </p>

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

        return """
            {
                "server": "GTSTORE",
                "running": ${running.get()},
                "port": $port,
                "local_address": "${getLocalIpAddress()}",
                "active_connections": ${activeConnections.get()}
            }
        """.trimIndent()
    }

    private fun getLocalIpAddress(): String {

        return try {

            val interfaces =
                Collections.list(
                    NetworkInterface.getNetworkInterfaces()
                )

            /*
             * Primeiro procuramos uma interface que tenha
             * endereço IPv4 privado típico de uma rede local.
             *
             * Exemplos:
             * 192.168.x.x
             * 10.x.x.x
             * 172.16.x.x até 172.31.x.x
             */

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
                        !address.isLoopbackAddress &&
                        isPrivateIpv4(address)
                    ) {

                        return address.hostAddress
                            ?: continue
                    }
                }
            }

            /*
             * Fallback caso a interface Wi-Fi não seja
             * identificada como endereço privado.
             */

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
                            ?: continue
                    }
                }
            }

            "127.0.0.1"

        } catch (_: SocketException) {

            "127.0.0.1"
        }
    }

    private fun isPrivateIpv4(
        address: Inet4Address
    ): Boolean {

        val bytes =
            address.address

        val first =
            bytes[0].toInt() and 0xFF

        val second =
            bytes[1].toInt() and 0xFF

        return when {

            first == 10 -> {
                true
            }

            first == 172 &&
                second in 16..31 -> {
                true
            }

            first == 192 &&
                second == 168 -> {
                true
            }

            else -> {
                false
            }
        }
    }
}
