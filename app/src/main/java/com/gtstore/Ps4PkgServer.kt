package com.teu.pacote.gtstore // ATENÇÃO: Muda isto para o package name do teu projeto!

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// ---------------------------------------------------------------- PKG

data class PkgInfo(
    val file: File,
    val title: String,
    val contentId: String,
    val category: String,
    val digest: String,
    val size: Long,
    val icon: ByteArray?,
) {
    val bgftType: String get() = "PS4" + category.uppercase()
}

object PkgReader {
    private const val MAGIC = 0x7F434E54
    private const val ID_PARAM_SFO = 0x1000
    private const val ID_ICON0_PNG = 0x1200

    fun read(file: File): PkgInfo? = try {
        RandomAccessFile(file, "r").use { parse(file, it) }
    } catch (e: Exception) {
        null
    }

    private fun parse(file: File, raf: RandomAccessFile): PkgInfo? {
        val head = ByteArray(0x1000)
        raf.seek(0)
        raf.readFully(head)
        val h = ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN)
        if (h.getInt(0) != MAGIC) return null

        val entryCount = h.getInt(0x10)
        val tableOffset = h.getInt(0x18).toLong() and 0xFFFFFFFFL
        if (entryCount !in 1..4096) return null

        val table = ByteArray(entryCount * 0x20)
        raf.seek(tableOffset)
        raf.readFully(table)
        val t = ByteBuffer.wrap(table).order(ByteOrder.BIG_ENDIAN)

        var sfo: ByteArray? = null
        var icon: ByteArray? = null
        for (i in 0 until entryCount) {
            val base = i * 0x20
            val id = t.getInt(base)
            val dataOffset = t.getInt(base + 16).toLong() and 0xFFFFFFFFL
            val dataSize = t.getInt(base + 20).toLong() and 0xFFFFFFFFL
            if (dataSize == 0L || dataSize > 4_000_000L) continue
            when (id) {
                ID_PARAM_SFO -> sfo = readAt(raf, dataOffset, dataSize)
                ID_ICON0_PNG -> icon = readAt(raf, dataOffset, dataSize)
            }
        }

        val params = parseSfo(sfo ?: return null)
        val digest = head.copyOfRange(0xFE0, 0x1000).joinToString("") { "%02X".format(it) }

        return PkgInfo(
            file = file,
            title = params["TITLE"] ?: file.nameWithoutExtension,
            contentId = params["CONTENT_ID"] ?: return null,
            category = params["CATEGORY"] ?: return null,
            digest = digest,
            size = file.length(),
            icon = icon,
        )
    }

    private fun readAt(raf: RandomAccessFile, offset: Long, size: Long): ByteArray {
        val buf = ByteArray(size.toInt())
        raf.seek(offset)
        raf.readFully(buf)
        return buf
    }

    private fun parseSfo(b: ByteArray): Map<String, String> {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (b.size < 20 || bb.getInt(0) != 0x46535000) return emptyMap()
        val keyTable = bb.getInt(8)
        val dataTable = bb.getInt(12)
        val count = bb.getInt(16)
        val out = HashMap<String, String>()
        for (i in 0 until count) {
            val e = 20 + i * 16
            val keyOff = bb.getShort(e).toInt() and 0xFFFF
            val fmt = bb.getShort(e + 2).toInt() and 0xFFFF
            val len = bb.getInt(e + 4)
            val dataOff = bb.getInt(e + 12)
            if (fmt != 0x0204) continue
            val ks = keyTable + keyOff
            var ke = ks
            while (b[ke] != 0.toByte()) ke++
            val key = String(b, ks, ke - ks, Charsets.UTF_8)
            val value = String(b, dataTable + dataOff, len, Charsets.UTF_8).trimEnd('\u0000')
            out[key] = value
        }
        return out
    }
}

// ---------------------------------------------------------------- Payload / BinLoader

class PayloadSender(private val payloadTemplate: ByteArray) {
    private val ports = intArrayOf(9090, 9021, 9020)

    fun send(ps4Ip: String, localIp: String, manifestUrl: String, info: PkgInfo) {
        val payload = payloadTemplate.copyOf()
        val off = indexOf(payload, ByteArray(6) { 0xB4.toByte() })
        require(off >= 0) { "Marcador do payload não encontrado" }

        val local = InetAddress.getByName(localIp)
        ServerSocket(0, 5, local).use { server ->
            server.soTimeout = 15_000
            val port = server.localPort
            local.address.copyInto(payload, off)
            payload[off + 4] = (port ushr 8).toByte()
            payload[off + 5] = port.toByte()

            sendToBinLoader(ps4Ip, payload)

            server.accept().use { client ->
                client.getOutputStream().apply {
                    write(buildInfo(manifestUrl, info))
                    flush()
                }
            }
        }
    }

    private fun sendToBinLoader(ps4Ip: String, payload: ByteArray) {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            for (port in ports) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(ps4Ip, port), 3000)
                        s.getOutputStream().apply { write(payload); flush() }
                    }
                    return
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (attempt == 0) Thread.sleep(3000)
        }
        throw IllegalStateException("BinLoader inacessível (9090/9021/9020). GoldHEN > BinLoader ativado?", lastError)
    }

    private fun buildInfo(url: String, info: PkgInfo): ByteArray {
        val out = ByteArrayOutputStream()
        fun i32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun str(s: String) { val b = s.toByteArray(Charsets.UTF_8); i32(b.size); out.write(b) }

        i32(1)
        str(url)
        str(info.title)
        str(info.contentId)
        str(info.bgftType)
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(info.size).array())
        val icon = info.icon
        if (icon == null || icon.isEmpty()) i32(0) else { i32(icon.size); out.write(icon) }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}

