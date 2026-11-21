package com.veltrax.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local user library (favorites, playlists, download queue). Survives restarts. */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("veltrax_library_2", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = getArray("favorites").toSet()

    fun toggleFavorite(uri: String) {
        if (uri.isBlank()) return
        val values = favorites().toMutableSet()
        if (!values.add(uri)) values.remove(uri)
        saveArray("favorites", values.toList())
    }

    fun playlists(): Map<String, List<String>> {
        val raw = prefs.getString("playlists", "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { name ->
                obj.optJSONArray(name)?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim().take(45)
        if (clean.isEmpty()) return
        val maps = playlists().toMutableMap()
        if (!maps.containsKey(clean)) {
            maps[clean] = emptyList()
            savePlaylists(maps)
        }
    }

    fun addToPlaylist(name: String, uri: String) {
        if (uri.isBlank()) return
        val maps = playlists().toMutableMap()
        val old = maps[name] ?: return
        maps[name] = (old + uri).distinct()
        savePlaylists(maps)
    }

    fun removeFromPlaylist(name: String, uri: String) {
        val maps = playlists().toMutableMap()
        maps[name] = maps[name].orEmpty().filterNot { it == uri }
        savePlaylists(maps)
    }

    fun deletePlaylist(name: String) {
        val maps = playlists().toMutableMap()
        if (maps.remove(name) != null) savePlaylists(maps)
    }

    private fun savePlaylists(maps: Map<String, List<String>>) {
        val obj = JSONObject()
        maps.forEach { (k, v) -> obj.put(k, JSONArray(v)) }
        prefs.edit().putString("playlists", obj.toString()).apply()
    }

    private fun getArray(key: String): List<String> {
        return try {
            val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveArray(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }

    data class DownloadItem(
        val id: String,
        val url: String,
        val fileName: String,
        val workId: String = ""
    )

    fun downloads(): List<DownloadItem> {
        return try {
            val array = JSONArray(prefs.getString("downloads", "[]") ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id")
                val url = obj.optString("url")
                val fileName = obj.optString("fileName")
                if (id.isBlank() || url.isBlank() || fileName.isBlank()) null
                else DownloadItem(id, url, fileName, obj.optString("workId"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addDownload(item: DownloadItem) {
        val current = downloads().filterNot { it.id == item.id }
        val array = JSONArray()
        (listOf(item) + current).take(100).forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("url", it.url)
                    .put("fileName", it.fileName)
                    .put("workId", it.workId)
            )
        }
        prefs.edit().putString("downloads", array.toString()).apply()
    }

    fun removeDownload(id: String) {
        val remaining = downloads().filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("url", it.url)
                    .put("fileName", it.fileName)
                    .put("workId", it.workId)
            )
        }
        prefs.edit().putString("downloads", array.toString()).apply()
    }
}
