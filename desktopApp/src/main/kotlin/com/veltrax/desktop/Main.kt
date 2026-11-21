package com.veltrax.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

private val purple = Color(0xFFB15CFF)
private val gradient = Brush.linearGradient(listOf(Color(0xFF39166C), Color(0xFF9C30EF)))
private val dark = darkColorScheme(primary = purple, background = Color(0xFF090911), surface = Color(0xFF10101C),
    surfaceVariant = Color(0xFF21192E), onBackground = Color.White, onSurface = Color.White)

fun main() = application {
    val storage = remember { DesktopStorage() }
    val player = remember { DesktopPlayer() }
    var songs by remember { mutableStateOf(storage.scan()) }
    var version by remember { mutableIntStateOf(0) }
    val downloads = remember { DesktopDownloadQueue(storage) { songs = storage.scan() } }
    var floating by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(true) }
    Window(onCloseRequest = { player.close(); exitApplication() }, title = "VeltraX · Tu música",
        state = rememberWindowState(width = 1100.dp, height = 760.dp)) {
        MaterialTheme(colorScheme = dark) {
            DesktopScreen(storage, player, downloads, songs, version, onRefresh = { songs = storage.scan(); version++ },
                onMini = { floating = !floating })
        }
    }
    if (floating) Window(onCloseRequest = { floating = false }, title = "VeltraX · Mini", alwaysOnTop = true, undecorated = false,
        state = rememberWindowState(width = 480.dp, height = 125.dp)) {
        MaterialTheme(colorScheme = dark) {
            Row(Modifier.fillMaxSize().background(Color(0xFF21192E)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = purple)
                Text("  ${player.selected?.title ?: "Sin reproducción"}", modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { player.previous() }) { Icon(Icons.Default.SkipPrevious, null) }
                IconButton(onClick = { player.toggle() }) { Icon(Icons.Default.PlayCircle, null, tint = purple) }
                IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, null) }
            }
        }
    }
}

