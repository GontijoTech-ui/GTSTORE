package com.gtstore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors

class HttpServer(
    private val context: Context,
    private val port: Int = 8080
) {

    private var serverSocket: ServerSocket? = null

    // IMPORTANTE:
    // Deve permanecer público porque MainActivity e GTStoreService
    // utilizam httpServer.running.
    var running = false

    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var activeConnections = 0

    // IMPORTANTE:
    // MainActivity utiliza httpServer.localAddress.
    val localAddress: String
        get() = getWifiIpv4Address()?.hostAddress ?: "0.0.0.0"

    data class ServerStatus(
        val online: Boolean,
        val port: Int,
        val address: String,
        val url: String,
        val activeConnections: Int
    )

    private data class PkgInfo(
        val id: Int,
        val name: String,
        val fileName: String,
        val uri: Uri,
        val size: Long,
        val modified: Long
    )

    private data class ByteRange(
        val start: Long,
        val end: Long,
        val invalid: Boolean = false
    )

    // =========================================================
    // START
    // =========================================================

    fun start() {

        if (running) {
            return
        }

        executor.execute {

            try {

                val address = getWifiIpv4Address()

                if (address == null) {
                    running = false
                    return@execute
                }

                serverSocket = ServerSocket(
                    port,
                    50,
                    address
                )

                running = true

                while (running) {

                    try {

                        val client =
                            serverSocket?.accept()
                                ?: break

                        executor.execute {
                            handleClient(client)
                        }

                    } catch (_: Exception) {

                        if (!running) {
                            break
                        }
                    }
                }

            } catch (_: Exception) {

                running = false
            }
        }
    }

    // =========================================================
    // STOP
    // =========================================================

    fun stop() {

        running = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
    }

    // =========================================================
    // STATUS
    // =========================================================

    fun isRunning(): Boolean {
        return running
    }

    fun getStatus(): ServerStatus {

        val address =
            getWifiIpv4Address()?.hostAddress
                ?: "0.0.0.0"

        return ServerStatus(
            online = running,
            port = port,
            address = address,
            url = "http://$address:$port",
            activeConnections = activeConnections
        )
    }

    // =========================================================
    // CLIENT
    // =========================================================

    private fun handleClient(
        socket: Socket
    ) {

        activeConnections++

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
                    parts[0]
                        .uppercase(Locale.US)

                val target =
                    parts[1]

                val headers =
                    mutableMapOf<String, String>()

                while (true) {

                    val line =
                        input.readLine()
                            ?: break

                    if (line.isEmpty()) {
                        break
                    }

                    val separator =
                        line.indexOf(":")

                    if (separator > 0) {

                        val key =
                            line.substring(
                                0,
                                separator
                            )
                                .trim()
                                .lowercase(Locale.US)

                        val value =
                            line.substring(
                                separator + 1
                            )
                                .trim()

                        headers[key] = value
                    }
                }

                when (method) {

                    "GET" -> {

                        handleGet(
                            target,
                            headers,
                            output
                        )
                    }

                    "HEAD" -> {

                        handleHead(
                            target,
                            headers,
                            output
                        )
                    }

                    else -> {

                        sendResponse(
                            output = output,
                            status = 405,
                            statusText = "Method Not Allowed",
                            headers = mapOf(
                                "Allow" to "GET, HEAD",
                                "Content-Length" to "0",
                                "Connection" to "close"
                            )
                        )
                    }
                }

            } catch (_: Exception) {

                // Cliente encerrou a conexão ou ocorreu
                // algum erro de rede.
            }
        }

        activeConnections--
    }

    // =========================================================
    // GET
    // =========================================================

    private fun handleGet(
        target: String,
        headers: Map<String, String>,
        output: OutputStream
    ) {

        val uri = Uri.parse(target)

        when (uri.path ?: "/") {

            "/" -> {

                sendHomePage(output)
            }

            "/ps4" -> {

                sendPs4Page(output)
            }

            "/api/status" -> {

                sendStatusJson(output)
            }

            "/api/packages" -> {

                sendPackagesJson(output)
            }

            "/download" -> {

                val id =
                    uri.getQueryParameter("id")
                        ?.toIntOrNull()

                if (id == null) {

                    sendError(
                        output,
                        400,
                        "Missing package id"
                    )

                    return
                }

                val pkg =
                    getPackages()
                        .firstOrNull {
                            it.id == id
                        }

                if (pkg == null) {

                    sendError(
                        output,
                        404,
                        "Package not found"
                    )

                    return
                }

                servePackage(
                    pkg = pkg,
                    rangeHeader = headers["range"],
                    output = output,
                    headOnly = false
                )
            }

            "/pkg" -> {

                val id =
                    uri.getQueryParameter("id")
                        ?.toIntOrNull()

                if (id == null) {

                    sendError(
                        output,
                        400,
                        "Missing package id"
                    )

                    return
                }

                val pkg =
                    getPackages()
                        .firstOrNull {
                            it.id == id
                        }

                if (pkg == null) {

                    sendError(
                        output,
                        404,
                        "Package not found"
                    )

                    return
                }

                servePackage(
                    pkg = pkg,
                    rangeHeader = headers["range"],
                    output = output,
                    headOnly = false
                )
            }

            "/favicon.ico" -> {

                sendResponse(
                    output = output,
                    status = 204,
                    statusText = "No Content",
                    headers = mapOf(
                        "Content-Length" to "0",
                        "Connection" to "close"
                    )
                )
            }

            else -> {

                val path =
                    uri.path ?: ""

                if (path.startsWith("/pkg/")) {

                    val id =
                        extractPkgId(path)

                    if (id == null) {

                        sendError(
                            output,
                            404,
                            "Package not found"
                        )

                        return
                    }

                    val pkg =
                        getPackages()
                            .firstOrNull {
                                it.id == id
                            }

                    if (pkg == null) {

                        sendError(
                            output,
                            404,
                            "Package not found"
                        )

                        return
                    }

                    servePackage(
                        pkg = pkg,
                        rangeHeader = headers["range"],
                        output = output,
                        headOnly = false
                    )

                } else {

                    sendError(
                        output,
                        404,
                        "Not Found"
                    )
                }
            }
        }
    }

    // =========================================================
    // HEAD
    // =========================================================

    private fun handleHead(
        target: String,
        headers: Map<String, String>,
        output: OutputStream
    ) {

        val uri = Uri.parse(target)

        val path =
            uri.path ?: "/"

        if (
            path == "/download" ||
            path == "/pkg"
        ) {

            val id =
                uri.getQueryParameter("id")
                    ?.toIntOrNull()

            if (id == null) {

                sendError(
                    output,
                    400,
                    "Missing package id"
                )

                return
            }

            val pkg =
                getPackages()
                    .firstOrNull {
                        it.id == id
                    }

            if (pkg == null) {

                sendError(
                    output,
                    404,
                    "Package not found"
                )

                return
            }

            servePackage(
                pkg = pkg,
                rangeHeader = headers["range"],
                output = output,
                headOnly = true
            )

            return
        }

        if (path.startsWith("/pkg/")) {

            val id =
                extractPkgId(path)

            if (id == null) {

                sendError(
                    output,
                    404,
                    "Package not found"
                )

                return
            }

            val pkg =
                getPackages()
                    .firstOrNull {
                        it.id == id
                    }

            if (pkg == null) {

                sendError(
                    output,
                    404,
                    "Package not found"
                )

                return
            }

            servePackage(
                pkg = pkg,
                rangeHeader = headers["range"],
                output = output,
                headOnly = true
            )

            return
        }

        sendResponse(
            output = output,
            status = 200,
            statusText = "OK",
            headers = mapOf(
                "Content-Type" to
                        "text/html; charset=utf-8",

                "Content-Length" to "0",

                "Connection" to "close"
            )
        )
    }

    // =========================================================
    // SERVE PKG
    // =========================================================

    private fun servePackage(
        pkg: PkgInfo,
        rangeHeader: String?,
        output: OutputStream,
        headOnly: Boolean
    ) {

        val totalSize =
            pkg.size

        if (totalSize < 0) {

            sendError(
                output,
                500,
                "Invalid package size"
            )

            return
        }

        val range =
            parseRange(
                rangeHeader,
                totalSize
            )

        if (
            range != null &&
            range.invalid
        ) {

            sendResponse(
                output = output,
                status = 416,
                statusText = "Range Not Satisfiable",
                headers = mapOf(
                    "Content-Range" to
                            "bytes */$totalSize",

                    "Content-Length" to "0",

                    "Accept-Ranges" to "bytes",

                    "Connection" to "close",

                    "Cache-Control" to "no-store"
                )
            )

            return
        }

        val start =
            range?.start ?: 0L

        val end =
            range?.end
                ?: if (totalSize > 0) {
                    totalSize - 1
                } else {
                    0
                }

        val contentLength =
            if (totalSize <= 0) {
                0L
            } else {
                end - start + 1
            }

        val status =
            if (range != null) {
                206
            } else {
                200
            }

        val statusText =
            if (range != null) {
                "Partial Content"
            } else {
                "OK"
            }

        val filename =
            sanitizeFileName(
                pkg.fileName
            )

        val responseHeaders =
            linkedMapOf<String, String>()

        responseHeaders["Content-Type"] =
            "application/octet-stream"

        responseHeaders["Content-Length"] =
            contentLength.toString()

        responseHeaders["Accept-Ranges"] =
            "bytes"

        responseHeaders["Content-Disposition"] =
            "attachment; filename=\"$filename\""

        responseHeaders["Connection"] =
            "close"

        responseHeaders["Cache-Control"] =
            "no-store"

        responseHeaders["Access-Control-Allow-Origin"] =
            "*"

        if (range != null) {

            /*
             * IMPORTANTE:
             *
             * O tamanho total do PKG aparece depois da
             * barra.
             *
             * Exemplo:
             *
             * Content-Range:
             * bytes 1000-1999/5000000000
             */
            responseHeaders["Content-Range"] =
                "bytes $start-$end/$totalSize"
        }

        sendHeaders(
            output = output,
            status = status,
            statusText = statusText,
            headers = responseHeaders
        )

        if (headOnly) {

            output.flush()
            return
        }

        if (contentLength <= 0) {

            output.flush()
            return
        }

        var input: InputStream? = null

        try {

            val opened =
                context.contentResolver
                    .openInputStream(pkg.uri)
                    ?: throw IllegalStateException(
                        "Unable to open package"
                    )

            input =
                BufferedInputStream(
                    opened,
                    1024 * 1024
                )

            skipFully(
                input,
                start
            )

            val buffer =
                ByteArray(1024 * 1024)

            var remaining =
                contentLength

            while (remaining > 0) {

                val wanted =
                    minOf(
                        buffer.size.toLong(),
                        remaining
                    ).toInt()

                val read =
                    input.read(
                        buffer,
                        0,
                        wanted
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

            output.flush()

        } finally {

            try {
                input?.close()
            } catch (_: Exception) {
            }
        }
    }

    // =========================================================
    // RANGE PARSER
    // =========================================================

    private fun parseRange(
        header: String?,
        totalSize: Long
    ): ByteRange? {

        if (header.isNullOrBlank()) {
            return null
        }

        if (!header.startsWith("bytes=")) {

            return ByteRange(
                start = 0,
                end = 0,
                invalid = true
            )
        }

        val value =
            header
                .substringAfter("bytes=")
                .trim()

        // Não suportamos múltiplas faixas.
        if (value.contains(",")) {

            return ByteRange(
                start = 0,
                end = 0,
                invalid = true
            )
        }

        val parts =
            value.split(
                "-",
                limit = 2
            )

        if (parts.size != 2) {

            return ByteRange(
                start = 0,
                end = 0,
                invalid = true
            )
        }

        val startText =
            parts[0].trim()

        val endText =
            parts[1].trim()

        /*
         * Range de sufixo:
         *
         * bytes=-500
         *
         * significa os últimos 500 bytes.
         */

        if (startText.isEmpty()) {

            val suffixLength =
                endText.toLongOrNull()

            if (
                suffixLength == null ||
                suffixLength <= 0 ||
                totalSize <= 0
            ) {

                return ByteRange(
                    start = 0,
                    end = 0,
                    invalid = true
                )
            }

            val actualLength =
                minOf(
                    suffixLength,
                    totalSize
                )

            return ByteRange(
                start =
                    totalSize - actualLength,

                end =
                    totalSize - 1
            )
        }

        val start =
            startText.toLongOrNull()

        if (
            start == null ||
            start < 0
        ) {

            return ByteRange(
                start = 0,
                end = 0,
                invalid = true
            )
        }

        if (start >= totalSize) {

            return ByteRange(
                start = 0,
                end = 0,
                invalid = true
            )
        }

        val end =
            if (endText.isEmpty()) {

                totalSize - 1

            } else {

                val requestedEnd =
                    endText.toLongOrNull()

                if (
                    requestedEnd == null ||
                    requestedEnd < start
                ) {

                    return ByteRange(
                        start = 0,
                        end = 0,
                        invalid = true
                    )
                }

                minOf(
                    requestedEnd,
                    totalSize - 1
                )
            }

        return ByteRange(
            start = start,
            end = end
        )
    }

    // =========================================================
    // SKIP INPUT
    // =========================================================

    private fun skipFully(
        input: InputStream,
        amount: Long
    ) {

        var remaining =
            amount

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

                throw IllegalStateException(
                    "Unable to seek to requested range"
                )
            }

            remaining--
        }
    }

    // =========================================================
    // PACKAGE ID
    // =========================================================

    private fun extractPkgId(
        path: String
    ): Int? {

        /*
         * Aceita:
         *
         * /pkg/0
         *
         * /pkg/0/Jogo.pkg
         */

        val value =
            path.removePrefix("/pkg/")

        val parts =
            value.split("/")

        return parts
            .firstOrNull()
            ?.toIntOrNull()
    }

    // =========================================================
    // GET PACKAGES
    // =========================================================

    private fun getPackages(): List<PkgInfo> {

        val preferences =
            context.getSharedPreferences(
                "GTSTORE",
                Context.MODE_PRIVATE
            )

        var uriString =
            preferences.getString(
                "pkg_folder_uri",
                null
            )

        if (uriString.isNullOrBlank()) {

            uriString =
                context
                    .getSharedPreferences(
                        context.packageName +
                                "_preferences",
                        Context.MODE_PRIVATE
                    )
                    .getString(
                        "pkg_folder_uri",
                        null
                    )
        }

        if (uriString.isNullOrBlank()) {
            return emptyList()
        }

        val treeUri =
            try {
                Uri.parse(uriString)
            } catch (_: Exception) {
                return emptyList()
            }

        val root =
            try {
                DocumentFile.fromTreeUri(
                    context,
                    treeUri
                )
            } catch (_: Exception) {
                null
            }
                ?: return emptyList()

        val result =
            mutableListOf<PkgInfo>()

        scanDirectory(
            directory = root,
            result = result
        )

        return result
            .sortedBy {
                it.fileName
                    .lowercase(Locale.getDefault())
            }
            .mapIndexed { index, pkg ->
                pkg.copy(
                    id = index
                )
            }
    }

    // =========================================================
    // SCAN DIRECTORY
    // =========================================================

    private fun scanDirectory(
        directory: DocumentFile,
        result: MutableList<PkgInfo>
    ) {

        val children =
            try {
                directory.listFiles()
            } catch (_: Exception) {
                emptyArray()
            }

        for (file in children) {

            try {

                if (file.isDirectory) {

                    scanDirectory(
                        directory = file,
                        result = result
                    )

                } else if (file.isFile) {

                    val name =
                        file.name
                            ?: continue

                    if (
                        name.lowercase(
                            Locale.getDefault()
                        ).endsWith(".pkg")
                    ) {

                        result.add(
                            PkgInfo(
                                id = -1,

                                name =
                                    name.removeSuffix(
                                        ".pkg"
                                    ),

                                fileName =
                                    name,

                                uri =
                                    file.uri,

                                size =
                                    file.length(),

                                modified =
                                    file.lastModified()
                            )
                        )
                    }
                }

            } catch (_: Exception) {
                // Ignora arquivos que não puderam ser lidos.
            }
        }
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun sendHomePage(
        output: OutputStream
    ) {

        val status =
            getStatus()

        val html =
            """
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

                <p>
                    Servidor:
                    ${if (status.online) "ONLINE" else "OFFLINE"}
                </p>

                <p>
                    Endereço:
                    ${status.url}
                </p>

                <p>
                    Porta:
                    ${status.port}
                </p>

                <p>
                    Conexões:
                    ${status.activeConnections}
                </p>

                <p>
                    <a href="/ps4">
                        Página PS4
                    </a>
                </p>

                <p>
                    <a href="/api/packages">
                        API Packages
                    </a>
                </p>

                <p>
                    <a href="/api/status">
                        API Status
                    </a>
                </p>

            </body>
            </html>
            """.trimIndent()

        sendText(
            output,
            html
        )
    }

    // =========================================================
    // PS4 PAGE
    // =========================================================

    private fun sendPs4Page(
        output: OutputStream
    ) {

        val packages =
            getPackages()

        val builder =
            StringBuilder()

        builder.append(
            """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0">
                <title>GTSTORE PS4</title>
            </head>
            <body>

            <h1>GTSTORE - PKGs</h1>
            """.trimIndent()
        )

        for (pkg in packages) {

            val url =
                "/pkg/${pkg.id}/${Uri.encode(pkg.fileName)}"

            builder.append(
                """
                <div>

                    <h3>
                        ${escapeHtml(pkg.name)}
                    </h3>

                    <p>
                        ${formatFileSize(pkg.size)}
                    </p>

                    <p>
                        <a href="$url">
                            Download / Install
                        </a>
                    </p>

                </div>

                <hr>

                """.trimIndent()
            )
        }

        builder.append(
            """
            </body>
            </html>
            """.trimIndent()
        )

        sendText(
            output,
            builder.toString()
        )
    }

    // =========================================================
    // API STATUS
    // =========================================================

    private fun sendStatusJson(
        output: OutputStream
    ) {

        val status =
            getStatus()

        val json =
            JSONObject()

        json.put(
            "online",
            status.online
        )

        json.put(
            "port",
            status.port
        )

        json.put(
            "address",
            status.address
        )

        json.put(
            "url",
            status.url
        )

        json.put(
            "activeConnections",
            status.activeConnections
        )

        sendJson(
            output,
            json.toString()
        )
    }

    // =========================================================
    // API PACKAGES
    // =========================================================

    private fun sendPackagesJson(
        output: OutputStream
    ) {

        val array =
            JSONArray()

        for (pkg in getPackages()) {

            val json =
                JSONObject()

            json.put(
                "id",
                pkg.id
            )

            json.put(
                "name",
                pkg.name
            )

            json.put(
                "file",
                pkg.fileName
            )

            json.put(
                "size",
                pkg.size
            )

            json.put(
                "size_formatted",
                formatFileSize(pkg.size)
            )

            json.put(
                "modified",
                pkg.modified
            )

            json.put(
                "type",
                "PKG"
            )

            json.put(
                "url",
                "/pkg/${pkg.id}/${Uri.encode(pkg.fileName)}"
            )

            array.put(json)
        }

        sendJson(
            output,
            array.toString()
        )
    }

    // =========================================================
    // TEXT RESPONSE
    // =========================================================

    private fun sendText(
        output: OutputStream,
        text: String
    ) {

        val body =
            text.toByteArray(
                StandardCharsets.UTF_8
            )

        sendHeaders(
            output = output,
            status = 200,
            statusText = "OK",
            headers = mapOf(

                "Content-Type" to
                        "text/html; charset=utf-8",

                "Content-Length" to
                        body.size.toString(),

                "Connection" to
                        "close",

                "Cache-Control" to
                        "no-store"
            )
        )

        output.write(body)
        output.flush()
    }

    // =========================================================
    // JSON RESPONSE
    // =========================================================

    private fun sendJson(
        output: OutputStream,
        json: String
    ) {

        val body =
            json.toByteArray(
                StandardCharsets.UTF_8
            )

        sendHeaders(
            output = output,
            status = 200,
            statusText = "OK",
            headers = mapOf(

                "Content-Type" to
                        "application/json; charset=utf-8",

                "Content-Length" to
                        body.size.toString(),

                "Connection" to
                        "close",

                "Cache-Control" to
                        "no-store",

                "Access-Control-Allow-Origin" to
                        "*"
            )
        )

        output.write(body)
        output.flush()
    }

    // =========================================================
    // ERROR
    // =========================================================

    private fun sendError(
        output: OutputStream,
        status: Int,
        message: String
    ) {

        val body =
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>$status</title>
            </head>
            <body>

                <h1>$status</h1>

                <p>
                    ${escapeHtml(message)}
                </p>

            </body>
            </html>
            """.trimIndent()
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        sendHeaders(
            output = output,
            status = status,
            statusText = message,
            headers = mapOf(

                "Content-Type" to
                        "text/html; charset=utf-8",

                "Content-Length" to
                        body.size.toString(),

                "Connection" to
                        "close"
            )
        )

        output.write(body)
        output.flush()
    }

    // =========================================================
    // EMPTY RESPONSE
    // =========================================================

    private fun sendResponse(
        output: OutputStream,
        status: Int,
        statusText: String,
        headers: Map<String, String>
    ) {

        sendHeaders(
            output = output,
            status = status,
            statusText = statusText,
            headers = headers
        )

        output.flush()
    }

    // =========================================================
    // HTTP HEADERS
    // =========================================================

    private fun sendHeaders(
        output: OutputStream,
        status: Int,
        statusText: String,
        headers: Map<String, String>
    ) {

        val builder =
            StringBuilder()

        builder.append(
            "HTTP/1.1 $status $statusText\r\n"
        )

        for ((key, value) in headers) {

            builder.append(
                "$key: $value\r\n"
            )
        }

        builder.append(
            "\r\n"
        )

        output.write(
            builder.toString()
                .toByteArray(
                    StandardCharsets.ISO_8859_1
                )
        )
    }

    // =========================================================
    // NETWORK ADDRESS
    // =========================================================

    private fun getWifiIpv4Address():
        Inet4Address? {

        return try {

            val interfaces =
                java.net.NetworkInterface
                    .getNetworkInterfaces()

            while (
                interfaces.hasMoreElements()
            ) {

                val networkInterface =
                    interfaces.nextElement()

                if (
                    !networkInterface.isUp
                ) {
                    continue
                }

                if (
                    networkInterface.isLoopback
                ) {
                    continue
                }

                val addresses =
                    networkInterface
                        .inetAddresses

                while (
                    addresses.hasMoreElements()
                ) {

                    val address =
                        addresses.nextElement()

                    if (
                        address is Inet4Address &&
                        !address.isLoopbackAddress
                    ) {

                        return address
                    }
                }
            }

            null

        } catch (_: Exception) {

            null
        }
    }

    // =========================================================
    // SANITIZE FILE NAME
    // =========================================================

    private fun sanitizeFileName(
        name: String
    ): String {

        return name
            .replace("\\", "_")
            .replace("\"", "_")
            .replace("\r", "_")
            .replace("\n", "_")
            .replace("/", "_")
    }

    // =========================================================
    // HTML ESCAPE
    // =========================================================

    private fun escapeHtml(
        value: String
    ): String {

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    // =========================================================
    // FILE SIZE
    // =========================================================

    private fun formatFileSize(
        size: Long
    ): String {

        if (size <= 0) {
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
            size.toDouble()

        var index = 0

        while (
            value >= 1024 &&
            index < units.size - 1
        ) {

            value /= 1024
            index++
        }

        return String.format(
            Locale.US,
            "%.2f %s",
            value,
            units[index]
        )
    }
}
