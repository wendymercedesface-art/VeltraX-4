package com.veltrax.music.data

import android.content.ContentUris
import android.content.Context
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
                val title = when {
                    !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" -> rawTitle
                    !displayName.isNullOrBlank() -> displayName.substringBeforeLast('.')
                    else -> "Sin título"
                }
                val artist = cur.getString(artistCol)
                    ?.takeUnless { it.isBlank() || it == "<unknown>" }
                    ?: "Artista desconocido"
                val album = cur.getString(albumCol)?.takeUnless { it.isBlank() || it == "<unknown>" } ?: ""
                val duration = cur.getLong(durationCol).coerceAtLeast(0L)
                songs += Song(uri, title, artist, album, duration)
            }
        }
        songs
    }
}
