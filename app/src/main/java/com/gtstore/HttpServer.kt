package com.gtstore

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ServerStatus(
    val running: Boolean,
    val port: Int,
    val localAddress: String,
    val activeConnections: Int
)

class HttpServer(
    private val context: Context,
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

        val wifiAddress =
            getWifiIpv4Address()

        if (wifiAddress == null) {
            return false
        }

        return try {

            /*
             * O servidor fica vinculado exclusivamente
             * ao endereço IPv4 da rede Wi-Fi.
             */
            val socket =
                ServerSocket(
                    port,
                    50,
                    wifiAddress
                )

            socket.reuseAddress = true

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

                            }.apply {

                                name =
                                    "GTSTORE-HTTP-CLIENT"

                                start()
                            }

                        } catch (_: Exception) {

                            if (running.get()) {
                                // Erro durante accept.
                            }
                        }
                    }

                }.apply {

                    name =
                        "GTSTORE-HTTP-SERVER"

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
            localAddress =
                getWifiIpv4Address()
                    ?.hostAddress
                    ?: "SEM WI-FI",
            activeConnections =
                activeConnections.get()
        )
    }

    private fun getWifiIpv4Address(): Inet4Address? {

        return try {

            val connectivityManager =
                context.getSystemService(
                    Context.CONNECTIVITY_SERVICE
                ) as ConnectivityManager

            val network =
                connectivityManager.activeNetwork
                    ?: return null

            val capabilities =
                connectivityManager.getNetworkCapabilities(
                    network
                )
                    ?: return null

            if (
                !capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                )
            ) {
                return null
            }

            val linkProperties =
                connectivityManager.getLinkProperties(
                    network
                )
                    ?: return null

            for (
                linkAddress
                in linkProperties.linkAddresses
            ) {

                val address =
                    linkAddress.address

                if (
                    address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    isPrivateIpv4(address)
                ) {

                    return address
                }
            }

            null

        } catch (_: Exception) {

            null
        }
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

                val rawTarget =
                    parts.getOrNull(1)
                        ?: "/"

                val path =
                    rawTarget
                        .substringBefore("?")

                val query =
                    rawTarget
                        .substringAfter(
                            "?",
                            ""
                        )

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

                    path == "/ps4" -> {

                        sendResponse(
                            socket.outputStream,
                            "200 OK",
                            "text/html; charset=utf-8",
                            buildPs4TestPage()
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

                    path == "/api/packages" -> {

                        sendResponse(
                            socket.outputStream,
                            "200 OK",
                            "application/json; charset=utf-8",
                            buildPackagesJson()
                        )
                    }

                    path == "/download" -> {

                        handleDownload(
                            socket = socket,
                            query = query
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

    private fun buildHomePage(): String {

        val ip =
            getWifiIpv4Address()
                ?.hostAddress
                ?: "SEM WI-FI"

        val packages =
            getPackages()

        val packageHtml =
            if (packages.isEmpty()) {

                """
                    <p>
                        Nenhum arquivo PKG encontrado.
                    </p>
                """.trimIndent()

            } else {

                buildString {

                    append("<h2>PKGs disponíveis</h2>")

                    append("<ul>")

                    for (pkg in packages) {

                        val encodedId =
                            java.net.URLEncoder
                                .encode(
                                    pkg.id,
                                    StandardCharsets.UTF_8.toString()
                                )

                        append("<li>")

                        append(
                            "<strong>"
                        )

                        append(
                            escapeHtml(
                                pkg.name
                            )
                        )

                        append(
                            "</strong>"
                        )

                        append(
                            " - "
                        )

                        append(
                            formatFileSize(
                                pkg.size
                            )
                        )

                        append(
                            " - "
                        )

                        append(
                            "<a href=\"/download?id=$encodedId\">"
                        )

                        append(
                            "BAIXAR"
                        )

                        append(
                            "</a>"
                        )

                        append("</li>")
                    }

                    append("</ul>")
                }
            }

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

                <style>

                    body {
                        font-family: sans-serif;
                        margin: 20px;
                    }

                    h1 {
                        margin-bottom: 8px;
                    }

                    a {
                        text-decoration: none;
                    }

                    li {
                        margin-bottom: 12px;
                    }

                </style>

            </head>

            <body>

                <h1>GTSTORE</h1>

                <p>
                    Servidor HTTP funcionando exclusivamente por Wi-Fi.
                </p>

                <p>
                    Endereço:
                    http://$ip:$port
                </p>

                $packageHtml

                <hr>

                <p>
                    <a href="/ps4">
                        Testar compatibilidade PS4
                    </a>
                </p>

                <p>
                    <a href="/api/status">
                        Ver status da API
                    </a>
                </p>

                <p>
                    <a href="/api/packages">
                        Ver catálogo JSON
                    </a>
                </p>

            </body>

            </html>
        """.trimIndent()
    }

    private fun buildPs4TestPage(): String {

    return """
        <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN"
        "http://www.w3.org/TR/html4/strict.dtd">

        <html>
        <head>
            <title>GTSTORE</title>
        </head>

        <body>

            <h1>GTSTORE</h1>

            <p>Servidor funcionando.</p>

            <p>TESTE PS4</p>

            <p>
                <a href="/api/status">
                    TESTAR STATUS
                </a>
            </p>

            <p>
                <a href="/">
                    VOLTAR
                </a>
            </p>

        </body>
        </html>
    """.trimIndent()
    }

    private fun buildPackagesJson(): String {

        val packages =
            getPackages()

        val json =
            StringBuilder()

        json.append("[\n")

        packages.forEachIndexed { index, pkg ->

            json.append("    {\n")

            json.append(
                "        \"id\": \""
            )

            json.append(
                escapeJson(
                    pkg.id
                )
            )

            json.append("\",\n")

            json.append(
                "        \"name\": \""
            )

            json.append(
                escapeJson(
                    pkg.name
                )
            )

            json.append("\",\n")

            json.append(
                "        \"size\": "
            )

            json.append(
                pkg.size
            )

            json.append(",\n")

            json.append(
                "        \"modified\": "
            )

            json.append(
                pkg.modified
            )

            json.append("\n")

            json.append("    }")

            if (
                index <
                packages.lastIndex
            ) {
                json.append(",")
            }

            json.append("\n")
        }

        json.append("]")

        return json.toString()
    }

    private fun getPackages(): List<WebPackage> {

        return try {

            val uriString =
                context
                    .getSharedPreferences(
                        "GTSTORE",
                        Context.MODE_PRIVATE
                    )
                    .getString(
                        "pkg_folder_uri",
                        null
                    )

            /*
             * A MainActivity atual salva a preferência
             * usando getPreferences(MODE_PRIVATE).
             *
             * Portanto, também procuramos nessa
             * preferência específica da Activity.
             */
            val activityPreferences =
                context
                    .getSharedPreferences(
                        "${context.packageName}_preferences",
                        Context.MODE_PRIVATE
                    )

            val savedUri =
                uriString
                    ?: activityPreferences.getString(
                        "pkg_folder_uri",
                        null
                    )

            if (savedUri.isNullOrBlank()) {
                return emptyList()
            }

            val treeUri =
                android.net.Uri.parse(
                    savedUri
                )

            val root =
                DocumentFile.fromTreeUri(
                    context,
                    treeUri
                )
                    ?: return emptyList()

            val result =
                mutableListOf<WebPackage>()

            scanPackagesForWeb(
                root = root,
                result = result
            )

            result.sortedBy {
                it.name.lowercase(
                    LocaleHolder.locale
                )
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun scanPackagesForWeb(
        root: DocumentFile,
        result: MutableList<WebPackage>
    ) {

        for (file in root.listFiles()) {

            if (file.isDirectory) {

                scanPackagesForWeb(
                    root = file,
                    result = result
                )

            } else if (
                file.isFile &&
                file.name
                    ?.lowercase(
                        LocaleHolder.locale
                    )
                    ?.endsWith(".pkg") == true
            ) {

                val name =
                    file.name
                        ?: "PKG"

                val uri =
                    file.uri.toString()

                result.add(
                    WebPackage(
                        id =
                            buildPackageId(
                                uri
                            ),
                        name = name,
                        uri = uri,
                        size = file.length(),
                        modified =
                            file.lastModified()
                    )
                )
            }
        }
    }

    private fun handleDownload(
        socket: Socket,
        query: String
    ) {

        try {

            val id =
                getQueryParameter(
                    query,
                    "id"
                )

            if (id.isNullOrBlank()) {

                sendResponse(
                    socket.outputStream,
                    "400 Bad Request",
                    "text/plain; charset=utf-8",
                    "ID do arquivo não informado."
                )

                return
            }

            val pkg =
                getPackages()
                    .firstOrNull {
                        it.id == id
                    }

            if (pkg == null) {

                sendResponse(
                    socket.outputStream,
                    "404 Not Found",
                    "text/plain; charset=utf-8",
                    "PKG não encontrado."
                )

                return
            }

            val uri =
                android.net.Uri.parse(
                    pkg.uri
                )

            val input =
                context.contentResolver
                    .openInputStream(uri)

            if (input == null) {

                sendResponse(
                    socket.outputStream,
                    "404 Not Found",
                    "text/plain; charset=utf-8",
                    "Não foi possível abrir o PKG."
                )

                return
            }

            input.use {

                val output =
                    socket.outputStream

                val fileName =
                    pkg.name

                val headers =
                    buildString {

                        append(
                            "HTTP/1.1 200 OK\r\n"
                        )

                        append(
                            "Content-Type: application/octet-stream\r\n"
                        )

                        append(
                            "Content-Length: ${pkg.size}\r\n"
                        )

                        append(
                            "Content-Disposition: attachment; filename=\""
                        )

                        append(
                            escapeHeaderFileName(
                                fileName
                            )
                        )

                        append(
                            "\"\r\n"
                        )

                        append(
                            "Connection: close\r\n"
                        )

                        append(
                            "Cache-Control: no-store\r\n"
                        )

                        append(
                            "Access-Control-Allow-Origin: *\r\n"
                        )

                        append(
                            "\r\n"
                        )
                    }

                output.write(
                    headers.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                val buffer =
                    ByteArray(
                        64 * 1024
                    )

                while (true) {

                    val count =
                        it.read(
                            buffer
                        )

                    if (count <= 0) {
                        break
                    }

                    output.write(
                        buffer,
                        0,
                        count
                    )
                }

                output.flush()
            }

        } catch (_: Exception) {

            /*
             * O cliente pode cancelar o download
             * ou fechar a conexão antes do término.
             */
        }
    }

    private fun getQueryParameter(
        query: String,
        name: String
    ): String? {

        if (query.isBlank()) {
            return null
        }

        val parameters =
            query.split("&")

        for (parameter in parameters) {

            val parts =
                parameter.split(
                    "=",
                    limit = 2
                )

            if (
                parts.size == 2 &&
                parts[0] == name
            ) {

                return try {

                    URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8.toString()
                    )

                } catch (_: Exception) {

                    parts[1]
                }
            }
        }

        return null
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

                append(
                    "Access-Control-Allow-Origin: *\r\n"
                )

                append("\r\n")
            }

        output.write(
            headers.toByteArray(
                StandardCharsets.UTF_8
            )
        )

        if (bodyBytes.isNotEmpty()) {

            output.write(
                bodyBytes
            )
        }

        output.flush()
    }

    private fun buildPackageId(
        path: String
    ): String {

        return try {

            val digest =
                java.security.MessageDigest
                    .getInstance(
                        "SHA-256"
                    )

            val hash =
                digest.digest(
                    path.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

            hash.joinToString("") {
                "%02x".format(it)
            }

        } catch (_: Exception) {

            path.hashCode()
                .toString()
        }
    }

    private fun formatFileSize(
        bytes: Long
    ): String {

        if (bytes < 1024) {
            return "$bytes B"
        }

        val kb =
            bytes / 1024.0

        if (kb < 1024) {

            return String.format(
                LocaleHolder.locale,
                "%.2f KB",
                kb
            )
        }

        val mb =
            kb / 1024.0

        if (mb < 1024) {

            return String.format(
                LocaleHolder.locale,
                "%.2f MB",
                mb
            )
        }

        val gb =
            mb / 1024.0

        if (gb < 1024) {

            return String.format(
                LocaleHolder.locale,
                "%.2f GB",
                gb
            )
        }

        val tb =
            gb / 1024.0

        return String.format(
            LocaleHolder.locale,
            "%.2f TB",
            tb
        )
    }

    private fun escapeJson(
        value: String
    ): String {

        return value
            .replace(
                "\\",
                "\\\\"
            )
            .replace(
                "\"",
                "\\\""
            )
            .replace(
                "\n",
                "\\n"
            )
            .replace(
                "\r",
                "\\r"
            )
            .replace(
                "\t",
                "\\t"
            )
    }

    private fun escapeHtml(
        value: String
    ): String {

        return value
            .replace(
                "&",
                "&amp;"
            )
            .replace(
                "<",
                "&lt;"
            )
            .replace(
                ">",
                "&gt;"
            )
            .replace(
                "\"",
                "&quot;"
            )
            .replace(
                "'",
                "&#39;"
            )
    }

    private fun escapeHeaderFileName(
        value: String
    ): String {

        return value
            .replace(
                "\\",
                "_"
            )
            .replace(
                "\"",
                "_"
            )
            .replace(
                "\r",
                "_"
            )
            .replace(
                "\n",
                "_"
            )
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

    private data class WebPackage(
        val id: String,
        val name: String,
        val uri: String,
        val size: Long,
        val modified: Long
    )

    private object LocaleHolder {
        val locale =
            java.util.Locale.getDefault()
    }
}
