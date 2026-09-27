package com.gtstore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.net.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

class HttpServer(
    private val context: Context,
    val port: Int = 8080
) {
    data class PkgItem(
        val id: String,
        val name: String,
        val size: Long,
        val uri: Uri
    )

    data class ServerStatus(
        val running: Boolean,
        val isRunning: Boolean,
        val port: Int,
        val localAddress: String?,
        val activeConnections: Int
    )

    @Volatile
    var isRunning: Boolean = false
        private set

    private var serverSocket: ServerSocket? = null
    private val threadPool = Executors.newFixedThreadPool(12)
    private val pkgCache = ConcurrentHashMap<String, PkgItem>()
    private val activeConnectionsCount = AtomicInteger(0)

    val localAddress: String?
        get() = getWifiIpv4Address()

    fun getStatus(): ServerStatus {
        val currentRunning = isRunning
        return ServerStatus(
            running = currentRunning,
            isRunning = currentRunning,
            port = port,
            localAddress = localAddress,
            activeConnections = activeConnectionsCount.get()
        )
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        threadPool.execute { scanPkgs() }

        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    threadPool.execute { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (isRunning) e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        threadPool.shutdown()
    }

    fun scanPkgs() {
        val prefs = context.getSharedPreferences("ps4_rpi_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("pkg_folder_uri", null) ?: return
        val treeUri = Uri.parse(uriString)
        val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return

        pkgCache.clear()
        scanDirectory(rootDir)
    }

    private fun scanDirectory(dir: DocumentFile) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file)
            } else if (file.isFile && file.name?.endsWith(".pkg", ignoreCase = true) == true) {
                val name = file.name ?: "unknown.pkg"
                val id = name.hashCode().toString().replace("-", "0")
                pkgCache[id] = PkgItem(id, name, file.length(), file.uri)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        activeConnectionsCount.incrementAndGet()
        try {
            socket.use { client ->
                client.soTimeout = 15000
                val input = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
                val output = client.getOutputStream()

                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val path = parts[1]

                val headers = mutableMapOf<String, String>()
                var line: String?
                while (input.readLine().also { line = it } != null && !line.isNullOrBlank()) {
                    val headerParts = line!!.split(":", limit = 2)
                    if (headerParts.size == 2) {
                        headers[headerParts[0].trim().lowercase(Locale.US)] = headerParts[1].trim()
                    }
                }

                if (method.equals("OPTIONS", ignoreCase = true)) {
                    sendCorsResponse(output)
                    return
                }

                when {
                    path == "/" || path == "/index.html" -> sendHomePage(output)
                    path == "/api/pkgs" -> sendPkgListJson(output)
                    path == "/api/scan" -> {
                        scanPkgs()
                        sendJsonResponse(output, 200, "{\"status\":\"ok\",\"count\":${pkgCache.size}}")
                    }
                    path.startsWith("/api/install") -> handleInstallRequest(input, headers, output)
                    path.startsWith("/pkg/") -> {
                        val pkgId = path.removePrefix("/pkg/").substringBefore("?")
                        servePkgFile(pkgId, headers["range"], method, output)
                    }
                    else -> sendNotFound(output)
                }
            }
        } finally {
            activeConnectionsCount.decrementAndGet()
        }
    }

    private fun servePkgFile(pkgId: String, rangeHeader: String?, method: String, output: OutputStream) {
        val pkg = pkgCache[pkgId] ?: run {
            sendNotFound(output)
            return
        }

        val totalFileSize = pkg.size
        var rangeStart = 0L
        var rangeEnd = totalFileSize - 1

        val isPartial = !rangeHeader.isNullOrEmpty() && rangeHeader.startsWith("bytes=")

        if (isPartial) {
            val rangeVal = rangeHeader!!.removePrefix("bytes=").trim()
            val ranges = rangeVal.split("-")
            try {
                if (ranges[0].isNotEmpty()) {
                    rangeStart = ranges[0].toLong()
                }
                if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                    rangeEnd = ranges[1].toLong()
                }
            } catch (e: NumberFormatException) {
                rangeStart = 0L
                rangeEnd = totalFileSize - 1
            }
        }

        if (rangeEnd >= totalFileSize) rangeEnd = totalFileSize - 1
        if (rangeStart > rangeEnd) rangeStart = 0L

        val contentLength = (rangeEnd - rangeStart) + 1

        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
        val rangeHeaderStr = if (isPartial) "Content-Range: bytes $rangeStart-$rangeEnd/$totalFileSize\r\n" else ""

        val responseHeaders = statusLine +
                "Accept-Ranges: bytes\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"${pkg.name}\"\r\n" +
                rangeHeaderStr +
                "Content-Length: $contentLength\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: keep-alive\r\n\r\n"

        output.write(responseHeaders.toByteArray(Charsets.US_ASCII))
        output.flush()

        if (method.equals("HEAD", ignoreCase = true)) return

        try {
            context.contentResolver.openInputStream(pkg.uri)?.use { inputStream ->
                skipFully(inputStream, rangeStart)

                val buffer = ByteArray(64 * 1024)
                var bytesRemaining = contentLength

                while (bytesRemaining > 0) {
                    val readSize = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                    val bytesRead = inputStream.read(buffer, 0, readSize)
                    if (bytesRead == -1) break

                    output.write(buffer, 0, bytesRead)
                    bytesRemaining -= bytesRead
                }
                output.flush()
            }
        } catch (e: IOException) {
            // Cliente desconectou durante o streaming
        }
    }

    private fun skipFully(inputStream: InputStream, bytesToSkip: Long) {
        var skippedTotal = 0L
        while (skippedTotal < bytesToSkip) {
            val skipped = inputStream.skip(bytesToSkip - skippedTotal)
            if (skipped <= 0) {
                if (inputStream.read() == -1) break
                skippedTotal++
            } else {
                skippedTotal += skipped
            }
        }
    }

    private fun handleInstallRequest(input: BufferedReader, headers: Map<String, String>, output: OutputStream) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = CharArray(contentLength)
        if (contentLength > 0) {
            input.read(body, 0, contentLength)
        }
        val requestBody = String(body)

        try {
            val json = JSONObject(requestBody)
            val ps4Ip = json.optString("ps4_ip")
            val pkgId = json.optString("pkg_id")

            val pkg = pkgCache[pkgId]
            val localIp = getWifiIpv4Address()

            if (pkg != null && ps4Ip.isNotEmpty() && localIp != null) {
                val pkgUrl = "http://$localIp:$port/pkg/${pkg.id}"
                val result = sendRpiInstallCommand(ps4Ip, pkgUrl)
                sendJsonResponse(output, 200, "{\"status\":\"success\",\"ps4_response\":$result}")
            } else {
                sendJsonResponse(output, 400, "{\"status\":\"error\",\"message\":\"Parâmetros inválidos ou Wi-Fi não conectado\"}")
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 500, "{\"status\":\"error\",\"message\":\"${e.localizedMessage}\"}")
        }
    }

    private fun sendRpiInstallCommand(ps4Ip: String, pkgUrl: String): String {
        val payload = JSONObject().apply {
            put("type", "direct")
            put("packages", JSONArray().put(pkgUrl))
        }.toString()

        val url = URL("http://$ps4Ip:12800/api/install")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun sendPkgListJson(output: OutputStream) {
        val jsonArray = JSONArray()
        for (pkg in pkgCache.values) {
            val item = JSONObject().apply {
                put("id", pkg.id)
                put("name", pkg.name)
                put("size", pkg.size)
                put("formatted_size", formatFileSize(pkg.size))
            }
            jsonArray.put(item)
        }
        sendJsonResponse(output, 200, jsonArray.toString())
    }

    private fun sendHomePage(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>GTStore PS4 Server</title>
                <style>
                    body { font-family: system-ui, sans-serif; background: #121212; color: #fff; padding: 20px; }
                    .card { background: #1e1e1e; padding: 15px; border-radius: 8px; margin-bottom: 10px; }
                </style>
            </head>
            <body>
                <h1>GTStore PS4 Package Server</h1>
                <p>Status: Servidor ativo</p>
                <div id="pkgs">Carregando pacotes...</div>
                <script>
                    fetch('/api/pkgs')
                        .then(r => r.json())
                        .then(data => {
                            const container = document.getElementById('pkgs');
                            if (data.length === 0) {
                                container.innerHTML = '<p>Nenhum pacote PKG encontrado.</p>';
                                return;
                            }
                            container.innerHTML = data.map(p => `
                                <div class="card">
                                    <h3>${'$'}{p.name}</h3>
                                    <p>Tamanho: ${'$'}{p.formatted_size}</p>
                                </div>
                            `).join('');
                        });
                </script>
            </body>
            </html>
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n" +
                "Connection: close\r\n\r\n" + html

        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val bodyBytes = json.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun sendCorsResponse(output: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Range\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun sendNotFound(output: OutputStream) {
        val body = "404 Not Found"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n" + body
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    fun getWifiIpv4Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.name.contains("wlan", ignoreCase = true) || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val value = size / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
    }
}
