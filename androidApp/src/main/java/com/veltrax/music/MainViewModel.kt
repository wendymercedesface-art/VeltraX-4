package com.veltrax.music

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import com.veltrax.music.data.LibraryStore
import com.veltrax.music.data.LyricsRepository
import com.veltrax.music.data.MusicRepository
import com.veltrax.music.data.Song
import com.veltrax.music.download.DownloadWorker
import com.veltrax.music.player.PlaybackService
import com.veltrax.core.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class LyricsUiState(
    val songUri: String = "",
    val lines: List<LyricLine> = emptyList(),
    val synced: Boolean = false,
    val offsetMs: Long = 0L,
    val loading: Boolean = false,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val lyricsRepository = LyricsRepository(app)
    private val library = LibraryStore(app)
    private val workManager = WorkManager.getInstance(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _favorites = MutableStateFlow(library.favorites())
    val favorites = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(library.playlists())
    val playlists = _playlists.asStateFlow()

    private val _downloads = MutableStateFlow(library.downloads())
    val downloads = _downloads.asStateFlow()

    private val _appearance = MutableStateFlow(library.appearance())
    val appearance = _appearance.asStateFlow()

    private val _workStates = MutableStateFlow<Map<String, WorkInfo>>(emptyMap())
    val workStates = _workStates.asStateFlow()

    private val _nowPlaying = MutableStateFlow<Song?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics = _lyrics.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val playerReady = _ready.asStateFlow()

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    init {
        connectController()
    }

    private fun connectController() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { f ->
            f.addListener({
                try {
                    controller = f.get().also { c ->
                        c.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                _isPlaying.value = isPlaying
                            }

                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                syncPlayer()
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                syncPlayer()
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                _isPlaying.value = false
                            }
                        })
                        _ready.value = true
                        syncPlayer()
                    }
                } catch (_: Exception) {
                    _ready.value = false
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }

    private fun syncPlayer() {
        val c = controller ?: return
        val uri = c.currentMediaItem?.localConfiguration?.uri?.toString()
        _nowPlaying.value = _songs.value.firstOrNull { it.uri == uri }
            ?: c.currentMediaItem?.let { item ->
                Song(
                    uri ?: "",
                    item.mediaMetadata.title?.toString() ?: "Canción",
                    item.mediaMetadata.artist?.toString() ?: "",
                    item.mediaMetadata.albumTitle?.toString() ?: "",
                    c.duration.coerceAtLeast(0)
                )
            }
        _isPlaying.value = c.isPlaying
        val pos = c.currentPosition
        val dur = c.duration
        _position.value = if (pos >= 0) pos else 0L
        _duration.value = if (dur >= 0) dur else 0L
    }

    fun refreshSongs() {
        viewModelScope.launch {
            try {
                _songs.value = repository.readAll()
                syncPlayer()
            } catch (_: SecurityException) {
                _songs.value = emptyList()
            } catch (_: Exception) {
                // Keep previous list on transient MediaStore failures.
            }
        }
    }

    fun refreshProgress() {
        val c = controller ?: return
        val pos = c.currentPosition
        val dur = c.duration
        if (pos >= 0) _position.value = pos
        if (dur >= 0) _duration.value = dur
        _isPlaying.value = c.isPlaying
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            _downloads.value = library.downloads()
            val results = withContext(Dispatchers.IO) {
                try {
                    workManager.getWorkInfosByTag("veltrax").get()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val currentRequests = _downloads.value
                .filter { it.workId.isNotBlank() }
                .associate { it.workId to it.id }
            _workStates.value = results.mapNotNull { wi ->
                currentRequests[wi.id.toString()]?.let { downloadId -> downloadId to wi }
            }.toMap()
        }
    }

    fun play(song: Song, collection: List<Song>) {
        val c = controller ?: return
        val queue = collection.ifEmpty { listOf(song) }
        val startIndex = queue.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
        val items = queue.map { s ->
            MediaItem.Builder()
                .setUri(Uri.parse(s.uri))
                .setMediaId(s.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
                .build()
        }
        try {
            c.setMediaItems(items, startIndex, 0L)
            c.prepare()
            c.play()
            syncPlayer()
        } catch (_: Exception) {
            // Ignore invalid media items.
        }
    }

    fun togglePlayback() {
        val c = controller ?: return
        try {
            if (c.isPlaying) c.pause() else c.play()
            syncPlayer()
        } catch (_: Exception) { }
    }

    fun next() {
        try {
            controller?.seekToNextMediaItem()
            syncPlayer()
        } catch (_: Exception) { }
    }

    fun previous() {
        try {
            val c = controller ?: return
            if (c.currentPosition > 3000) {
                c.seekTo(0)
            } else {
                c.seekToPreviousMediaItem()
            }
            syncPlayer()
        } catch (_: Exception) { }
    }

    fun seek(milliseconds: Long) {
        try {
            controller?.seekTo(milliseconds.coerceAtLeast(0))
            syncPlayer()
        } catch (_: Exception) { }
    }

    fun loadLyrics(song: Song) {
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { lyricsRepository.cached(song.uri) }
                _lyrics.value = LyricsUiState(
                    songUri = song.uri,
                    lines = cached?.lines.orEmpty(),
                    synced = cached?.synced ?: false,
                    offsetMs = withContext(Dispatchers.IO) { lyricsRepository.offset(song.uri) },
                    message = if (cached == null) {
                        "No hay letra guardada. Puedes buscarla cuando tengas conexión."
                    } else null
                )
            } catch (error: Exception) {
                _lyrics.value = LyricsUiState(
                    songUri = song.uri,
                    offsetMs = withContext(Dispatchers.IO) { lyricsRepository.offset(song.uri) },
                    message = "No se pudo leer la letra local: ${error.message ?: "archivo dañado"}"
                )
            }
        }
    }

    fun searchLyrics(song: Song, query: String = "") {
        if (_lyrics.value.loading) return
        _lyrics.value = _lyrics.value.copy(songUri = song.uri, loading = true, message = null)
        viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) { lyricsRepository.lookup(song, query) }
                if (found == null) {
                    _lyrics.value = LyricsUiState(
                        songUri = song.uri,
                        offsetMs = withContext(Dispatchers.IO) { lyricsRepository.offset(song.uri) },
                        message = "No se encontró una letra para esta pista."
                    )
                } else {
                    withContext(Dispatchers.IO) { lyricsRepository.save(song.uri, found) }
                    _lyrics.value = LyricsUiState(
                        songUri = song.uri,
                        lines = found.lines,
                        synced = found.synced,
                        offsetMs = withContext(Dispatchers.IO) { lyricsRepository.offset(song.uri) },
                        message = if (found.synced) null else "La letra encontrada no tiene sincronización."
                    )
                }
            } catch (error: IOException) {
                _lyrics.value = LyricsUiState(
                    songUri = song.uri,
                    offsetMs = withContext(Dispatchers.IO) { lyricsRepository.offset(song.uri) },
                    message = "No se pudo buscar la letra: ${error.message ?: "error de red"}"
                )
            } catch (error: Exception) {
                _lyrics.value = LyricsUiState(
                    songUri = song.uri,
                    offsetMs = withContext(Dispatchers.IO) { lyricsRepository.offset(song.uri) },
                    message = "No se pudo guardar la letra: ${error.message ?: "error local"}"
                )
            }
        }
    }

    fun adjustLyricsOffset(song: Song, deltaMs: Long) {
        val current = _lyrics.value
        if (current.songUri != song.uri) return
        val offset = (current.offsetMs + deltaMs).coerceIn(-30_000L, 30_000L)
        _lyrics.value = current.copy(offsetMs = offset)
        viewModelScope.launch(Dispatchers.IO) { lyricsRepository.saveOffset(song.uri, offset) }
    }

    fun markNextLyricLine(song: Song, playbackPositionMs: Long) {
        val current = _lyrics.value
        if (current.songUri != song.uri || current.lines.isEmpty() || !current.synced) return
        val adjustedPosition = playbackPositionMs + current.offsetMs
        val index = current.lines.indexOfFirst { it.timestampMs > adjustedPosition }
            .takeIf { it >= 0 } ?: current.lines.lastIndex
        val updated = current.lines.toMutableList()
        updated[index] = updated[index].copy(
            timestampMs = adjustedPosition.coerceAtLeast(0L),
            words = emptyList()
        )
        val sorted = updated.sortedBy { it.timestampMs }
        _lyrics.value = current.copy(lines = sorted, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                lyricsRepository.save(song.uri, LyricsRepository.Lyrics(sorted, synced = true))
            } catch (error: IOException) {
                _lyrics.value = _lyrics.value.copy(message = "No se pudo guardar el ajuste: ${error.message}")
            }
        }
    }

    fun exportLyrics(song: Song): String? {
        val current = _lyrics.value
        if (current.songUri != song.uri || !current.synced) return null
        return try {
            val adjustedLines = current.lines.map { line ->
                line.copy(
                    timestampMs = (line.timestampMs - current.offsetMs).coerceAtLeast(0L),
                    words = line.words.map { word ->
                        word.copy(timestampMs = (word.timestampMs - current.offsetMs).coerceAtLeast(0L))
                    }
                )
            }
            lyricsRepository.export(LyricsRepository.Lyrics(adjustedLines, synced = true))
        } catch (error: IllegalArgumentException) {
            _lyrics.value = current.copy(message = error.message)
            null
        }
    }

    fun toggleFavorite(uri: String) {
        if (uri.isBlank()) return
        library.toggleFavorite(uri)
        _favorites.value = library.favorites()
    }

    fun updateAppearance(appearance: LibraryStore.Appearance) {
        library.saveAppearance(appearance)
        _appearance.value = library.appearance()
    }

    fun createPlaylist(name: String) {
        library.createPlaylist(name)
        _playlists.value = library.playlists()
    }

    fun addToPlaylist(name: String, uri: String) {
        library.addToPlaylist(name, uri)
        _playlists.value = library.playlists()
    }

    fun removeFromPlaylist(name: String, uri: String) {
        library.removeFromPlaylist(name, uri)
        _playlists.value = library.playlists()
    }

    fun deletePlaylist(name: String) {
        library.deletePlaylist(name)
        _playlists.value = library.playlists()
    }

    fun enqueue(url: String, filename: String) {
        val trimmedUrl = url.trim()
        val trimmedName = filename.trim()
        val validUrl = try {
            val u = java.net.URI(trimmedUrl)
            u.scheme in listOf("https", "http") && !u.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
        if (!validUrl || trimmedName.isBlank()) return

        val id = UUID.randomUUID().toString()
        val item = LibraryStore.DownloadItem(id, trimmedUrl, trimmedName)
        library.addDownload(item)
        _downloads.value = library.downloads()
        submit(item)
    }

    fun enqueueBulk(input: String): Int {
        val urls = Regex("""https?://[^\s,;"<>]+""", RegexOption.IGNORE_CASE)
            .findAll(input)
            .map { it.value.trimEnd('.', ')', ']', '}') }
            .distinct()
            .toList()
        var added = 0
        urls.forEachIndexed { index, rawUrl ->
            val url = Regex("^https?://", RegexOption.IGNORE_CASE)
                .replace(rawUrl) { it.value.lowercase() }
            val filename = try {
                val pathName = java.net.URI(url).path.substringAfterLast('/')
                java.net.URLDecoder.decode(pathName, Charsets.UTF_8.name())
                    .takeIf { it.isNotBlank() } ?: "Descarga ${index + 1}.mp3"
            } catch (_: Exception) {
                "Descarga ${index + 1}.mp3"
            }
            val valid = try {
                val parsed = java.net.URI(url)
                parsed.scheme in listOf("https", "http") && !parsed.host.isNullOrBlank()
            } catch (_: Exception) {
                false
            }
            if (!valid) return@forEachIndexed
            enqueue(url, filename)
            added++
        }
        return added
    }

    private fun submit(item: LibraryStore.DownloadItem) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.URL, item.url)
                    .putString(DownloadWorker.NAME, item.fileName)
                    .putString(DownloadWorker.ID, item.id)
                    .build()
            )
            .addTag("veltrax")
            .addTag("file:${item.id}")
            .build()
        library.addDownload(item.copy(workId = request.id.toString()))
        _downloads.value = library.downloads()
        workManager.enqueueUniqueWork(
            "veltrax:${item.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        refreshDownloads()
    }

    fun pause(item: LibraryStore.DownloadItem) {
        workManager.cancelUniqueWork("veltrax:${item.id}")
        refreshDownloads()
    }

    fun resume(item: LibraryStore.DownloadItem) {
        submit(item)
    }

    fun removeDownload(item: LibraryStore.DownloadItem) {
        workManager.cancelUniqueWork("veltrax:${item.id}")
        library.removeDownload(item.id)
        _downloads.value = library.downloads()
        refreshDownloads()
    }

    override fun onCleared() {
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
