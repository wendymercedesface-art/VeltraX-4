package com.veltrax.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer

/** JavaFX player running on its own media thread; persists when the main window is minimized. */
class DesktopPlayer {
    private var current: MediaPlayer? = null
    private var queue: List<DesktopSong> = emptyList()
    @Volatile var selected: DesktopSong? = null
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var error: String? = null
        private set
    init { JFXPanel(); Platform.setImplicitExit(false) }
    fun play(song: DesktopSong, list: List<DesktopSong>) {
        queue = list.ifEmpty { listOf(song) }
        selected = song
        error = null
        Platform.runLater {
            try {
                current?.stop(); current?.dispose()
                current = MediaPlayer(Media(song.file.toURI().toASCIIString())).also { mp ->
                    mp.setOnEndOfMedia { next() }
                    mp.setOnError { error = mp.error?.localizedMessage ?: "El formato no se puede reproducir"; playing = false }
                    mp.play()
                    playing = true
                }
            } catch (e: Exception) { error = e.localizedMessage; playing = false }
        }
    }
    fun toggle() { Platform.runLater { current?.let { if (playing) it.pause() else it.play(); playing = !playing } } }
    fun next() {
        val idx = queue.indexOfFirst { it.id == selected?.id }
        if (idx >= 0 && idx + 1 < queue.size) play(queue[idx + 1], queue)
        else { Platform.runLater { current?.stop() }; playing = false }
    }
    fun previous() {
        val idx = queue.indexOfFirst { it.id == selected?.id }
        if (idx > 0) play(queue[idx - 1], queue)
        else seek(0.0)
    }
    fun positionMs(): Long = try { current?.currentTime?.toMillis()?.toLong() ?: 0 } catch (_: Exception) { 0 }
    fun durationMs(): Long = try { current?.totalDuration?.toMillis()?.toLong() ?: 0 } catch (_: Exception) { 0 }
    fun seek(fraction: Double) { Platform.runLater {
        current?.let { player -> if (durationMs() > 0) player.seek(javafx.util.Duration.millis(durationMs() * fraction.coerceIn(0.0, 1.0))) }
    } }
    fun close() { Platform.runLater { current?.stop(); current?.dispose(); Platform.exit() } }
}
