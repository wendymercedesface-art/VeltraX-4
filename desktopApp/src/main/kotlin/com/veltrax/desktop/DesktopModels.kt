package com.veltrax.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.veltrax.core.DirectAudioDownloader
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
data class DesktopSong(val file: File) {
    val id get() = file.absolutePath
    val title get() = file.nameWithoutExtension.substringAfter(" - ", file.nameWithoutExtension)
    val artist get() = file.nameWithoutExtension.substringBefore(" - ", "Artista desconocido")
}

class DesktopStorage {
    val configDirectory = File(System.getProperty("user.home"), ".veltrax").apply { mkdirs() }
    val downloadsDirectory = File(System.getProperty("user.home"), "Music/VeltraX").apply { mkdirs() }
    private val preferences = File(configDirectory, "library.properties")
    private val properties = Properties().also { if (preferences.exists()) preferences.inputStream().use { stream -> it.load(stream) } }
    val roots: MutableSet<String> = properties.getProperty("roots", "").split('|').filter { it.isNotBlank() }.toMutableSet()
    val favorites: MutableSet<String> = properties.getProperty("favorites", "").split('|').filter { it.isNotBlank() }.toMutableSet()
    val playlists: MutableMap<String, MutableSet<String>> = mutableMapOf()
    init {
        properties.stringPropertyNames().filter { it.startsWith("list.") }.forEach { key ->
            playlists[key.removePrefix("list.")] = properties.getProperty(key, "").split('|').filter { it.isNotBlank() }.toMutableSet()
        }
    }
    @Synchronized fun save() {
        properties.setProperty("roots", roots.joinToString("|"))
        properties.setProperty("favorites", favorites.joinToString("|"))
        properties.stringPropertyNames().filter { it.startsWith("list.") }.forEach { properties.remove(it) }
        playlists.forEach { (name, tracks) -> properties.setProperty("list.$name", tracks.joinToString("|")) }
        preferences.outputStream().use { properties.store(it, "VeltraX local library") }
    }
    fun scan(): List<DesktopSong> {
        val paths = (roots.map { File(it) } + downloadsDirectory).distinct()
        return paths.flatMap { root ->
            if (!root.isDirectory) emptyList() else root.walkTopDown().onEnter { it.absolutePath.length < 400 }
                .filter { it.isFile && it.extension.lowercase() in audioExtensions }.toList()
        }.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }.map(::DesktopSong)
    }
}

class DesktopDownloadQueue(private val storage: DesktopStorage, private val changed: () -> Unit) {
    class Job(val id: String, val url: String, val name: String) {
        val stop = AtomicBoolean(false)
        var state by mutableStateOf("En cola")
        var transferred by mutableStateOf(0L)
        var total by mutableStateOf(-1L)
    }
    private val executor = Executors.newFixedThreadPool(3)
    private val registry = File(storage.configDirectory, "downloads.properties")
    val jobs = mutableStateListOf<Job>()
    init {
        if (registry.exists()) {
            val props = Properties()
            registry.inputStream().use { props.load(it) }
            props.stringPropertyNames().filter { it.endsWith(".url") }.forEach { key ->
                val id = key.removeSuffix(".url")
                val url = props.getProperty(key)
                val name = props.getProperty("$id.name", "audio.mp3")
                jobs += Job(id, url, name).also { it.state = "Pausado" }
            }
        }
    }
    @Synchronized private fun persist() {
        val props = Properties()
        jobs.filter { it.state != "Completado" }.forEach { j ->
            props.setProperty("${j.id}.url", j.url)
            props.setProperty("${j.id}.name", j.name)
        }
        registry.outputStream().use { props.store(it, "VeltraX resumable downloads") }
    }
    fun enqueue(url: String, name: String): Job {
        val job = Job(UUID.randomUUID().toString(), url, DirectAudioDownloader.safeFileName(name))
        jobs.add(0, job)
        persist()
        start(job)
        return job
    }
    fun pause(job: Job) { job.stop.set(true); job.state = "Pausando…" }
    fun resume(job: Job) { if (job.state != "Completado") { job.stop.set(false); start(job) } }
    private fun start(job: Job) {
        job.state = "En cola"
        executor.submit {
            job.state = "Descargando"
            try {
                val result = DirectAudioDownloader().download(job.url, File(storage.configDirectory, "parts"),
                    job.id, job.name, stopped = { job.stop.get() }) { p ->
                    job.transferred = p.transferred
                    job.total = p.total
                }
                if (job.stop.get()) throw DirectAudioDownloader.Paused()
                val destination = uniqueName(File(storage.downloadsDirectory, job.name))
                result.copyTo(destination)
                result.delete()
                job.state = "Completado"
                changed()
            } catch (_: DirectAudioDownloader.Paused) { job.state = "Pausado" }
            catch (error: Exception) { job.state = "Error: ${error.message}" }
            persist()
        }
    }
    private fun uniqueName(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        for (i in 2..999) {
            val candidate = File(file.parentFile, "$base ($i).${file.extension}")
            if (!candidate.exists()) return candidate
        }
        throw IllegalStateException("Demasiadas copias del mismo nombre")
    }
}