@Composable
private fun DesktopScreen(storage: DesktopStorage, player: DesktopPlayer, downloads: DesktopDownloadQueue,
                          songs: List<DesktopSong>, version: Int, onRefresh: () -> Unit, onMini: () -> Unit) {
    var tab by remember { mutableStateOf("Inicio") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Canciones") }
    var playlist by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var focusedSong by remember { mutableStateOf<DesktopSong?>(null) }
    var now by remember { mutableStateOf<DesktopSong?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            now = player.selected; isPlaying = player.playing; progress = player.positionMs(); duration = player.durationMs()
            kotlinx.coroutines.delay(700)
        }
    }
    fun chooseDirectory() {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Seleccionar carpeta musical" }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                storage.roots.add(chooser.selectedFile.absolutePath)
                storage.save()
                onRefresh()
            }
        }
    }
    Row(Modifier.fillMaxSize().background(Color(0xFF0A0912))) {
        Column(Modifier.width(215.dp).fillMaxHeight().background(Color(0xFF140F1E)).padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("◈ VeltraX", fontWeight = FontWeight.Black, fontSize = 28.sp, color = purple)
            Text("MÚSICA SIN LÍMITES", color = Color.LightGray, fontSize = 10.sp)
            Spacer(Modifier.height(20.dp))
            listOf("Inicio", "Buscar", "Biblioteca", "Playlists", "Descargas").forEach { item ->
                NavigationDrawerItem(label = { Text(item) }, selected = tab == item, onClick = { tab = item; playlist = null },
                    icon = { Icon(when(item) {
                        "Inicio" -> Icons.Default.Home
                        "Buscar" -> Icons.Default.Search
                        "Biblioteca" -> Icons.Default.LibraryMusic
                        "Playlists" -> Icons.Default.QueueMusic
                        else -> Icons.Default.Download
                    }, null) })
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { chooseDirectory() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Text(" Carpetas") }
            Text("${songs.size} archivos locales", fontSize = 11.sp, color = Color.LightGray)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                Text(tab, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("Tu música local · Windows", color = Color.LightGray)
                Spacer(Modifier.height(18.dp))
                when(tab) {
                    "Inicio" -> {
                        Column(Modifier.fillMaxWidth().height(155.dp).background(gradient, RoundedCornerShape(23.dp)).clickable { songs.firstOrNull()?.let { player.play(it, songs) } }.padding(25.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("TU COLECCIÓN MUSICAL", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${songs.size} canciones completas", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("▶  Reproducir desde el dispositivo", color = Color.White)
                        }
                        Spacer(Modifier.height(15.dp))
                        SongListing(songs.take(20), player, storage, onUpdate = onRefresh, onPlaylist = { focusedSong = it })
                    }
                    "Buscar" -> {
                        OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Buscar entre archivos del dispositivo") })
                        Spacer(Modifier.height(15.dp))
                        SongListing(songs.filter { listOf(it.title,it.artist,it.file.name).any { s -> s.contains(query, true) } },
                            player, storage, onUpdate = onRefresh, onPlaylist = { focusedSong = it })
                    }
                    "Biblioteca" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Canciones", "Favoritos", "Álbumes").forEach { item ->
                                FilterChip(filter == item, onClick = { filter = item }, label = { Text(item) })
                            }
                        }
                        if (filter == "Álbumes") {
                            Text("Las carpetas se usan como agrupaciones de álbumes.", color = Color.Gray)
                            LazyColumn {
                                items(songs.groupBy { it.file.parentFile?.name ?: "Sin carpeta" }.toList()) { (folder, tracks) ->
                                    TextButton(onClick = { tracks.firstOrNull()?.let { player.play(it, tracks) } }) {
                                        Icon(Icons.Default.Album, null); Text("  $folder · ${tracks.size} pistas")
                                    }
                                }
                            }
                        } else SongListing(if (filter == "Favoritos") songs.filter { it.id in storage.favorites } else songs,
                            player, storage, onUpdate = onRefresh, onPlaylist = { focusedSong = it })
                    }
                    "Playlists" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Nueva playlist") }, singleLine = true)
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = { if (name.isNotBlank()) { storage.playlists.putIfAbsent(name.trim(), mutableSetOf()); storage.save(); name=""; onRefresh() } }) {
                                Icon(Icons.Default.Add, null); Text(" Crear")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (playlist == null) {
                            LazyColumn { items(storage.playlists.keys.toList()) { list ->
                                TextButton(onClick = { playlist = list }) { Icon(Icons.Default.QueueMusic, null); Text("  $list · ${storage.playlists[list]?.size ?: 0} canciones") }
                            } }
                        } else {
                            TextButton(onClick = { playlist = null }) { Text("← Mis playlists / $playlist") }
                            SongListing(songs.filter { it.id in storage.playlists[playlist].orEmpty() }, player, storage,
                                onUpdate = onRefresh, onPlaylist = { focusedSong = it })
                        }
                    }
                    "Descargas" -> {
                        Column(Modifier.fillMaxWidth().background(Color(0xFF21192E), RoundedCornerShape(18.dp)).padding(16.dp)) {
                            Text("URL directa de audio", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("https://.../cancion.mp3") }, singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(filename, onValueChange = { filename = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre de archivo") }, singleLine = true)
                            Spacer(Modifier.height(10.dp))
                            Button(enabled = url.startsWith("https://") || url.startsWith("http://"), onClick = {
                                try { downloads.enqueue(url, filename.ifBlank { "audio.mp3" }); url=""; filename="" }
                                catch (_: IllegalArgumentException) { }
                            }) { Text("Agregar a cola (3 tareas simultáneas)") }
                            Text("Sin descargas desde webs protegidas, DRM o extractores de plataformas.", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(13.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(downloads.jobs) { job ->
                                Column(Modifier.fillMaxWidth().background(Color(0xFF21192E), RoundedCornerShape(16.dp)).padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(job.name, fontWeight = FontWeight.Bold)
                                            Text(job.state, color = Color.LightGray, fontSize = 12.sp)
                                        }
                                        if (job.state == "Descargando" || job.state == "En cola") {
                                            IconButton(onClick = { downloads.pause(job) }) { Icon(Icons.Default.Pause, "Pausar") }
                                        } else if (job.state != "Completado" && job.state != "Pausando…") {
                                            IconButton(onClick = { downloads.resume(job) }) { Icon(Icons.Default.Refresh, "Reanudar") }
                                        }
                                    }
                                    if (job.total > 0 && job.state == "Descargando") LinearProgressIndicator(progress = { (job.transferred.toFloat()/job.total).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(94.dp).background(Color(0xFF23172F)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = purple, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(165.dp)) {
                    Text(now?.title ?: "Sin canción", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(now?.artist ?: "Elige un archivo para empezar", color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { player.previous() }) { Icon(Icons.Default.SkipPrevious, null) }
                IconButton(onClick = { player.toggle() }) { Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = purple, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, null) }
                Spacer(Modifier.width(12.dp))
                Slider(value = if (duration > 0) (progress.toFloat()/duration).coerceIn(0f,1f) else 0f,
                    onValueChange = { player.seek(it.toDouble()) }, enabled = duration > 0, modifier = Modifier.weight(1f))
                IconButton(onClick = onMini) { Icon(Icons.Default.OpenInNew, "Reproductor flotante") }
            }
        }
    }
    focusedSong?.let { song ->
        AlertDialog(onDismissRequest = { focusedSong = null }, title = { Text("Añadir a una playlist") },
            text = { Column {
                storage.playlists.keys.forEach { list -> TextButton(onClick = {
                    storage.playlists[list]?.add(song.id); storage.save(); onRefresh(); focusedSong = null
                }) { Text(list) } }
                if (storage.playlists.isEmpty()) Text("Crea primero una playlist.")
            } }, confirmButton = { TextButton(onClick = { focusedSong = null }) { Text("Cerrar") } })
    }
}

@Composable
private fun SongListing(songs: List<DesktopSong>, player: DesktopPlayer, store: DesktopStorage,
                        onUpdate: () -> Unit, onPlaylist: (DesktopSong) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(songs, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth().background(Color(0xFF191523), RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = purple, modifier = Modifier.size(40.dp).clickable { player.play(song, songs) })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { player.play(song, songs) }) {
                    Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, fontSize = 12.sp, color = Color.LightGray)
                }
                IconButton(onClick = {
                    if (!store.favorites.add(song.id)) store.favorites.remove(song.id)
                    store.save(); onUpdate()
                }) { Icon(if (song.id in store.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = purple) }
                IconButton(onClick = { onPlaylist(song) }) { Icon(Icons.Default.PlaylistAdd, "Playlist") }
            }
        }
    }
}
