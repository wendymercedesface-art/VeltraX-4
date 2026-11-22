package com.veltrax.music.data

import android.content.Context
import com.veltrax.core.LrcLyrics
import com.veltrax.core.LyricLine
import com.veltrax.core.LyricWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class LyricsRepository(context: Context) {
    data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

    private val cacheDirectory = java.io.File(context.filesDir, "lyrics").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("veltrax_lyrics", Context.MODE_PRIVATE)

    fun cached(trackUri: String): Lyrics? {
        val file = cacheFile(trackUri)
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        val lines = root.optJSONArray("lines") ?: JSONArray()
        return Lyrics(
            lines.toLines(),
            root.optBoolean("synced", false)
        )
    }

    fun offset(trackUri: String): Long = preferences.getLong(preferenceKey(trackUri), 0L)

    fun saveOffset(trackUri: String, milliseconds: Long) {
        preferences.edit().putLong(preferenceKey(trackUri), milliseconds).apply()
    }

    fun save(trackUri: String, lyrics: Lyrics) {
        val root = JSONObject()
            .put("synced", lyrics.synced)
            .put("lines", JSONArray().apply {
                lyrics.lines.forEach { line ->
                    put(
                        JSONObject()
                            .put("timestampMs", line.timestampMs)
                            .put("text", line.text)
                            .put("words", JSONArray().apply {
                                line.words.forEach { word ->
                                    put(JSONObject().put("text", word.text).put("timestampMs", word.timestampMs))
                                }
                            })
                    )
                }
            })
        val target = cacheFile(trackUri)
        val temporary = java.io.File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(root.toString())
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            temporary.delete()
            throw IOException("No se pudo guardar la letra en el almacenamiento local", error)
        }
    }

    fun lookup(song: Song, query: String = ""): Lyrics? {
        val trackName = query.trim().ifBlank { song.title }
        val exact = (fetch(
            "/api/get?track_name=${encode(trackName)}&artist_name=${encode(song.artist)}&duration=${(song.duration / 1000).coerceAtLeast(1)}"
        ) as? JSONObject)?.let(::fromResponse)
        if (exact?.synced == true) return exact

        val searchTerm = listOf(trackName, song.artist).filter { it.isNotBlank() }.joinToString(" ")
        val results = fetch("/api/search?q=${encode(searchTerm)}") as? JSONArray ?: return exact
        val candidates = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        val chosen = candidates.firstOrNull {
            it.optString("syncedLyrics").isNotBlank() &&
                it.optString("trackName").equals(trackName, ignoreCase = true) &&
                it.optString("artistName").equals(song.artist, ignoreCase = true)
        } ?: candidates.firstOrNull { it.optString("syncedLyrics").isNotBlank() }
            ?: candidates.firstOrNull { it.optString("plainLyrics").isNotBlank() }
        return chosen?.let(::fromResponse) ?: exact
    }

    fun export(lyrics: Lyrics): String {
        require(lyrics.synced) { "Solo se pueden exportar letras sincronizadas como LRC" }
        return LrcLyrics.export(lyrics.lines)
    }

    private fun fromResponse(response: JSONObject): Lyrics? {
        val syncedText = response.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
        if (syncedText != null) {
            val lines = LrcLyrics.parse(syncedText)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true)
        }
        val plainText = response.optString("plainLyrics", "").takeIf { it.isNotBlank() } ?: return null
        val lines = plainText.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .mapIndexed { index, text -> LyricLine(index * 4_000L, text) }.toList()
        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = false) }
    }

    private fun fetch(path: String): Any? {
        val url = URL("https://lrclib.net$path")
        var lastFailure: IOException? = null
        repeat(2) { attempt ->
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "VeltraX/4.2 (local music player)")
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_NOT_FOUND) return null
                if (status !in 200..299) throw IOException("LRCLIB respondió HTTP $status")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
            } catch (error: IOException) {
                lastFailure = error
                if (attempt == 1) throw error
            } finally {
                connection.disconnect()
            }
        }
        throw lastFailure ?: IOException("No se pudo consultar LRCLIB")
    }

    private fun cacheFile(trackUri: String): java.io.File =
        java.io.File(cacheDirectory, "${hash(trackUri)}.json")

    private fun preferenceKey(trackUri: String): String = "offset_${hash(trackUri)}"

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JSONArray.toLines(): List<LyricLine> = (0 until length()).mapNotNull { index ->
        val line = optJSONObject(index) ?: return@mapNotNull null
        val words = line.optJSONArray("words") ?: JSONArray()
        LyricLine(
            line.optLong("timestampMs"),
            line.optString("text"),
            (0 until words.length()).mapNotNull { wordIndex ->
                val word = words.optJSONObject(wordIndex) ?: return@mapNotNull null
                LyricWord(word.optString("text"), word.optLong("timestampMs"))
            }
        )
    }
}
