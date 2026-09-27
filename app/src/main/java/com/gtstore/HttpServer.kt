package com.gtstore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
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
        private const val MAX_POST_SIZE = 1024 * 1024

        private const val PAYLOAD_DIR = "payloads"

        private val ALLOWED_PAYLOADS = setOf(
            "rpi_installer.bin",
            "direct-installer.bin",
            "ps4-rpi.bin"
        )
    }

    private var serverSocket: ServerSocket? = null

    @Volatile
    var running = false
        private set

    private val executor =
        Executors.newCachedThreadPool()

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
                            StandardCharsets.ISO_8859_1
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

                val contentLength =
                    headers["content-length"]
                        ?.toIntOrNull()
                        ?: 0

                if (contentLength < 0 ||
                    contentLength > MAX_POST_SIZE
                ) {

                    sendError(
                        output,
                        413,
                        "Request Entity Too Large"
                    )

                    return
                }

                val body =
                    if (
                        method == "POST" &&
                        contentLength > 0
                    ) {
                        readRequestBody(
                            client.getInputStream(),
                            contentLength
                        )
                    } else {
                        ByteArray(0)
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

                    "POST" -> {

                        handlePostRequest(
                            target = target,
                            headers = headers,
                            body = body,
                            output = output
                        )
                    }

                    "OPTIONS" -> {

                        sendOptions(output)
                    }

                    else -> {

                        sendError(
                            output,
                            405,
                            "Method Not Allowed",
                            mapOf(
                                "Allow" to
                                    "GET, HEAD, POST, OPTIONS"
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

    private fun readRequestBody(
        input: InputStream,
        length: Int
    ): ByteArray {

        val body =
            ByteArray(length)

        var offset = 0

        while (offset < length) {

            val read =
                input.read(
                    body,
                    offset,
                    length - offset
                )

            if (read <= 0) {
                break
            }

            offset += read
        }

        return if (offset == length) {
            body
        } else {
            body.copyOf(offset)
        }
    }

    private fun handlePostRequest(
        target: String,
        headers: Map<String, String>,
        body: ByteArray,
        output: OutputStream
    ) {

        val uri =
            Uri.parse(target)

        val path =
            uri.path ?: "/"

        when (path) {

            "/api/send-payload" -> {

                handleSendPayload(
                    body = body,
                    output = output
                )
            }

            "/api/install" -> {

                handleRemoteInstall(
                    body = body,
                    output = output
                )
            }

            else -> {

                sendError(
                    output,
                    404,
                    "POST endpoint not found"
                )
            }
        }
    }

    private fun handleSendPayload(
        body: ByteArray,
        output: OutputStream
    ) {

        try {

            val json =
                JSONObject(
                    String(
                        body,
                        StandardCharsets.UTF_8
                    )
                )

            val ip =
                json.optString("ip")
                    .trim()

            val payloadName =
                json.optString("payloadName")
                    .trim()

            if (!isValidIp(ip)) {

                sendJsonError(
                    output,
                    400,
                    "IP do PS4 inválido."
                )

                return
            }

            if (
                !ALLOWED_PAYLOADS.contains(
                    payloadName
                )
            ) {

                sendJsonError(
                    output,
                    400,
                    "Payload não autorizado."
                )

                return
            }

            val payload =
                loadPayload(
                    payloadName
                )

            if (payload == null) {

                sendJsonError(
                    output,
                    404,
                    "Payload não encontrado em assets/$PAYLOAD_DIR."
                )

                return
            }

            val result =
                sendPayloadToBinLoader(
                    ip = ip,
                    payload = payload
                )

            if (result.success) {

                sendJson(
                    output,
                    200,
                    JSONObject()
                        .put(
                            "success",
                            true
                        )
                        .put(
                            "message",
                            "Payload enviado para $ip:9090."
                        )
                        .put(
                            "bytes",
                            payload.size
                        )
                )

            } else {

                sendJson(
                    output,
                    500,
                    JSONObject()
                        .put(
                            "success",
                            false
                        )
                        .put(
                            "error",
                            result.error
                        )
                )
            }

        } catch (e: Exception) {

            sendJsonError(
                output,
                400,
                "JSON inválido: ${e.message}"
            )
        }
    }

    private data class PayloadResult(
        val success: Boolean,
        val error: String? = null
    )

    private fun sendPayloadToBinLoader(
        ip: String,
        payload: ByteArray
    ): PayloadResult {

        return try {

            Socket().use { socket ->

                socket.connect(
                    java.net.InetSocketAddress(
                        ip,
                        9090
                    ),
                    5000
                )

                socket.soTimeout = 5000

                val output =
                    socket.getOutputStream()

                output.write(payload)
                output.flush()

                try {
                    socket.shutdownOutput()
                } catch (_: Exception) {
                }
            }

            PayloadResult(
                success = true
            )

        } catch (e: Exception) {

            PayloadResult(
                success = false,
                error =
                    "Falha TCP para $ip:9090: ${e.message}"
            )
        }
    }

    private fun loadPayload(
        payloadName: String
    ): ByteArray? {

        return try {

            context.assets
                .open(
                    "$PAYLOAD_DIR/$payloadName"
                )
                .use { input ->

                    val output =
                        ByteArrayOutputStream()

                    val buffer =
                        ByteArray(BUFFER_SIZE)

                    while (true) {

                        val read =
                            input.read(buffer)

                        if (read <= 0) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            read
                        )
                    }

                    output.toByteArray()
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun handleRemoteInstall(
        body: ByteArray,
        output: OutputStream
    ) {

        try {

            val json =
                JSONObject(
                    String(
                        body,
                        StandardCharsets.UTF_8
                    )
                )

            val ip =
                json.optString("ip")
                    .trim()

            val pkgUrl =
                json.optString("pkgUrl")
                    .trim()

            if (!isValidIp(ip)) {

                sendJsonError(
                    output,
                    400,
                    "IP do PS4 inválido."
                )

                return
            }

            if (pkgUrl.isBlank()) {

                sendJsonError(
                    output,
                    400,
                    "URL do PKG ausente."
                )

                return
            }

            val result =
                sendInstallRequestToPs4(
                    ip = ip,
                    pkgUrl = pkgUrl
                )

            if (result.success) {

                sendJson(
                    output,
                    200,
                    JSONObject()
                        .put(
                            "success",
                            true
                        )
                        .put(
                            "message",
                            "PS4 aceitou a solicitação de instalação."
                        )
                        .put(
                            "response",
                            result.response
                        )
                )

            } else {

                sendJson(
                    output,
                    502,
                    JSONObject()
                        .put(
                            "success",
                            false
                        )
                        .put(
                            "error",
                            result.error
                        )
                )
            }

        } catch (e: Exception) {

            sendJsonError(
                output,
                400,
                "JSON inválido: ${e.message}"
            )
        }
    }

    private data class InstallResult(
        val success: Boolean,
        val response: String = "",
        val error: String? = null
    )

    private fun sendInstallRequestToPs4(
        ip: String,
        pkgUrl: String
    ): InstallResult {

        return try {

            Socket().use { socket ->

                socket.connect(
                    java.net.InetSocketAddress(
                        ip,
                        12800
                    ),
                    5000
                )

                socket.soTimeout = 5000

                val body =
                    JSONObject()
                        .put(
                            "type",
                            "direct"
                        )
                        .put(
                            "packages",
                            JSONArray()
                                .put(pkgUrl)
                        )
                        .toString()

                val request =
                    buildString {

                        append(
                            "POST /api/install HTTP/1.1\r\n"
                        )

                        append(
                            "Host: $ip:12800\r\n"
                        )

                        append(
                            "Content-Type: application/json\r\n"
                        )

                        append(
                            "Content-Length: "
                        )

                        append(
                            body.toByteArray(
                                StandardCharsets.UTF_8
                            ).size
                        )

                        append(
                            "\r\n"
                        )

                        append(
                            "Connection: close\r\n"
                        )

                        append(
                            "\r\n"
                        )

                        append(body)
                    }

                val output =
                    socket.getOutputStream()

                output.write(
                    request.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                output.flush()

                val response =
                    readHttpResponse(
                        socket.getInputStream()
                    )

                val status =
                    response.first

                val responseBody =
                    response.second

                if (
                    status in 200..299
                ) {

                    InstallResult(
                        success = true,
                        response =
                            responseBody
                    )

                } else {

                    InstallResult(
                        success = false,
                        response =
                            responseBody,
                        error =
                            "PS4 respondeu HTTP $status"
                    )
                }
            }

        } catch (e: Exception) {

            InstallResult(
                success = false,
                error =
                    "Falha ao comunicar com $ip:12800: ${e.message}"
            )
        }
    }

    private fun readHttpResponse(
        input: InputStream
    ): Pair<Int, String> {

        val headerBuffer =
            ByteArrayOutputStream()

        val marker =
            byteArrayOf(
                '\r'.code.toByte(),
                '\n'.code.toByte(),
                '\r'.code.toByte(),
                '\n'.code.toByte()
            )

        var matched = 0

        while (
            headerBuffer.size() <
            MAX_HEADER_SIZE
        ) {

            val value =
                input.read()

            if (value == -1) {
                break
            }

            headerBuffer.write(value)

            if (
                value.toByte() ==
                marker[matched]
            ) {

                matched++

                if (matched == 4) {
                    break
                }

            } else {

                matched =
                    if (
                        value.toByte() ==
                        marker[0]
                    ) {
                        1
                    } else {
                        0
                    }
            }
        }

        val headersText =
            String(
                headerBuffer.toByteArray(),
                StandardCharsets.ISO_8859_1
            )

        val firstLine =
            headersText
                .lineSequence()
                .firstOrNull()
                ?: ""

        val status =
            firstLine
                .split(" ")
                .getOrNull(1)
                ?.toIntOrNull()
                ?: 0

        val contentLength =
            headersText
                .lineSequence()
                .firstOrNull {
                    it.startsWith(
                        "Content-Length:",
                        ignoreCase = true
                    )
                }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?: 0

        val body =
            if (
                contentLength > 0 &&
                contentLength <= MAX_POST_SIZE
            ) {

                val bytes =
                    ByteArray(
                        contentLength
                    )

                var offset = 0

                while (
                    offset <
                    contentLength
                ) {

                    val read =
                        input.read(
                            bytes,
                            offset,
                            contentLength - offset
                        )

                    if (read <= 0) {
                        break
                    }

                    offset += read
                }

                String(
                    bytes,
                    0,
                    offset,
                    StandardCharsets.UTF_8
                )

            } else {
                ""
            }

        return Pair(
            status,
            body
        )
    }

    private fun isValidIp(
        ip: String
    ): Boolean {

        if (ip.isBlank()) {
            return false
        }

        val parts =
            ip.split(".")

        if (parts.size != 4) {
            return false
        }

        return try {

            parts.all {

                val value =
                    it.toInt()

                value in 0..255
            }

        } catch (_: Exception) {

            false
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

                val id =
                    path.removePrefix("/pkg/")
                        .split("/")
                        .firstOrNull()
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
            getPackages()
                .firstOrNull {
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

        val totalSize =
            packageInfo.size

        var start = 0L

        var end =
            if (totalSize > 0) {
                totalSize - 1
            } else {
                0
            }

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

                        if (
                            suffixLength <= 0 ||
                            totalSize <= 0
                        ) {

                            sendRangeError(
                                output,
                                totalSize
                            )

                            return
                        }

                        start =
                            if (
                                suffixLength >= totalSize
                            ) {
                                0L
                            } else {
                                totalSize - suffixLength
                            }

                        end =
                            totalSize - 1
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

                        end =
                            totalSize - 1
                    }

                    else -> {

                        val parts =
                            range.split(
                                "-",
                                limit = 2
                            )

                        if (parts.size != 2) {

                            sendRangeError(
                                output,
                                totalSize
                            )

                            return
                        }

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

        val responseHeaders =
            LinkedHashMap<String, String>()

        responseHeaders["Accept-Ranges"] =
            "bytes"

        responseHeaders["Content-Length"] =
            contentLength.toString()

        responseHeaders["Content-Type"] =
            "application/octet-stream"

        responseHeaders["Content-Disposition"] =
            "attachment; filename=\"${sanitizeFileName(packageInfo.fileName)}\""

        responseHeaders["Cache-Control"] =
            "no-cache"

        responseHeaders["Access-Control-Allow-Origin"] =
            "*"

        responseHeaders["Access-Control-Expose-Headers"] =
            "Content-Length, Content-Range, Accept-Ranges"

        if (partial) {

            responseHeaders["Content-Range"] =
                "bytes $start-$end/$totalSize"
        }

        writeHeaders(
            output = output,
            statusCode = statusCode,
            statusText = statusText,
            headers = responseHeaders
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
                        input,
                        start
                    )

                    streamRange(
                        input,
                        output,
                        contentLength
                    )
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

        var remaining =
            bytesToSend

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
                ?: context.getSharedPreferences(
                    "GTSTORE_PREFS",
                    Context.MODE_PRIVATE
                ).getString(
                    "pkg_folder_uri",
                    null
                )

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

        if (
            root == null ||
            !root.exists()
        ) {
            return emptyList()
        }

        val files =
            mutableListOf<DocumentFile>()

        scanDocumentFile(
            root,
            files
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
                    child,
                    result
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

            val item =
                JSONObject()

            item.put("id", pkg.id)
            item.put("name", pkg.name)
            item.put("file", pkg.fileName)
            item.put("size", pkg.size)
            item.put(
                "formattedSize",
                formatFileSize(pkg.size)
            )
            item.put(
                "modified",
                pkg.modified
            )
            item.put("type", pkg.type)

            item.put(
                "url",
                "/pkg/${pkg.id}"
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
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType =
                "application/json; charset=utf-8",
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
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType =
                "application/json; charset=utf-8",
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

        val html =
            """
<!DOCTYPE html>
<html lang="pt-BR">

<head>

<meta charset="UTF-8">

<meta
    name="viewport"
    content="width=device-width, initial-scale=1.0"
>

<title>GTSTORE - PS4 PKG Installer</title>

<style>

* {
    box-sizing: border-box;
}

body {
    margin: 0;
    padding: 25px;
    background: #101114;
    color: #fff;
    font-family: Arial, Helvetica, sans-serif;
}

.container {
    max-width: 850px;
    margin: auto;
}

h1 {
    margin-top: 0;
}

h2 {
    margin-top: 0;
}

.subtitle {
    color: #999;
    margin-bottom: 25px;
}

.panel {
    background: #1b1d22;
    border-radius: 12px;
    padding: 20px;
    margin-bottom: 18px;
    border: 1px solid #292c33;
}

label {
    display: block;
    margin-bottom: 8px;
    color: #aaa;
    font-weight: bold;
}

input,
select {
    width: 100%;
    padding: 13px;
    margin-bottom: 15px;
    background: #0f1013;
    color: #fff;
    border: 1px solid #444;
    border-radius: 7px;
    font-size: 16px;
}

button {
    width: 100%;
    padding: 14px;
    border: 0;
    border-radius: 7px;
    background: #0070d1;
    color: white;
    font-size: 16px;
    font-weight: bold;
    cursor: pointer;
    margin-top: 5px;
}

button:disabled {
    background: #444;
    color: #999;
}

.secondary {
    background: #333840;
}

.status,
.pkg-info {
    background: #111317;
    border-radius: 8px;
    padding: 15px;
    line-height: 1.8;
    word-break: break-word;
}

.online {
    color: #4caf50;
    font-weight: bold;
}

.offline {
    color: #f44336;
    font-weight: bold;
}

#log {
    min-height: 220px;
    max-height: 400px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
    background: #08090b;
    border: 1px solid #333;
    border-radius: 8px;
    padding: 15px;
    font-family: monospace;
    font-size: 13px;
    color: #00ff66;
}

.success {
    color: #00ff66 !important;
}

.error {
    color: #ff5252 !important;
}

.warning {
    color: #ffca28 !important;
}

.info {
    color: #61dafb !important;
}

.small {
    color: #777;
    font-size: 12px;
    line-height: 1.5;
}

</style>

</head>

<body>

<div class="container">

<h1>GTSTORE</h1>

<div class="subtitle">
PS4 Remote PKG Installer
</div>

<div class="panel">

<div class="status">

<strong>Servidor GTSTORE:</strong>

<span
    id="serverStatus"
    class="${if (status.running) "online" else "offline"}"
>
${if (status.running) " ONLINE" else " OFFLINE"}
</span>

<br>

<strong>URL:</strong>

<span id="serverUrl">
${escapeHtml(status.url)}
</span>

<br>

<strong>Porta:</strong>

<span>${status.port}</span>

<br>

<strong>Conexões:</strong>

<span id="connections">
${status.activeConnections}
</span>

</div>

</div>

<div class="panel">

<h2>PS4</h2>

<label for="ps4ip">
IP do PS4
</label>

<input
    type="text"
    id="ps4ip"
    placeholder="Ex: 192.168.0.3"
>

<button
    class="secondary"
    onclick="testarPS4()"
>
Testar comunicação
</button>

</div>

<div class="panel">

<h2>PKG</h2>

<label for="pkgSelect">
Pacote disponível
</label>

<select
    id="pkgSelect"
    onchange="mostrarPkg()"
>

<option value="">
Carregando PKGs...
</option>

</select>

<div
    id="pkgInfo"
    class="pkg-info"
>
Nenhum PKG selecionado.
</div>

<button
    id="installButton"
    onclick="iniciarInstalacao()"
    disabled
>
Enviar jogo para o PS4
</button>

</div>

<div class="panel">

<h2>Console</h2>

<div id="log">
&gt; GTSTORE iniciado.
&gt; Aguardando comando...
</div>

</div>

<div class="panel">

<div class="small">

Fluxo GTSTORE:

<br><br>

1. Browser → Android :8080

<br>

2. Android → TCP RAW → PS4 :9090

<br>

3. BinLoader recebe o payload

<br>

4. Android → PS4 :12800

<br>

5. PS4 registra a URL do PKG

<br>

6. PS4 baixa o PKG diretamente do Android

</div>

</div>

</div>

<script>

let packages = [];

let selectedPackage = null;

function log(message, type) {

    const logDiv =
        document.getElementById("log");

    if (type) {
        logDiv.className = type;
    }

    logDiv.innerText +=
        "\\n> " + message;

    logDiv.scrollTop =
        logDiv.scrollHeight;
}

function getServerBaseUrl() {

    return window.location.origin;
}

async function carregarStatus() {

    try {

        const response =
            await fetch(
                "/api/status",
                {
                    cache: "no-cache"
                }
            );

        const data =
            await response.json();

        const status =
            document.getElementById(
                "serverStatus"
            );

        status.innerText =
            data.online
                ? " ONLINE"
                : " OFFLINE";

        status.className =
            data.online
                ? "online"
                : "offline";

        document.getElementById(
            "serverUrl"
        ).innerText =
            data.url;

        document.getElementById(
            "connections"
        ).innerText =
            data.activeConnections;

    } catch (error) {

        document.getElementById(
            "serverStatus"
        ).innerText =
            " OFFLINE";

        document.getElementById(
            "serverStatus"
        ).className =
            "offline";
    }
}

async function carregarPackages() {

    const select =
        document.getElementById(
            "pkgSelect"
        );

    try {

        const response =
            await fetch(
                "/api/packages",
                {
                    cache: "no-cache"
                }
            );

        if (!response.ok) {
            throw new Error(
                "HTTP " + response.status
            );
        }

        const data =
            await response.json();

        packages =
            data.packages || [];

        select.innerHTML = "";

        if (packages.length === 0) {

            const option =
                document.createElement(
                    "option"
                );

            option.value = "";

            option.textContent =
                "Nenhum PKG encontrado";

            select.appendChild(option);

            log(
                "Nenhum PKG encontrado.",
                "warning"
            );

            return;
        }

        const first =
            document.createElement(
                "option"
            );

        first.value = "";

        first.textContent =
            "Selecione um PKG...";

        select.appendChild(first);

        packages.forEach(
            function(pkg) {

                const option =
                    document.createElement(
                        "option"
                    );

                option.value =
                    String(pkg.id);

                option.textContent =
                    pkg.name +
                    " (" +
                    pkg.formattedSize +
                    ")";

                select.appendChild(option);
            }
        );

        log(
            packages.length +
            " PKG(s) carregado(s).",
            "info"
        );

    } catch (error) {

        select.innerHTML = "";

        const option =
            document.createElement(
                "option"
            );

        option.value = "";

        option.textContent =
            "Erro ao carregar PKGs";

        select.appendChild(option);

        log(
            "Erro ao carregar PKGs: " +
            error.message,
            "error"
        );
    }
}

function mostrarPkg() {

    const select =
        document.getElementById(
            "pkgSelect"
        );

    const id =
        parseInt(select.value);

    selectedPackage =
        packages.find(
            function(pkg) {
                return pkg.id === id;
            }
        );

    const info =
        document.getElementById(
            "pkgInfo"
        );

    const button =
        document.getElementById(
            "installButton"
        );

    if (!selectedPackage) {

        info.innerText =
            "Nenhum PKG selecionado.";

        button.disabled = true;

        return;
    }

    const url =
        getServerBaseUrl() +
        selectedPackage.url;

    info.innerHTML =
        "<strong>Nome:</strong> " +
        escapeHtml(
            selectedPackage.name
        ) +

        "<br>" +

        "<strong>Tamanho:</strong> " +
        escapeHtml(
            selectedPackage.formattedSize
        ) +

        "<br>" +

        "<strong>URL:</strong><br>" +

        escapeHtml(url);

    button.disabled = false;
}

async function testarPS4() {

    const ip =
        document
            .getElementById("ps4ip")
            .value
            .trim();

    if (!ip) {

        alert(
            "Informe o IP do PS4."
        );

        return;
    }

    log(
        "Testando comunicação com o PS4..."
    );

    try {

        const response =
            await fetch(
                "/api/send-payload",
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body:
                        JSON.stringify({
                            ip: ip,
                            payloadName:
                                "rpi_installer.bin"
                        })
                }
            );

        const data =
            await response.json();

        if (data.success) {

            log(
                "TCP RAW 9090: conexão e envio realizados.",
                "success"
            );

            log(
                "Bytes enviados: " +
                data.bytes,
                "success"
            );

        } else {

            log(
                "Falha 9090: " +
                data.error,
                "error"
            );
        }

    } catch (error) {

        log(
            "Erro ao testar 9090: " +
            error.message,
            "error"
        );
    }
}

async function enviarPayload(ip) {

    log(
        "FASE 1: Android → TCP RAW → PS4:9090",
        "info"
    );

    log(
        "Payload: rpi_installer.bin"
    );

    try {

        const response =
            await fetch(
                "/api/send-payload",
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body:
                        JSON.stringify({
                            ip: ip,
                            payloadName:
                                "rpi_installer.bin"
                        })
                }
            );

        const data =
            await response.json();

        if (!response.ok ||
            !data.success
        ) {

            log(
                "Falha na Fase 1: " +
                (
                    data.error ||
                    "erro desconhecido"
                ),
                "error"
            );

            return false;
        }

        log(
            "TCP RAW 9090 concluído.",
            "success"
        );

        log(
            "Bytes enviados: " +
            data.bytes,
            "success"
        );

        return true;

    } catch (error) {

        log(
            "Erro na Fase 1: " +
            error.message,
            "error"
        );

        return false;
    }
}

async function registrarPkg(ip, pkgUrl) {

    log(
        "FASE 2: Android → PS4:12800",
        "info"
    );

    log(
        "URL enviada ao PS4:"
    );

    log(
        pkgUrl
    );

    try {

        const response =
            await fetch(
                "/api/install",
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body:
                        JSON.stringify({
                            ip: ip,
                            pkgUrl: pkgUrl
                        })
                }
            );

        const data =
            await response.json();

        if (!response.ok ||
            !data.success
        ) {

            log(
                "Falha na Fase 2: " +
                (
                    data.error ||
                    "erro desconhecido"
                ),
                "error"
            );

            return false;
        }

        log(
            "PS4 recebeu a solicitação de instalação.",
            "success"
        );

        if (data.response) {

            log(
                "Resposta: " +
                data.response,
                "success"
            );
        }

        return true;

    } catch (error) {

        log(
            "Erro na Fase 2: " +
            error.message,
            "error"
        );

        return false;
    }
}

async function iniciarInstalacao() {

    const ip =
        document
            .getElementById("ps4ip")
            .value
            .trim();

    if (!ip) {

        alert(
            "Informe o IP do PS4."
        );

        return;
    }

    if (!selectedPackage) {

        alert(
            "Selecione um PKG."
        );

        return;
    }

    const pkgUrl =
        getServerBaseUrl() +
        selectedPackage.url;

    const button =
        document.getElementById(
            "installButton"
        );

    button.disabled = true;

    log(
        "--------------------------------"
    );

    log(
        "Iniciando instalação..."
    );

    log(
        "PS4: " + ip
    );

    log(
        "PKG: " +
        selectedPackage.name
    );

    log(
        "URL: " +
        pkgUrl
    );

    const payloadOk =
        await enviarPayload(ip);

    if (!payloadOk) {

        log(
            "Instalação interrompida: Fase 1 falhou.",
            "error"
        );

        button.disabled = false;

        return;
    }

    /*
     * Pequeno intervalo para permitir que
     * o payload termine sua inicialização.
     */
    log(
        "Aguardando inicialização do payload..."
    );

    await new Promise(
        function(resolve) {
            setTimeout(
                resolve,
                1000
            );
        }
    );

    const pkgOk =
        await registrarPkg(
            ip,
            pkgUrl
        );

    if (pkgOk) {

        log(
            "--------------------------------"
        );

        log(
            "PROCESSO CONCLUÍDO.",
            "success"
        );

    } else {

        log(
            "Fase 1 concluída, mas Fase 2 falhou.",
            "warning"
        );
    }

    button.disabled = false;
}

function escapeHtml(text) {

    return String(text)
        .replace(
            /&/g,
            "&amp;"
        )
        .replace(
            /</g,
            "&lt;"
        )
        .replace(
            />/g,
            "&gt;"
        )
        .replace(
            /"/g,
            "&quot;"
        )
        .replace(
            /'/g,
            "&#039;"
        );
}

window.addEventListener(
    "load",
    function() {

        carregarStatus();

        carregarPackages();

        setInterval(
            carregarStatus,
            3000
        );
    }
);

</script>

</body>

</html>
            """.trimIndent()

        val body =
            html.toByteArray(
                StandardCharsets.UTF_8
            )

        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType =
                "text/html; charset=utf-8",
            body = body,
            headOnly = headOnly,
            extraHeaders = mapOf(
                "Cache-Control" to "no-cache",
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to
                    "GET, HEAD, POST, OPTIONS",
                "Access-Control-Allow-Headers" to
                    "Content-Type"
            )
        )
    }

    private fun sendOptions(
        output: OutputStream
    ) {

        writeHeaders(
            output = output,
            statusCode = 204,
            statusText = "No Content",
            headers = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to
                    "GET, HEAD, POST, OPTIONS",
                "Access-Control-Allow-Headers" to
                    "Content-Type",
                "Content-Length" to "0"
            )
        )

        output.flush()
    }

    private fun sendJson(
        output: OutputStream,
        statusCode: Int,
        json: JSONObject
    ) {

        val body =
            json.toString()
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        sendResponse(
            output = output,
            statusCode = statusCode,
            statusText =
                if (statusCode in 200..299) {
                    "OK"
                } else {
                    "Error"
                },
            contentType =
                "application/json; charset=utf-8",
            body = body,
            headOnly = false,
            extraHeaders = mapOf(
                "Cache-Control" to "no-cache",
                "Access-Control-Allow-Origin" to "*"
            )
        )
    }

    private fun sendJsonError(
        output: OutputStream,
        statusCode: Int,
        message: String
    ) {

        sendJson(
            output = output,
            statusCode = statusCode,
            json =
                JSONObject()
                    .put(
                        "success",
                        false
                    )
                    .put(
                        "error",
                        message
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
        extraHeaders:
            Map<String, String> = emptyMap()
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
            "HTTP/1.1 "
        )

        builder.append(statusCode)

        builder.append(" ")

        builder.append(statusText)

        builder.append("\r\n")

        builder.append(
            "Server: GTSTORE\r\n"
        )

        builder.append(
            "Connection: close\r\n"
        )

        for (entry in headers) {

            builder.append(entry.key)
            builder.append(": ")
            builder.append(entry.value)
            builder.append("\r\n")
        }

        builder.append("\r\n")

        output.write(
            builder.toString()
                .toByteArray(
                    StandardCharsets.UTF_8
                )
        )
    }

    private fun sendError(
        output: OutputStream,
        statusCode: Int,
        message: String,
        extraHeaders:
            Map<String, String> = emptyMap()
    ) {

        val body =
            """
<!DOCTYPE html>
<html lang="pt-BR">
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
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        sendResponse(
            output = output,
            statusCode = statusCode,
            statusText = message,
            contentType =
                "text/html; charset=utf-8",
            body = body,
            headOnly = false,
            extraHeaders = extraHeaders
        )
    }

    private fun sendRangeError(
        output: OutputStream,
        totalSize: Long
    ) {

        sendError(
            output = output,
            statusCode = 416,
            message = "Range Not Satisfiable",
            extraHeaders = mapOf(
                "Content-Range" to
                    "bytes */$totalSize",

                "Accept-Ranges" to
                    "bytes",

                "Access-Control-Allow-Origin" to
                    "*"
            )
        )
    }

    private fun getWifiIpv4Address():
        Inet4Address? {

        return try {

            val interfaces =
                Collections.list(
                    NetworkInterface
                        .getNetworkInterfaces()
                )

            val candidates =
                mutableListOf<Inet4Address>()

            for (
                networkInterface
                in interfaces
            ) {

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
