package com.veltrax.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Downloads only direct HTTP(S) audio files. No DRM circumvention or webpage extraction. */
class DirectAudioDownloader {
    data class Progress(val transferred: Long, val total: Long, val phase: String)
    class Paused : IOException("Transferencia pausada")

    fun download(
        url: String,
        stagingDirectory: File,
        downloadId: String,
        requestedFilename: String,
        chunkCount: Int = 3,
        stopped: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {}
    ): File {
        val uri = URL(url)
        require(uri.protocol == "https" || uri.protocol == "http") { "Solo se admiten URLs HTTP/HTTPS" }
        require(downloadId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "ID inválido" }
        require(chunkCount in 1..6)
        require(requestedFilename.isNotBlank())
        stagingDirectory.mkdirs()
        val name = safeFileName(requestedFilename)
        val probe = probe(url)
        if (probe.contentType.isNotEmpty() &&
            (probe.contentType.startsWith("text/") || probe.contentType.contains("json") || probe.contentType.contains("html"))) {
            throw IOException("El enlace devuelve una página o respuesta de texto, no un archivo de audio")
        }
        val meta = File(stagingDirectory, "$downloadId.meta")
        val old = Properties()
        if (meta.exists()) meta.inputStream().use { old.load(it) }
        val count = if (probe.supportsRange && probe.length > 2L * 1024 * 1024) chunkCount else 1
        if (old.getProperty("url") != url || old.getProperty("length") != probe.length.toString() ||
            old.getProperty("count") != count.toString() || old.getProperty("etag", "") != probe.etag) {
            for (i in 0..5) File(stagingDirectory, "$downloadId.$i.part").delete()
            File(stagingDirectory, "$downloadId.single.part").delete()
        }
        Properties().apply {
            setProperty("url", url)
            setProperty("length", probe.length.toString())
            setProperty("count", count.toString())
            setProperty("etag", probe.etag)
            setProperty("name", name)
        }.let { p -> meta.outputStream().use { p.store(it, "VeltraX download resume metadata") } }
        if (stopped()) throw Paused()
        val output = File(stagingDirectory, "$downloadId.completed")
        output.delete()
        if (count > 1) {
            val pool = Executors.newFixedThreadPool(count)
            val ranges = (0 until count).map { i ->
                val start = probe.length * i / count
                val end = probe.length * (i + 1) / count - 1
                Triple(i, start, end)
            }
            val lastReport = longArrayOf(0L)
            fun report() {
                synchronized(lastReport) {
                    val now = System.currentTimeMillis()
                    if (now - lastReport[0] < 180) return
                    lastReport[0] = now
                    val bytes = ranges.sumOf { (i, _, _) -> File(stagingDirectory, "$downloadId.$i.part").length() }
                    onProgress(Progress(bytes, probe.length, "Descargando por $count segmentos"))
                }
            }
            val futures = ranges.map { (i, start, end) ->
                pool.submit(Callable {
                    val part = File(stagingDirectory, "$downloadId.$i.part")
                    val required = end - start + 1
                    if (part.length() > required) part.delete()
                    val already = part.length()
                    if (already == required) return@Callable
                    val conn = connection(url, "GET", "bytes=${start + already}-$end")
                    try {
                        if (conn.responseCode != HttpURLConnection.HTTP_PARTIAL) throw IOException("El servidor dejó de admitir descargas por segmentos")
                        val contentRange = conn.getHeaderField("Content-Range").orEmpty()
                        if (!contentRange.startsWith("bytes ${start + already}-$end/")) {
                            throw IOException("Rango inesperado del servidor: $contentRange")
                        }
                        FileOutputStream(part, true).use { out ->
                            conn.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    if (stopped() || Thread.currentThread().isInterrupted) throw Paused()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    report()
                                }
                            }
                        }
                        if (part.length() != required) throw IOException("Segmento incompleto: $i")
                    } finally { conn.disconnect() }
                })
            }
            try {
                try { futures.forEach { it.get() } }
                catch (e: ExecutionException) {
                    val cause = e.cause
                    if (cause is Paused) throw cause
                    if (cause is IOException) throw cause
                    throw IOException("Falló un segmento", cause)
                }
                FileOutputStream(output).use { out ->
                    for (i in 0 until count) {
                        FileInputStream(File(stagingDirectory, "$downloadId.$i.part")).use { it.copyTo(out) }
                    }
                }
                if (output.length() != probe.length) throw IOException("Archivo incompleto")
            } finally {
                if (stopped()) futures.forEach { it.cancel(true) }
                pool.shutdownNow()
                pool.awaitTermination(3, TimeUnit.SECONDS)
            }
        } else {
            val part = File(stagingDirectory, "$downloadId.single.part")
            var already = part.length()
            var conn = connection(url, "GET", if (already > 0 && probe.supportsRange) "bytes=$already-" else null)
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                if (code != 206 && already > 0) {
                    already = 0
                    conn.disconnect()
                    part.delete()
                    conn = connection(url, "GET", null)
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                }
                FileOutputStream(part, already > 0).use { out ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = already
                        while (true) {
                            if (stopped()) throw Paused()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            bytes += n
                            onProgress(Progress(bytes, probe.length, "Descargando"))
                        }
                    }
                }
            } finally { conn.disconnect() }
            if (probe.length >= 0 && part.length() != probe.length) throw IOException("Archivo incompleto")
            part.copyTo(output, overwrite = true)
        }
        if (stopped()) throw Paused()
        if (output.length() == 0L) throw IOException("El archivo está vacío")
        // Verify a recognizable audio container header, rather than trusting a file extension.
        if (!hasAudioHeader(output)) {
            output.delete()
            throw IOException("El archivo no parece audio reconocido (MP3, MP4/M4A, WAV, OGG, FLAC, AAC o WMA)")
        }
        onProgress(Progress(output.length(), output.length(), "Completado"))
        for (i in 0..5) File(stagingDirectory, "$downloadId.$i.part").delete()
        File(stagingDirectory, "$downloadId.single.part").delete()
        meta.delete()
        return output
    }

    private data class Probe(val length: Long, val supportsRange: Boolean, val contentType: String, val etag: String)
    private fun probe(url: String): Probe {
        var mime = ""
        var etag = ""
        var length = -1L
        try {
            val head = connection(url, "HEAD", null)
            try {
                if (head.responseCode in 200..299) {
                    length = head.getHeaderFieldLong("Content-Length", -1)
                    mime = head.contentType.orEmpty().lowercase()
                    etag = head.getHeaderField("ETag").orEmpty()
                }
            } finally { head.disconnect() }
        } catch (_: IOException) { /* Servers can reject HEAD. */ }
        val range = connection(url, "GET", "bytes=0-0")
        try {
            val code = range.responseCode
            if (code !in 200..299) throw IOException("Servidor HTTP $code")
            val returnedType = range.contentType.orEmpty().lowercase()
            if (returnedType.isNotEmpty()) mime = returnedType
            if (etag.isEmpty()) etag = range.getHeaderField("ETag").orEmpty()
            val cr = range.getHeaderField("Content-Range").orEmpty()
            val total = cr.substringAfterLast('/', "").toLongOrNull()
            if (code == 206 && total != null) return Probe(total, true, mime, etag)
            if (length < 0) length = range.getHeaderFieldLong("Content-Length", -1)
            return Probe(length, false, mime, etag)
        } finally { range.disconnect() }
    }

    private fun connection(url: String, method: String, range: String?): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VeltraX/2.0 (Direct audio downloader)")
            setRequestProperty("Accept-Encoding", "identity")
            if (range != null) setRequestProperty("Range", range)
        }
    }
    private fun hasAudioHeader(file: File): Boolean = file.inputStream().use { stream ->
        val buffer = ByteArray(min(32L, file.length()).toInt())
        val n = stream.read(buffer)
        if (n < 4) return@use false
        val ascii = String(buffer, 0, n, Charsets.ISO_8859_1)
        if (ascii.startsWith("ID3") || ascii.startsWith("OggS") || ascii.startsWith("fLaC") ||
            ascii.startsWith("ADIF") || (ascii.startsWith("RIFF") && ascii.contains("WAVE")) ||
            (ascii.startsWith("FORM") && ascii.contains("AIFF"))) return@use true
        if (n >= 8 && ascii.substring(4, 8) == "ftyp") return@use true
        val b0 = buffer[0].toInt() and 0xFF
        val b1 = buffer[1].toInt() and 0xFF
        // MPEG audio / AAC ADTS frame sync.
        if (b0 == 0xFF && b1 and 0xE0 == 0xE0) return@use true
        // Windows Media ASF container signature.
        if (n >= 4 && b0 == 0x30 && b1 == 0x26 && (buffer[2].toInt() and 255) == 0xB2 &&
            (buffer[3].toInt() and 255) == 0x75) return@use true
        false
    }
    companion object {
        fun safeFileName(name: String): String {
            val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120)
            require(cleaned.isNotBlank() && cleaned != "." && cleaned != "..")
            val extensions = listOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma")
            return if (extensions.any { cleaned.endsWith(".$it", true) }) cleaned else "$cleaned.mp3"
        }
    }
}
