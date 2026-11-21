package com.veltrax.music.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.veltrax.core.DirectAudioDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(NAME) ?: return@withContext Result.failure()
        val downloadId = inputData.getString(ID) ?: return@withContext Result.failure()

        try {
            setForeground(foregroundInfo("Preparando $fileName", 0))
            val downloader = DirectAudioDownloader()
            var lastUpdate = 0L

            val staged = downloader.download(
                url = url,
                stagingDirectory = File(applicationContext.filesDir, "pending_downloads"),
                downloadId = downloadId,
                requestedFilename = fileName,
                chunkCount = 3,
                stopped = { isStopped }
            ) { p ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 400 || p.phase == "Completado") {
                    lastUpdate = now
                    val percent = if (p.total > 0) {
                        (100 * p.transferred / p.total).toInt().coerceIn(0, 100)
                    } else 0
                    setProgressAsync(
                        Data.Builder()
                            .putInt(PROGRESS, percent)
                            .putLong(BYTES, p.transferred)
                            .putLong(TOTAL, p.total)
                            .build()
                    )
                    setForegroundAsync(foregroundInfo("$fileName · $percent%", percent))
                }
            }

            if (isStopped) return@withContext Result.failure()

            publishToMediaStore(staged, fileName)
            staged.delete()
            Result.success(Data.Builder().putString("saved", fileName).build())
        } catch (_: DirectAudioDownloader.Paused) {
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 2 && !isStopped) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString("error", e.message?.take(180) ?: "Error de descarga")
                        .build()
                )
            }
        }
    }

    private fun publishToMediaStore(source: File, requestedName: String) {
        val safeName = DirectAudioDownloader.safeFileName(requestedName)
        val ext = safeName.substringAfterLast('.', "mp3").lowercase()
        val mime = when (ext) {
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            else -> "audio/mpeg"
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/VeltraX")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }

        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("No se pudo crear el archivo en Music/VeltraX")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            } ?: throw IllegalStateException("No se pudo guardar la canción")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null
            )
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            throw e
        }
    }

    private fun foregroundInfo(text: String, progress: Int): ForegroundInfo {
        val channel = "veltrax_downloads"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channel, "Descargas VeltraX", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notice = NotificationCompat.Builder(applicationContext, channel)
            .setContentTitle("VeltraX · descarga")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()

        val id = id.hashCode().and(0x7fffffff).coerceAtLeast(1000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notice)
        }
    }

    companion object {
        const val URL = "url"
        const val NAME = "name"
        const val ID = "id"
        const val PROGRESS = "progress"
        const val BYTES = "bytes"
        const val TOTAL = "total"
    }
}
