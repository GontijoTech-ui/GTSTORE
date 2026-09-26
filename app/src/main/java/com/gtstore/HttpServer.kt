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
import java.net.URLDecoder
import java.net.URLEncoder
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

    private val activeConnections = AtomicInteger(0)

    val localAddress: String
        get() = getWifiIpv4Address()?.hostAddress ?: "0.0.0.0"

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
                val address = getWifiIpv4Address()

                serverSocket = if (address != null) {
                    ServerSocket(port, 50, address)
                } else {
                    ServerSocket(port)
                }

                running = true

                while (running) {
                    try {
                        val socket = serverSocket?.accept()

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
            activeConnections = activeConnections.get()
        )
    }

    private fun handleClient(socket: Socket) {
        activeConnections.incrementAndGet()

        socket.use { client ->

            try {
                client.soTimeout = 30_000

                val input = BufferedReader(
                    InputStreamReader(
                        client.getInputStream(),
                        StandardCharsets.UTF_8
                    )
                )

                val output = client.getOutputStream()

                val requestLine = input.readLine()
                    ?: return

                if (requestLine.length > MAX_HEADER_SIZE) {
                    sendError(
                        output,
                        431,
                        "Request Header Fields Too Large"
                    )
                    return
                }

                val parts = requestLine.split(" ")

                if (parts.size < 2) {
                    sendError(
                        output,
                        400,
                        "Bad Request"
                    )
                    return
                }

                val method = parts[0].uppercase()
                val target = parts[1]

                val headers = HashMap<String, String>()

                while (true) {
                    val line = input.readLine()
                        ?: break

                    if (line.isEmpty()) {
                        break
                    }

                    val separator = line.indexOf(':')

                    if (separator > 0) {
                        val name =
                            line.substring(0, separator)
                                .trim()
                                .lowercase()

                        val value =
                            line.substring(separator + 1)
                                .trim()

                        headers[name] = value
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

                    else -> {
                        sendError(
                            output,
                            405,
                            "Method Not Allowed",
                            extraHeaders = mapOf(
                                "Allow" to "GET, HEAD"
                            )
                        )
                    }
                }

            } catch (_: Exception) {
                try {
                    sendError(
                        clientOutput(socket),
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

    private fun clientOutput(socket: Socket): OutputStream {
        return socket.getOutputStream()
    }

    private fun handleRequest(
        target: String,
        headers: Map<String, String>,
        output: OutputStream,
        headOnly: Boolean
    ) {

        val uri = Uri.parse(target)

        val path = uri.path ?: "/"

        when {
            path == "/" -> {
                sendHomePage(
                    output = output,
                    headOnly = headOnly
                )
            }

            path == "/ps4" -> {
                sendHomePage(
                    output = output,
                    headOnly = headOnly
                )
            }

            path == "/api/status" -> {
                sendStatusJson(
                    output = output,
                    headOnly = headOnly
                )
            }

            path == "/api/packages" -> {
                sendPackagesJson(
                    output = output,
                    headOnly = headOnly
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

            path.startsWith("/pkg/") -> {
                val segments =
                    path.removePrefix("/pkg/")
                        .split("/")

                val id =
                    segments.firstOrNull()
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

            path == "/favicon.ico" -> {
                sendResponse(
                    output = output,
                    statusCode = 204,
                    statusText = "No Content",
                    contentType = "image/x-icon",
                    body = ByteArray(0),
                    headOnly = headOnly
                )
            }

            else -> {
                sendError(
                    output,
                    404,
                    "Not Found"
                )
            }
        }
    }

    private fun servePackage(
        packageId: Int,
        headers: Map<String, String>,
        output: OutputStream,
        headOnly: Boolean
    ) {

        val packageInfo =
            getPackages().firstOrNull {
                it.id == packageId
            }

        if (packageInfo == null) {
            sendError(
                output,
                404,
                "Package Not Found"
            )
            return
        }

        val totalSize = packageInfo.size

        if (totalSize < 0) {
            sendError(
                output,
                500,
                "Invalid package size"
            )
            return
        }

        var start = 0L
        var end = totalSize - 1
        var partial = false

        val rangeHeader =
            headers["range"]

        if (!rangeHeader.isNullOrBlank()) {

            if (!rangeHeader.startsWith("bytes=")) {
                sendRangeError(
                    output,
                    totalSize
                )
                return
            }

            val ranges =
                rangeHeader
                    .removePrefix("bytes=")
                    .split(",")

            if (ranges.size != 1) {
                sendRangeError(
                    output,
                    totalSize
                )
                return
            }

            val range =
                ranges[0].trim()

            try {

                when {
                    range.startsWith("-") -> {
                        val suffixLength =
                            range
                                .removePrefix("-")
                                .toLong()

                        if (suffixLength <= 0) {
                            sendRangeError(
                                output,
                                totalSize
                            )
                            return
                        }

                        start =
                            if (suffixLength >= totalSize) {
                                0L
                            } else {
                                totalSize - suffixLength
                            }

                        end = totalSize - 1
                    }

                    range.endsWith("-") -> {
                        start =
                            range
                                .removeSuffix("-")
                                .toLong()

                        if (
                            start < 0 ||
                            start >= totalSize
                        ) {
                            sendRangeError(
                                output,
                                totalSize
                            )
                            return
                        }

                        end = totalSize - 1
                    }

                    else -> {
                        val parts =
                            range.split("-", limit = 2)

                        start =
                            parts[0].toLong()

                        end =
                            parts[1].toLong()

                        if (
                            start < 0 ||
                            start >= totalSize ||
                            end < start
                        ) {
                            sendRangeError(
                                output,
                                totalSize
                            )
                            return
                        }

                        if (end >= totalSize) {
                            end = totalSize - 1
                        }
                    }
                }

                partial = true

            } catch (_: Exception) {
                sendRangeError(
                    output,
                    totalSize
                )
                return
            }
        }

        val contentLength =
            if (totalSize == 0L) {
                0L
            } else {
                end - start + 1
            }

        val statusCode =
            if (partial) 206 else 200

        val statusText =
            if (partial) {
                "Partial Content"
            } else {
                "OK"
            }

        val extraHeaders =
            LinkedHashMap<String, String>()

        extraHeaders["Accept-Ranges"] = "bytes"

        extraHeaders["Content-Length"] =
            contentLength.toString()

        extraHeaders["Content-Type"] =
            "application/octet-stream"

        extraHeaders["Content-Disposition"] =
            "attachment; filename=\"${sanitizeFileName(packageInfo.fileName)}\""

        extraHeaders["Cache-Control"] =
            "no-cache"

        if (partial) {
            extraHeaders["Content-Range"] =
                "bytes $start-$end/$totalSize"
        }

        extraHeaders["Access-Control-Allow-Origin"] =
            "*"

        extraHeaders["Access-Control-Expose-Headers"] =
            "Content-Length, Content-Range, Accept-Ranges"

        writeHeaders(
            output = output,
            statusCode = statusCode,
            statusText = statusText,
            headers = extraHeaders
        )

        if (headOnly) {
            output.flush()
            return
        }

        if (contentLength <= 0L) {
            output.flush()
            return
        }

        try {
            context.contentResolver
                .openInputStream(packageInfo.uri)
                ?.use { input ->

                    skipFully(
                        input = input,
                        bytes = start
                    )

                    streamRange(
                        input = input,
                        output = output,
                        bytesToSend = contentLength
                    )
                }
                ?: run {
                    return
                }

        } catch (_: Exception) {
            return
        }

        output.flush()
    }

    private fun streamRange(
        input: InputStream,
        output: OutputStream,
        bytesToSend: Long
    ) {

        val buffer =
            ByteArray(BUFFER_SIZE)

        var remaining = bytesToSend

        while (remaining > 0) {

            val requested =
                minOf(
                    buffer.size.toLong(),
                    remaining
                ).toInt()

            val read =
                input.read(
                    buffer,
                    0,
                    requested
                )

            if (read <= 0) {
                break
            }

            output.write(
                buffer,
                0,
                read
            )

            remaining -= read
        }
    }

    private fun skipFully(
        input: InputStream,
        bytes: Long
    ) {

        var remaining = bytes

        while (remaining > 0) {

            val skipped =
                input.skip(remaining)

            if (skipped > 0) {
                remaining -= skipped
                continue
            }

            val read =
                input.read()

            if (read == -1) {
                break
            }

            remaining--
        }
    }

    private fun getPackages(): List<PackageInfo> {

        val preferences =
            context.getSharedPreferences(
                "GTSTORE",
                Context.MODE_PRIVATE
            )

        val savedUri =
            preferences.getString(
                "pkg_folder_uri",
                null
            )

        val uriString =
            savedUri
                ?: run {
                    val activityPreferences =
                        context.getSharedPreferences(
                            "GTSTORE_PREFS",
                            Context.MODE_PRIVATE
                        )

                    activityPreferences.getString(
                        "pkg_folder_uri",
                        null
                    )
                }

        if (uriString.isNullOrBlank()) {
            return emptyList()
        }

        val root =
            try {
                DocumentFile.fromTreeUri(
                    context,
                    Uri.parse(uriString)
                )
            } catch (_: Exception) {
                null
            }

        if (root == null || !root.exists()) {
            return emptyList()
        }

        val files =
            mutableListOf<DocumentFile>()

        scanDocumentFile(
            root = root,
            result = files
        )

        return files
            .filter {
                it.isFile &&
                    it.name
                        ?.lowercase()
                        ?.endsWith(".pkg") == true
            }
            .sortedBy {
                it.name?.lowercase() ?: ""
            }
            .mapIndexed { index, file ->

                val name =
                    file.name ?: "package.pkg"

                PackageInfo(
                    id = index + 1,
                    name = name,
                    fileName = name,
                    size = file.length(),
                    modified = file.lastModified(),
                    type = "PKG",
                    uri = file.uri
                )
            }
    }

    private fun scanDocumentFile(
        root: DocumentFile,
        result: MutableList<DocumentFile>
    ) {

        val children =
            try {
                root.listFiles()
            } catch (_: Exception) {
                emptyArray()
            }

        for (child in children) {

            if (child.isDirectory) {
                scanDocumentFile(
                    root = child,
                    result = result
                )
            } else {
                result.add(child)
            }
        }
    }

    private fun sendPackagesJson(
        output: OutputStream,
        headOnly: Boolean
    ) {

        val packages =
            getPackages()

        val array =
            JSONArray()

        for (pkg in packages) {

            val encodedName =
                URLEncoder.encode(
                    pkg.fileName,
                    StandardCharsets.UTF_8.toString()
                )

            val item =
                JSONObject()

            item.put(
                "id",
                pkg.id
            )

            item.put(
                "name",
                pkg.name
            )

            item.put(
                "file",
                pkg.fileName
            )

            item.put(
                "size",
                pkg.size
            )

            item.put(
                "formattedSize",
                formatFileSize(pkg.size)
            )

            item.put(
                "modified",
                pkg.modified
            )

            item.put(
                "type",
                pkg.type
            )

            item.put(
                "url",
                "/pkg/${pkg.id}/$encodedName"
            )

            item.put(
                "download",
                "/download?id=${pkg.id}"
            )

            array.put(item)
        }

        val json =
            JSONObject()

        json.put(
            "count",
            packages.size
        )

        json.put(
            "packages",
            array
        )

        val body =
            json.toString()
                .toByteArray(StandardCharsets.UTF_8)

        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType = "application/json; charset=utf-8",
            body = body,
            headOnly = headOnly,
            extraHeaders = mapOf(
                "Cache-Control" to "no-cache",
                "Access-Control-Allow-Origin" to "*"
            )
        )
    }

    private fun sendStatusJson(
        output: OutputStream,
        headOnly: Boolean
    ) {

        val status =
            getStatus()

        val json =
            JSONObject()

        json.put(
            "online",
            status.running
        )

        json.put(
            "running",
            status.running
        )

        json.put(
            "port",
            status.port
        )

        json.put(
            "address",
            status.localAddress
        )

        json.put(
            "localAddress",
            status.localAddress
        )

        json.put(
            "url",
            status.url
        )

        json.put(
            "activeConnections",
            status.activeConnections
        )

        val body =
            json.toString()
                .toByteArray(StandardCharsets.UTF_8)

        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType = "application/json; charset=utf-8",
            body = body,
            headOnly = headOnly,
            extraHeaders = mapOf(
                "Cache-Control" to "no-cache",
                "Access-Control-Allow-Origin" to "*"
            )
        )
    }

    private fun sendHomePage(
        output: OutputStream,
        headOnly: Boolean
    ) {

        val status =
            getStatus()

        val packages =
            getPackages()

        val html =
            """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0">
                <title>GTSTORE</title>

                <style>
                    body {
                        margin: 0;
                        padding: 24px;
                        background: #101114;
                        color: #ffffff;
                        font-family: Arial, sans-serif;
                    }

                    .container {
                        max-width: 900px;
                        margin: auto;
                    }

                    h1 {
                        margin-bottom: 8px;
                    }

                    .status {
                        padding: 14px;
                        border-radius: 10px;
                        background: #1b1d22;
                        margin-bottom: 20px;
                    }

                    .online {
                        color: #4caf50;
                    }

                    .offline {
                        color: #f44336;
                    }

                    .package {
                        background: #1b1d22;
                        padding: 16px;
                        margin-bottom: 12px;
                        border-radius: 10px;
                    }

                    .package a {
                        color: #64b5f6;
                        text-decoration: none;
                    }

                    .meta {
                        color: #b0b0b0;
                        font-size: 14px;
                        margin-top: 6px;
                    }
                </style>
            </head>

            <body>
                <div class="container">

                    <h1>GTSTORE</h1>

                    <div class="status">
                        <strong>
                            STATUS:
                            <span class="${if (status.running) "online" else "offline"}">
                                ${if (status.running) "ONLINE" else "OFFLINE"}
                            </span>
                        </strong>

                        <div class="meta">
                            Porta: ${status.port}
                        </div>

                        <div class="meta">
                            Endereço:
                            ${status.localAddress}
                        </div>

                        <div class="meta">
                            URL:
                            ${status.url}
                        </div>

                        <div class="meta">
                            Conexões:
                            ${status.activeConnections}
                        </div>

                        <div class="meta">
                            PKGs:
                            ${packages.size}
                        </div>
                    </div>

                    <h2>Pacotes</h2>

                    ${
                        if (packages.isEmpty()) {
                            "<p>Nenhum arquivo PKG encontrado.</p>"
                        } else {
                            packages.joinToString("") { pkg ->

                                val encodedName =
                                    URLEncoder.encode(
                                        pkg.fileName,
                                        StandardCharsets.UTF_8.toString()
                                    )

                                """
                                <div class="package">

                                    <strong>
                                        ${escapeHtml(pkg.name)}
                                    </strong>

                                    <div class="meta">
                                        Tamanho:
                                        ${formatFileSize(pkg.size)}
                                    </div>

                                    <div class="meta">
                                        Tipo:
                                        ${pkg.type}
                                    </div>

                                    <p>
                                        <a href="/pkg/${pkg.id}/$encodedName">
                                            Abrir URL do PKG
                                        </a>
                                    </p>

                                    <div class="meta">
                                        /download?id=${pkg.id}
                                    </div>

                                </div>
                                """
                            }
                        }
                    }

                </div>
            </body>
            </html>
            """.trimIndent()

        val body =
            html.toByteArray(StandardCharsets.UTF_8)

        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType = "text/html; charset=utf-8",
            body = body,
            headOnly = headOnly,
            extraHeaders = mapOf(
                "Cache-Control" to "no-cache",
                "Access-Control-Allow-Origin" to "*"
            )
        )
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        headOnly: Boolean,
        extraHeaders: Map<String, String> = emptyMap()
    ) {

        val headers =
            LinkedHashMap<String, String>()

        headers["Content-Type"] =
            contentType

        headers["Content-Length"] =
            body.size.toString()

        headers["Connection"] =
            "close"

        headers.putAll(
            extraHeaders
        )

        writeHeaders(
            output = output,
            statusCode = statusCode,
            statusText = statusText,
            headers = headers
        )

        if (!headOnly) {
            output.write(body)
        }

        output.flush()
    }

    private fun writeHeaders(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        headers: Map<String, String>
    ) {

        val builder =
            StringBuilder()

        builder.append(
            "HTTP/1.1 $statusCode $statusText\r\n"
        )

        builder.append(
            "Server: GTSTORE\r\n"
        )

        builder.append(
            "Connection: close\r\n"
        )

        for ((name, value) in headers) {
            builder.append(
                "$name: $value\r\n"
            )
        }

        builder.append("\r\n")

        output.write(
            builder.toString()
                .toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun sendError(
        output: OutputStream,
        statusCode: Int,
        message: String,
        extraHeaders: Map<String, String> = emptyMap()
    ) {

        val body =
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>$statusCode</title>
            </head>
            <body>
                <h1>$statusCode</h1>
                <p>${escapeHtml(message)}</p>
            </body>
            </html>
            """.trimIndent()
                .toByteArray(StandardCharsets.UTF_8)

        sendResponse(
            output = output,
            statusCode = statusCode,
            statusText = message,
            contentType = "text/html; charset=utf-8",
            body = body,
            headOnly = false,
            extraHeaders = extraHeaders
        )
    }

    private fun sendRangeError(
        output: OutputStream,
        totalSize: Long
    ) {

        val headers =
            mapOf(
                "Content-Range" to
                    "bytes */$totalSize",
                "Accept-Ranges" to
                    "bytes",
                "Access-Control-Allow-Origin" to
                    "*"
            )

        sendError(
            output = output,
            statusCode = 416,
            message = "Range Not Satisfiable",
            extraHeaders = headers
        )
    }

    private fun getWifiIpv4Address(): Inet4Address? {

        return try {

            val interfaces =
                Collections.list(
                    NetworkInterface.getNetworkInterfaces()
                )

            val candidates =
                mutableListOf<Inet4Address>()

            for (networkInterface in interfaces) {

                if (!networkInterface.isUp) {
                    continue
                }

                if (networkInterface.isLoopback) {
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
                        !address.isLinkLocalAddress
                    ) {
                        candidates.add(address)
                    }
                }
            }

            candidates.firstOrNull()

        } catch (_: Exception) {
            null
        }
    }

    private fun formatFileSize(
        bytes: Long
    ): String {

        if (bytes <= 0) {
            return "0 B"
        }

        val units =
            arrayOf(
                "B",
                "KB",
                "MB",
                "GB",
                "TB"
            )

        var value =
            bytes.toDouble()

        var index = 0

        while (
            value >= 1024 &&
            index < units.lastIndex
        ) {
            value /= 1024
            index++
        }

        return if (index == 0) {
            "${value.toLong()} ${units[index]}"
        } else {
            String.format(
                "%.2f %s",
                value,
                units[index]
            )
        }
    }

    private fun sanitizeFileName(
        name: String
    ): String {

        return name
            .replace("\"", "_")
            .replace("\r", "_")
            .replace("\n", "_")
    }

    private fun escapeHtml(
        text: String
    ): String {

        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
