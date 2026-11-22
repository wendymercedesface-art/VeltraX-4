package com.veltrax.music.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long
)

class MusicRepository(private val context: Context) {

    suspend fun readAll(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        // Prefer real music tracks; still include files without IS_MUSIC flag if they are large enough.
        val selection = "${MediaStore.Audio.Media.SIZE} > 8192 AND " +
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 5000)"

        context.contentResolver.query(
            collection,
            columns,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val nameCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cur.moveToNext()) {
                val id = cur.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val rawTitle = cur.getString(titleCol)
                val displayName = cur.getString(nameCol)
                val retriever = readEmbeddedMetadata(uri)
                val embeddedTitle = retriever["title"]
                val embeddedArtist = retriever["artist"]
                val embeddedAlbum = retriever["album"]
                val title = when {
                    !embeddedTitle.isNullOrBlank() && !embeddedTitle.isUnknown() -> embeddedTitle
                    !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" -> rawTitle
                    !displayName.isNullOrBlank() -> displayName.substringBeforeLast('.')
                    else -> "Sin título"
                }
                val artist = embeddedArtist?.takeUnless { it.isUnknown() }
                    ?: cur.getString(artistCol)?.takeUnless { it.isUnknown() }
                    ?: inferArtist(title, displayName)
                val album = embeddedAlbum?.takeUnless { it.isUnknown() }
                    ?: cur.getString(albumCol)?.takeUnless { it.isUnknown() }
                    ?: "Música"
                val duration = cur.getLong(durationCol).coerceAtLeast(0L)
                songs += Song(uri, title, artist, album, duration)
            }
        }
        songs
    }

    private fun readEmbeddedMetadata(uri: String): Map<String, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, android.net.Uri.parse(uri))
            mapOf(
                "title" to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                "artist" to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                "album" to retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            )
        } catch (_: Exception) {
            emptyMap()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                Unit
            }
        }
    }

    private fun inferArtist(title: String, displayName: String?): String {
        val candidates = listOf(title, displayName.orEmpty())
        candidates.forEach { candidate ->
            val separated = Regex("""^\s*(.+?)\s+[-–—]\s*(.+)$""").matchEntire(candidate)
            if (separated != null) {
                val left = separated.groupValues[1].trim()
                val right = separated.groupValues[2].trim().substringBeforeLast('.').trim()
                if (left.isNotBlank() && !left.isUnknown()) return left
                if (right.isNotBlank() && !right.isUnknown()) return right
            }
        }
        return "Artista no identificado"
    }
}

private fun String.isUnknown(): Boolean {
    val normalized = trim().lowercase()
    return isBlank() ||
        normalized == "<unknown>" ||
        normalized == "unknown" ||
        normalized == "unknown artist" ||
        normalized == "artista desconocido"
}