// ---------------------------------------------------------------- HTTP

class Ps4PkgServer(
    private val pkgDir: File,
    payload: ByteArray,
    private val httpPort: Int = 8080,
    private val log: (String) -> Unit = {},
) : NanoHTTPD(httpPort) {

    private val sender = PayloadSender(payload)
    private val pool = Executors.newCachedThreadPool()
    private val manifests = ConcurrentHashMap<String, String>()
    private val nextId = AtomicInteger(0)
    @Volatile private var known: Map<String, PkgInfo> = emptyMap()

    private fun scan(): Map<String, PkgInfo> {
        val map = LinkedHashMap<String, PkgInfo>()
        pkgDir.listFiles { f -> f.isFile && f.name.endsWith(".pkg", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { f -> PkgReader.read(f)?.let { map[f.name] = it } }
        known = map
        return map
    }

    override fun serve(s: IHTTPSession): Response = try {
        val uri = s.uri
        when {
            uri == "/" -> listing()
            uri == "/install" -> install(s)
            uri.startsWith("/json/") -> manifests[uri.removePrefix("/json/").removeSuffix(".json")]
                ?.let { newFixedLengthResponse(Response.Status.OK, "application/json", it) }
                ?: text(Response.Status.NOT_FOUND, "404")
            uri.startsWith("/files/") -> {
                val name = uri.removePrefix("/files/")
                val info = known[name] ?: scan()[name]
                if (info == null) text(Response.Status.NOT_FOUND, "404") else serveFile(s, info)
            }
            else -> text(Response.Status.NOT_FOUND, "404")
        }
    } catch (e: Exception) {
        log("Erro em ${s.uri}: $e")
        text(Response.Status.INTERNAL_ERROR, "Erro: ${e.message}")
    }

    private fun listing(): Response {
        val items = scan().values.joinToString("") { p ->
            val link = "/install?f=" + URLEncoder.encode(p.file.name, "UTF-8")
            "<p><a href=\"$link\">${esc(p.title)}</a><br><small>${esc(p.file.name)} — ${p.size / 1_048_576} MB — ${esc(p.bgftType)}</small></p>"
        }.ifEmpty { "<p>Nenhum PKG encontrado em ${esc(pkgDir.path)}</p>" }
        val html = "<html><head><meta charset=\"utf-8\"><title>PKGs</title></head><body>" +
            "<h2>Escolha um PKG</h2>$items</body></html>"
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun install(s: IHTTPSession): Response {
        val name = s.parameters["f"]?.firstOrNull() ?: return text(Response.Status.BAD_REQUEST, "Falta o parâmetro f")
        val info = known[name] ?: scan()[name] ?: return text(Response.Status.NOT_FOUND, "PKG não encontrado")

        val localIp = s.headers["host"]?.substringBefore(':')
        if (localIp == null || !Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(localIp))
            return text(Response.Status.BAD_REQUEST, "Abra a página pelo IP do Android, não por nome.")
        val ps4Ip = s.remoteIpAddress

        val id = nextId.getAndIncrement().toString()
        val fileUrl = "http://$localIp:$httpPort/files/" + URLEncoder.encode(info.file.name, "UTF-8").replace("+", "%20")
        manifests[id] = """{"originalFileSize":${info.size},"packageDigest":"${info.digest}",""" +
            """"numberOfSplitFiles":1,"pieces":[{"url":"$fileUrl","fileOffset":0,"fileSize":${info.size},""" +
            """"hashValue":"0000000000000000000000000000000000000000"}]}"""
        val manifestUrl = "http://$localIp:$httpPort/json/$id.json"

        pool.execute {
            try {
                log("Enviando ${info.file.name} para $ps4Ip")
                sender.send(ps4Ip, localIp, manifestUrl, info)
                log("Pacote enviado; o PS4 deve baixar o manifesto agora")
            } catch (e: Exception) {
                log("Falha ao enviar: $e")
            }
        }
        val html = "<html><head><meta charset=\"utf-8\"></head><body><h2>Enviando…</h2>" +
            "<p>${esc(info.title)}</p><p>Aguarde a notificação de download no PS4.</p>" +
            "<p><a href=\"/\">Voltar</a></p></body></html>"
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveFile(s: IHTTPSession, info: PkgInfo): Response {
        val size = info.size
        var start = 0L
        var end = size - 1
        var partial = false

        val range = s.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val parts = range.removePrefix("bytes=").split("-", limit = 2)
            val a = parts[0]
            val b = parts.getOrElse(1) { "" }
            if (a.isEmpty()) {
                val n = b.toLongOrNull() ?: return text(Response.Status.RANGE_NOT_SATISFIABLE, "416")
                start = maxOf(0L, size - n)
            } else {
                start = a.toLongOrNull() ?: return text(Response.Status.RANGE_NOT_SATISFIABLE, "416")
                end = b.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
            }
            if (start > end || start >= size) return text(Response.Status.RANGE_NOT_SATISFIABLE, "416")
            partial = true
        }

        val len = end - start + 1
        val raf = RandomAccessFile(info.file, "r")
        raf.seek(start)
        val res = newFixedLengthResponse(
            if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            "application/octet-stream", FileRangeStream(raf, len), len,
        )
        res.addHeader("Accept-Ranges", "none")
        if (partial) res.addHeader("Content-Range", "bytes $start-$end/$size")
        return res
    }

    private fun text(status: Response.IStatus, msg: String) =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private class FileRangeStream(private val raf: RandomAccessFile, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = raf.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun close() = raf.close()
    }
}
