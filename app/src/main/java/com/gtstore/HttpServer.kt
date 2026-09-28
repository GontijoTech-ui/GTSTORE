package com.gtstore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        private const val SOCKET_TIMEOUT_MS = 60_000
        private const val PAYLOAD_DIR = "payloads"

        private val ALLOWED_PAYLOADS = setOf(
            "rpi_installer.bin",
            "direct-installer.bin",
            "ps4-rpi.bin",
            "payload.bin"
        )
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
        if (running) return

        executor.execute {
            try {
                val address = getWifiIpv4Address()
                serverSocket = if (address != null) {
                    ServerSocket(port, 100, address)
                } else {
                    ServerSocket(port, 100)
                }
                serverSocket?.reuseAddress = true
                running = true

                while (running) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        executor.execute { handleClient(socket) }
                    } catch (_: Exception) {
                        if (!running) break
                    }
                }
            } catch (_: Exception) {
                running = false
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    fun isRunning(): Boolean = running

    fun getStatus(): ServerStatus {
        val address = localAddress
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

        val clientIp = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: ""

        socket.use { client ->
            var target = ""
            try {
                client.soTimeout = SOCKET_TIMEOUT_MS
                client.tcpNoDelay = true

                val input = BufferedReader(
                    InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1)
                )
                val output = BufferedOutputStream(client.getOutputStream(), BUFFER_SIZE)

                val requestLine = input.readLine() ?: return
                if (requestLine.length > MAX_HEADER_SIZE) {
                    sendError(output, 431, "Request Header Fields Too Large")
                    return
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    sendError(output, 400, "Bad Request")
                    return
                }

                val method = parts[0].uppercase()
                target = parts[1]
                val headers = HashMap<String, String>()

                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        val name = line.substring(0, separator).trim().lowercase()
                        val value = line.substring(separator + 1).trim()
                        headers[name] = value
                    }
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength < 0 || contentLength > MAX_POST_SIZE) {
                    sendError(output, 413, "Request Entity Too Large")
                    return
                }

                // CORREÇÃO: o BufferedReader já consumiu parte (ou todo) o corpo do socket.
                // O corpo precisa ser lido do MESMO reader. Como o charset é ISO_8859_1,
                // 1 char = 1 byte, então reconvertemos para os bytes originais.
                val body = if (method == "POST" && contentLength > 0) {
                    val chars = CharArray(contentLength)
                    var off = 0
                    while (off < contentLength) {
                        val n = input.read(chars, off, contentLength - off)
                        if (n <= 0) break
                        off += n
                    }
                    String(chars, 0, off).toByteArray(StandardCharsets.ISO_8859_1)
                } else {
                    ByteArray(0)
                }

                when (method) {
                    "GET" -> handleRequest(target, headers, output, headOnly = false, clientIp)
                    "HEAD" -> handleRequest(target, headers, output, headOnly = true, clientIp)
                    "POST" -> handlePostRequest(target, headers, body, output)
                    "OPTIONS" -> sendOptions(output)
                    else -> sendError(output, 405, "Method Not Allowed", mapOf("Allow" to "GET, HEAD, POST, OPTIONS"))
                }
            } catch (_: SocketException) {
            } catch (_: Exception) {
                try {
                    val out = client.getOutputStream()
                    // Rotas /api sempre respondem JSON, para o JS da página conseguir ler o erro.
                    if (target.startsWith("/api/")) {
                        sendJsonError(out, 500, "Erro interno do servidor (timeout ou falha ao ler a requisição).")
                    } else {
                        sendError(out, 500, "Internal Server Error")
                    }
                } catch (_: Exception) {}
            } finally {
                activeConnections.decrementAndGet()
            }
        }
    }

    private fun handlePostRequest(target: String, headers: Map<String, String>, body: ByteArray, output: OutputStream) {
        val uri = Uri.parse(target)
        val path = uri.path ?: "/"

        when (path) {
            "/api/send-payload" -> handleSendPayload(body, output)
            "/api/install" -> handleRemoteInstall(body, output)
            "/api/install-dpi" -> handleDirectInstallDpi(body, headers, output)
            else -> sendJsonError(output, 404, "POST endpoint not found")
        }
    }

    // --- Lógica do Direct Package Installer com RASTREIO DE ERROS ---
    private fun handleDirectInstallDpi(body: ByteArray, headers: Map<String, String>, output: OutputStream) {
        try {
            val json = JSONObject(String(body, StandardCharsets.UTF_8))
            val ps4Ip = json.optString("ip").trim()
            val pkgId = json.optInt("pkgId", -1)

            if (!isValidIp(ps4Ip) || pkgId == -1) {
                sendJsonError(output, 400, "[PASSO 1] IP do PS4 ou ID do PKG inválido.")
                return
            }

            val packageInfo = getPackages().firstOrNull { it.id == pkgId }
            if (packageInfo == null) {
                sendJsonError(output, 404, "[PASSO 2] Pacote não encontrado na lista.")
                return
            }

            // Metadados reais do PKG (TITLE, CONTENT_ID, CATEGORY, ícone).
            val meta = PkgMetaReader.read(context, packageInfo.uri)
            if (meta == null) {
                sendJsonError(output, 422, "[PASSO 2b] Não consegui ler o param.sfo deste PKG (${packageInfo.fileName}).")
                return
            }

            val payloadTemplate = loadPayload("payload.bin") ?: loadPayload("direct-installer.bin")
            if (payloadTemplate == null) {
                sendJsonError(output, 500, "[PASSO 3] Ficheiro payload.bin não encontrado na pasta assets/payloads.")
                return
            }

            val payload = payloadTemplate.copyOf()
            val off = indexOf(payload, byteArrayOf(0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte()))
            if (off < 0) {
                sendJsonError(output, 500, "[PASSO 4] Marcador B4 não encontrado. O payload.bin não é o correto do DPI.")
                return
            }

            // IP do telemóvel como o PS4 o enxerga (o mesmo que ele usou para abrir a página).
            val localIp = requestHost(headers)
            if (localIp == "0.0.0.0" || localIp.isEmpty()) {
                sendJsonError(output, 500, "[PASSO 5] O telemóvel não conseguiu ler o seu próprio IP. (Desligue os dados móveis 4G/5G).")
                return
            }

            val manifestUrl = "http://$localIp:$port/json/${packageInfo.id}.json"
            val localAddr = java.net.InetAddress.getByName(localIp)

            try {
                ServerSocket(0, 5, localAddr).use { tempServer ->
                    tempServer.soTimeout = 15_000 // Aguarda 15 segundos pelo PS4
                    val callbackPort = tempServer.localPort

                    localAddr.address.copyInto(payload, off)
                    payload[off + 4] = (callbackPort ushr 8).toByte()
                    payload[off + 5] = callbackPort.toByte()

                    val binResult = sendPayloadToBinLoader(ps4Ip, payload)
                    if (!binResult.success) {
                        sendJsonError(output, 502, "[PASSO 6] Falha ao enviar para o BinLoader (Porta 9090). O BinLoader está ativo no GoldHEN?\nDetalhe técnico: ${binResult.error}")
                        return
                    }

                    try {
                        tempServer.accept().use { ps4Client ->
                            ps4Client.getOutputStream().apply {
                                write(buildDpiInfo(manifestUrl, packageInfo, meta))
                                flush()
                            }
                        }
                        sendJson(output, 200, JSONObject().put("success", true).put("message", "Instalação DPI iniciada no PS4!"))
                    } catch (e: Exception) {
                        sendJsonError(output, 504, "[PASSO 7] Timeout: O PS4 recebeu o payload, mas não ligou de volta ao telemóvel (Porta $callbackPort).\nDetalhe técnico: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                sendJsonError(output, 500, "[PASSO 8] Falha ao abrir a porta de escuta no telemóvel.\nDetalhe técnico: ${e.message}")
            }
        } catch (e: Exception) {
            sendJsonError(output, 400, "[PASSO 9] Erro geral de execução.\nDetalhe técnico: ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    /** Tudo em little-endian: 1u, URL, nome, ContentID, tipo, tamanho (int64), ícone. */
    private fun buildDpiInfo(url: String, info: PackageInfo, meta: PkgMetaReader.Meta): ByteArray {
        val out = ByteArrayOutputStream()
        fun i32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun str(s: String) { val b = s.toByteArray(StandardCharsets.UTF_8); i32(b.size); out.write(b) }

        i32(1)
        str(url)
        str(meta.title)
        str(meta.contentId)
        str(meta.bgftType) // PS4GD = jogo, PS4GP = update, PS4AC = DLC
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(info.size).array())
        val icon = meta.icon
        if (icon == null || icon.isEmpty()) i32(0) else { i32(icon.size); out.write(icon) }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        for (i in 0..data.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) { match = false; break }
            }
            if (match) return i
        }
        return -1
    }

    /** IPv4 usado pelo cliente para chegar até aqui (cabeçalho Host); cai para a detecção por interface. */
    private fun requestHost(headers: Map<String, String>): String {
        val host = headers["host"]?.substringBefore(':')?.trim()
        return if (!host.isNullOrEmpty() && isValidIp(host)) host else localAddress
    }

    private fun handleSendPayload(body: ByteArray, output: OutputStream) {
        try {
            val json = JSONObject(String(body, StandardCharsets.UTF_8))
            val ip = json.optString("ip").trim()
            val payloadName = json.optString("payloadName").trim()

            if (!isValidIp(ip)) {
                sendJsonError(output, 400, "IP da PS4 inválido.")
                return
            }
            if (!ALLOWED_PAYLOADS.contains(payloadName)) {
                sendJsonError(output, 400, "Payload não autorizado.")
                return
            }

            val payload = loadPayload(payloadName)
            if (payload == null) {
                sendJsonError(output, 404, "Payload não encontrado em assets/$PAYLOAD_DIR.")
                return
            }

            val result = sendPayloadToBinLoader(ip, payload)
            if (result.success) {
                sendJson(output, 200, JSONObject().put("success", true).put("message", "Payload enviado para $ip:9090.").put("bytes", payload.size))
            } else {
                sendJson(output, 500, JSONObject().put("success", false).put("error", result.error))
            }
        } catch (e: Exception) {
            sendJsonError(output, 400, "JSON inválido: ${e.message}")
        }
    }

    private data class PayloadResult(val success: Boolean, val error: String? = null)

    private fun sendPayloadToBinLoader(ip: String, payload: ByteArray): PayloadResult {
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = 8000
                socket.connect(InetSocketAddress(ip, 9090), 5000)

                val out = socket.getOutputStream()
                out.write(payload)
                out.flush()

                try { socket.shutdownOutput() } catch (_: Exception) {}
            }
            PayloadResult(success = true)
        } catch (e: Exception) {
            PayloadResult(success = false, error = "Falha TCP 9090: ${e.message}")
        }
    }

    private fun loadPayload(payloadName: String): ByteArray? {
        return try {
            context.assets.open("$PAYLOAD_DIR/$payloadName").use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun handleRemoteInstall(body: ByteArray, output: OutputStream) {
        try {
            val json = JSONObject(String(body, StandardCharsets.UTF_8))
            val ip = json.optString("ip").trim()
            val pkgUrl = json.optString("pkgUrl").trim()

            if (!isValidIp(ip)) {
                sendJsonError(output, 400, "IP da PS4 inválido.")
                return
            }
            if (pkgUrl.isBlank()) {
                sendJsonError(output, 400, "URL do PKG ausente.")
                return
            }

            val result = sendInstallRequestToPs4(ip, pkgUrl)
            if (result.success) {
                sendJson(output, 200, JSONObject().put("success", true).put("message", "A PS4 aceitou a solicitação de instalação.").put("response", result.response))
            } else {
                sendJson(output, 502, JSONObject().put("success", false).put("error", result.error))
            }
        } catch (e: Exception) {
            sendJsonError(output, 400, "JSON inválido: ${e.message}")
        }
    }

    private data class InstallResult(val success: Boolean, val response: String = "", val error: String? = null)

    private fun sendInstallRequestToPs4(ip: String, pkgUrl: String): InstallResult {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 8000
                socket.connect(InetSocketAddress(ip, 12800), 5000)

                val body = JSONObject().put("type", "direct").put("packages", JSONArray().put(pkgUrl)).toString()
                val requestBytes = body.toByteArray(StandardCharsets.UTF_8)

                val request = "POST /api/install HTTP/1.1\r\n" +
                        "Host: $ip:12800\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${requestBytes.size}\r\n" +
                        "Connection: close\r\n\r\n" +
                        body

                val out = socket.getOutputStream()
                out.write(request.toByteArray(StandardCharsets.UTF_8))
                out.flush()

                val response = readHttpResponse(socket.getInputStream())
                if (response.first in 200..299) {
                    InstallResult(success = true, response = response.second)
                } else {
                    InstallResult(success = false, response = response.second, error = "A PS4 respondeu HTTP ${response.first}")
                }
            }
        } catch (e: Exception) {
            InstallResult(success = false, error = "Falha ao comunicar com PS4:12800 (${e.message})")
        }
    }

    private fun readHttpResponse(input: InputStream): Pair<Int, String> {
        val headerBuffer = ByteArrayOutputStream()
        val marker = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        var matched = 0

        while (headerBuffer.size() < MAX_HEADER_SIZE) {
            val value = input.read()
            if (value == -1) break
            headerBuffer.write(value)

            if (value.toByte() == marker[matched]) {
                matched++
                if (matched == 4) break
            } else {
                matched = if (value.toByte() == marker[0]) 1 else 0
            }
        }

        val headersText = String(headerBuffer.toByteArray(), StandardCharsets.ISO_8859_1)
        val firstLine = headersText.lineSequence().firstOrNull() ?: ""
        val status = firstLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0

        val contentLength = headersText.lineSequence()
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull() ?: 0

        val body = if (contentLength in 1..MAX_POST_SIZE) {
            val bytes = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(bytes, offset, contentLength - offset)
                if (read <= 0) break
                offset += read
            }
            String(bytes, 0, offset, StandardCharsets.UTF_8)
        } else {
            ""
        }
        return Pair(status, body)
    }

    private fun isValidIp(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return try { parts.all { it.toInt() in 0..255 } } catch (_: Exception) { false }
    }

    private fun handleRequest(target: String, headers: Map<String, String>, output: OutputStream, headOnly: Boolean, clientIp: String) {
        val uri = Uri.parse(target)
        val path = uri.path ?: "/"

        when {
            path == "/" || path == "/ps4" -> sendHomePage(output, headOnly)
            path == "/api/status" -> sendStatusJson(output, headOnly, clientIp)
            path == "/api/packages" -> sendPackagesJson(output, headOnly)
            path.startsWith("/json/") -> {
                val id = path.removePrefix("/json/").removeSuffix(".json").toIntOrNull()
                if (id != null) sendManifestJson(id, headers, output, headOnly) else sendError(output, 404, "Not Found")
            }
            path == "/download" || path == "/pkg" -> {
                val id = uri.getQueryParameter("id")?.toIntOrNull()
                if (id == null) {
                    sendError(output, 400, "Invalid package id")
                    return
                }
                servePackage(id, headers, output, headOnly)
            }
            path.startsWith("/pkg/") -> {
                val id = path.removePrefix("/pkg/").split("/").firstOrNull()?.toIntOrNull()
                if (id == null) {
                    sendError(output, 400, "Invalid package id")
                    return
                }
                servePackage(id, headers, output, headOnly)
            }
            path == "/favicon.ico" -> sendResponse(output, 204, "No Content", "image/x-icon", ByteArray(0), headOnly)
            else -> sendError(output, 404, "Not Found")
        }
    }

    private fun sendManifestJson(packageId: Int, headers: Map<String, String>, output: OutputStream, headOnly: Boolean) {
        val packageInfo = getPackages().firstOrNull { it.id == packageId }
        if (packageInfo == null) {
            sendError(output, 404, "Package Not Found")
            return
        }
        val fileUrl = "http://${requestHost(headers)}:$port/pkg/${packageInfo.id}"
        val json = """{"originalFileSize":${packageInfo.size},"packageDigest":"0000000000000000000000000000000000000000","numberOfSplitFiles":1,"pieces":[{"url":"$fileUrl","fileOffset":0,"fileSize":${packageInfo.size},"hashValue":"0000000000000000000000000000000000000000"}]}"""
        sendResponse(output, 200, "OK", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8), headOnly, mapOf("Access-Control-Allow-Origin" to "*"))
    }

    private fun servePackage(packageId: Int, headers: Map<String, String>, output: OutputStream, headOnly: Boolean) {
        val packageInfo = getPackages().firstOrNull { it.id == packageId }
        if (packageInfo == null) {
            sendError(output, 404, "Package Not Found")
            return
        }

        val totalSize = packageInfo.size
        var start = 0L
        var end = if (totalSize > 0) totalSize - 1 else 0L
        var partial = false
        val rangeHeader = headers["range"]

        if (!rangeHeader.isNullOrBlank()) {
            if (!rangeHeader.startsWith("bytes=")) {
                sendRangeError(output, totalSize)
                return
            }

            val ranges = rangeHeader.removePrefix("bytes=").split(",")
            if (ranges.size != 1) {
                sendRangeError(output, totalSize)
                return
            }

            val range = ranges[0].trim()
            try {
                when {
                    range.startsWith("-") -> {
                        val suffixLength = range.removePrefix("-").toLong()
                        if (suffixLength <= 0 || totalSize <= 0) {
                            sendRangeError(output, totalSize)
                            return
                        }
                        start = if (suffixLength >= totalSize) 0L else totalSize - suffixLength
                        end = totalSize - 1
                    }
                    range.endsWith("-") -> {
                        start = range.removeSuffix("-").toLong()
                        if (start < 0 || start >= totalSize) {
                            sendRangeError(output, totalSize)
                            return
                        }
                        end = totalSize - 1
                    }
                    else -> {
                        val parts = range.split("-", limit = 2)
                        if (parts.size != 2) {
                            sendRangeError(output, totalSize)
                            return
                        }
                        start = parts[0].toLong()
                        end = parts[1].toLong()
                        if (start < 0 || start >= totalSize || end < start) {
                            sendRangeError(output, totalSize)
                            return
                        }
                        if (end >= totalSize) end = totalSize - 1
                    }
                }
                partial = true
            } catch (_: Exception) {
                sendRangeError(output, totalSize)
                return
            }
        }

        val contentLength = if (totalSize == 0L) 0L else end - start + 1
        val statusCode = if (partial) 206 else 200
        val statusText = if (partial) "Partial Content" else "OK"

        val responseHeaders = LinkedHashMap<String, String>()
        responseHeaders["Accept-Ranges"] = "none"
        responseHeaders["Content-Length"] = contentLength.toString()
        responseHeaders["Content-Type"] = "application/octet-stream"
        responseHeaders["Content-Disposition"] = "attachment; filename=\"${sanitizeFileName(packageInfo.fileName)}\""
        responseHeaders["Cache-Control"] = "no-cache"
        responseHeaders["Access-Control-Allow-Origin"] = "*"
        responseHeaders["Access-Control-Expose-Headers"] = "Content-Length, Content-Range, Accept-Ranges"

        if (partial) {
            responseHeaders["Content-Range"] = "bytes $start-$end/$totalSize"
        }

        writeHeaders(output, statusCode, statusText, responseHeaders)

        if (headOnly || contentLength <= 0L) {
            output.flush()
            return
        }

        try {
            context.contentResolver.openFileDescriptor(packageInfo.uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    fis.channel.position(start)
                    streamRange(fis, output, contentLength)
                }
            }
        } catch (_: SocketException) {
        } catch (_: Exception) {}

        try { output.flush() } catch (_: Exception) {}
    }

    private fun streamRange(input: InputStream, output: OutputStream, bytesToSend: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = bytesToSend

        while (remaining > 0) {
            val requested = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, requested)
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun getPackages(): List<PackageInfo> {
        val preferences = context.getSharedPreferences("GTSTORE", Context.MODE_PRIVATE)
        val savedUri = preferences.getString("pkg_folder_uri", null)
            ?: context.getSharedPreferences("GTSTORE_PREFS", Context.MODE_PRIVATE)
                .getString("pkg_folder_uri", null)

        if (savedUri.isNullOrBlank()) return emptyList()

        val root = try {
            DocumentFile.fromTreeUri(context, Uri.parse(savedUri))
        } catch (_: Exception) { null }

        if (root == null || !root.exists()) return emptyList()

        val files = mutableListOf<DocumentFile>()
        scanDocumentFile(root, files)

        return files.filter { it.isFile && it.name?.lowercase()?.endsWith(".pkg") == true }
            .sortedBy { it.name?.lowercase() ?: "" }
            .mapIndexed { index, file ->
                val name = file.name ?: "package.pkg"
                PackageInfo(
                    id = index + 1, name = name, fileName = name,
                    size = file.length(), modified = file.lastModified(),
                    type = "PKG", uri = file.uri
                )
            }
    }

    private fun scanDocumentFile(root: DocumentFile, result: MutableList<DocumentFile>) {
        val children = try { root.listFiles() } catch (_: Exception) { emptyArray() }
        for (child in children) {
            if (child.isDirectory) {
                scanDocumentFile(child, result)
            } else {
                result.add(child)
            }
        }
    }

    private fun sendPackagesJson(output: OutputStream, headOnly: Boolean) {
        val packages = getPackages()
        val array = JSONArray()
        for (pkg in packages) {
            val item = JSONObject()
                .put("id", pkg.id).put("name", pkg.name).put("file", pkg.fileName)
                .put("size", pkg.size).put("formattedSize", formatFileSize(pkg.size))
                .put("modified", pkg.modified).put("type", pkg.type)
                .put("url", "/pkg/${pkg.id}").put("download", "/download?id=${pkg.id}")
            array.put(item)
        }
        val json = JSONObject().put("count", packages.size).put("packages", array)
        val body = json.toString().toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, 200, "OK", "application/json; charset=utf-8", body, headOnly, mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*"))
    }

    private fun sendStatusJson(output: OutputStream, headOnly: Boolean, clientIp: String) {
        val status = getStatus()
        val json = JSONObject()
            .put("online", status.running).put("running", status.running).put("port", status.port)
            .put("address", status.localAddress).put("localAddress", status.localAddress)
            .put("url", status.url).put("activeConnections", status.activeConnections)
            .put("clientIp", clientIp)

        val body = json.toString().toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, 200, "OK", "application/json; charset=utf-8", body, headOnly, mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*"))
    }

    private fun sendHomePage(output: OutputStream, headOnly: Boolean) {
        try {
            context.assets.open("index.html").use { input ->
                val body = input.readBytes()
                sendResponse(
                    output = output,
                    statusCode = 200,
                    statusText = "OK",
                    contentType = "text/html; charset=utf-8",
                    body = body,
                    headOnly = headOnly,
                    extraHeaders = mapOf(
                        "Cache-Control" to "no-cache",
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Methods" to "GET, HEAD, POST, OPTIONS",
                        "Access-Control-Allow-Headers" to "Content-Type"
                    )
                )
            }
        } catch (e: Exception) {
            sendError(output, 404, "Ficheiro index.html não encontrado na pasta assets.")
        }
    }

    private fun sendOptions(output: OutputStream) {
        writeHeaders(output, 204, "No Content", mapOf("Access-Control-Allow-Origin" to "*", "Access-Control-Allow-Methods" to "GET, HEAD, POST, OPTIONS", "Access-Control-Allow-Headers" to "Content-Type", "Content-Length" to "0"))
        output.flush()
    }

    private fun sendJson(output: OutputStream, statusCode: Int, json: JSONObject) {
        val body = json.toString().toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, statusCode, if (statusCode in 200..299) "OK" else "Error", "application/json; charset=utf-8", body, false, mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*"))
    }

    private fun sendJsonError(output: OutputStream, statusCode: Int, message: String) {
        sendJson(output, statusCode, JSONObject().put("success", false).put("error", message))
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, statusText: String, contentType: String, body: ByteArray, headOnly: Boolean, extraHeaders: Map<String, String> = emptyMap()) {
        val headers = LinkedHashMap<String, String>()
        headers["Content-Type"] = contentType
        headers["Content-Length"] = body.size.toString()
        headers["Connection"] = "close"
        headers.putAll(extraHeaders)
        writeHeaders(output, statusCode, statusText, headers)
        if (!headOnly) output.write(body)
        output.flush()
    }

    private fun writeHeaders(output: OutputStream, statusCode: Int, statusText: String, headers: Map<String, String>) {
        val builder = StringBuilder().append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n").append("Server: GTSTORE\r\n").append("Connection: close\r\n")
        for (entry in headers) builder.append(entry.key).append(": ").append(entry.value).append("\r\n")
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendError(output: OutputStream, statusCode: Int, message: String, extraHeaders: Map<String, String> = emptyMap()) {
        val body = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><title>$statusCode</title></head><body><h1>$statusCode</h1><p>${escapeHtml(message)}</p></body></html>".toByteArray(StandardCharsets.UTF_8)
        sendResponse(output, statusCode, message, "text/html; charset=utf-8", body, false, extraHeaders)
    }

    private fun sendRangeError(output: OutputStream, totalSize: Long) {
        sendError(output, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$totalSize", "Accept-Ranges" to "bytes", "Access-Control-Allow-Origin" to "*"))
    }

    private fun getWifiIpv4Address(): Inet4Address? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        } catch (_: Exception) { null }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
        return if (index == 0) "${value.toLong()} ${units[index]}" else String.format("%.2f %s", value, units[index])
    }

    private fun sanitizeFileName(name: String): String = name.replace("\"", "").replace("\r", "").replace("\n", "_")
    private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
