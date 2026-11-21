package com.veltrax.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.WorkInfo
import com.veltrax.music.MainViewModel
import com.veltrax.music.data.LibraryStore
import com.veltrax.music.data.Song
import com.veltrax.music.download.DownloadWorker
import kotlinx.coroutines.delay

// ——— Personalización VeltraX (sin cambios) ———
private val Background = Color(0xFF0A0912)
private val CardBg = Color(0xFF191625)
private val Purple = Color(0xFFB15CFF)
private val Violet = Color(0xFF7525F5)
private val Muted = Color(0xFFB3AABF)
private val TextColor = Color(0xFFF8F5FF)
private val gradient = Brush.linearGradient(
    listOf(Color(0xFF6022AD), Color(0xFFB34BFF), Color(0xFF341568))
)
private val colors = darkColorScheme(
    primary = Purple,
    secondary = Violet,
    background = Background,
    surface = Background,
    surfaceVariant = CardBg,
    onBackground = TextColor,
    onSurface = TextColor,
    onPrimary = Color.White,
    outline = Color(0xFF443A57)
)

private enum class Tab(val label: String) {
    HOME("Inicio"),
    SEARCH("Buscar"),
    LIBRARY("Biblioteca"),
    DOWNLOADS("Descargas")
}

@Composable
fun VeltraXScreen(
    model: MainViewModel,
    hasPermission: Boolean,
    onPermission: () -> Unit
) {
    val songs by model.songs.collectAsState()
    val favorites by model.favorites.collectAsState()
    val playlists by model.playlists.collectAsState()
    val downloads by model.downloads.collectAsState()
    val states by model.workStates.collectAsState()
    val playing by model.nowPlaying.collectAsState()
    val isPlaying by model.isPlaying.collectAsState()
    val position by model.position.collectAsState()
    val duration by model.duration.collectAsState()

    var selected by remember { mutableStateOf(Tab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var addSong by remember { mutableStateOf<Song?>(null) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            model.refreshProgress()
            model.refreshDownloads()
            delay(1000)
        }
    }

    LaunchedEffect(states) {
        if (states.values.any { it.state == WorkInfo.State.SUCCEEDED }) {
            model.refreshSongs()
        }
    }

    MaterialTheme(colorScheme = colors, typography = Typography()) {
        Scaffold(
            containerColor = Background,
            topBar = { BrandBar() },
            bottomBar = {
                Column {
                    if (playing != null) {
                        MiniPlayer(
                            song = playing!!,
                            playing = isPlaying,
                            onOpen = { showPlayer = true },
                            onToggle = model::togglePlayback,
                            onNext = model::next
                        )
                    }
                    NavigationBar(
                        containerColor = Color(0xFF110E1B),
                        tonalElevation = 0.dp
                    ) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selected == tab,
                                onClick = {
                                    selected = tab
                                    selectedPlaylist = null
                                },
                                icon = {
                                    Icon(
                                        when (tab) {
                                            Tab.HOME -> Icons.Default.Home
                                            Tab.SEARCH -> Icons.Default.Search
                                            Tab.LIBRARY -> Icons.Default.LibraryMusic
                                            Tab.DOWNLOADS -> Icons.Default.Download
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(tab.label, maxLines = 1, fontSize = 11.sp)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Purple,
                                    indicatorColor = Violet,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            }
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                if (!hasPermission) {
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Acceso a música local", fontWeight = FontWeight.Bold)
                            Text(
                                "VeltraX solo indexa archivos de audio almacenados en este dispositivo.",
                                color = Muted
                            )
                            Button(onClick = onPermission) { Text("Permitir acceso") }
                        }
                    }
                }
                when (selected) {
                    Tab.HOME -> HomePage(
                        songs, playlists, model,
                        onSelectPlaylist = {
                            selected = Tab.LIBRARY
                            selectedPlaylist = it
                        },
                        onAddSong = { addSong = it }
                    )
                    Tab.SEARCH -> SearchPage(
                        songs, favorites, model,
                        onAddSong = { addSong = it }
                    )
                    Tab.LIBRARY -> LibraryPage(
                        songs, favorites, playlists, selectedPlaylist, model,
                        onPlaylist = { selectedPlaylist = it },
                        onCreate = { createPlaylist = true },
                        onAddSong = { addSong = it }
                    )
                    Tab.DOWNLOADS -> DownloadsPage(downloads, states, model)
                }
            }
        }

        if (showPlayer && playing != null) {
            Dialog(
                onDismissRequest = { showPlayer = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                PlayerSheet(
                    song = playing!!,
                    playing = isPlaying,
                    position = position,
                    duration = duration,
                    favorite = favorites.contains(playing!!.uri),
                    onClose = { showPlayer = false },
                    onToggle = model::togglePlayback,
                    onNext = model::next,
                    onPrevious = model::previous,
                    onSeek = { model.seek(it) },
                    onFavorite = { model.toggleFavorite(playing!!.uri) }
                )
            }
        }

        addSong?.let { song ->
            AlertDialog(
                onDismissRequest = { addSong = null },
                title = { Text("Añadir a playlist") },
                text = {
                    Column {
                        if (playlists.isEmpty()) {
                            Text("Crea primero una playlist desde Biblioteca.")
                        }
                        playlists.keys.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    model.addToPlaylist(playlist, song.uri)
                                    addSong = null
                                }
                            ) { Text(playlist) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { addSong = null }) { Text("Cerrar") }
                }
            )
        }

        if (createPlaylist) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { createPlaylist = false },
                title = { Text("Nueva playlist") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = name.trim().isNotEmpty(),
                        onClick = {
                            model.createPlaylist(name)
                            createPlaylist = false
                        }
                    ) { Text("Crear") }
                },
                dismissButton = {
                    TextButton(onClick = { createPlaylist = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun BrandBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 19.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(43.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text("V", fontWeight = FontWeight.Black, fontSize = 25.sp, color = Color.White)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Veltra", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("X", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Purple)
            }
            Text(
                "M Ú S I C A  S I N  L Í M I T E S",
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                color = Muted
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Headphones, contentDescription = null, tint = Purple)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 17.dp)) {
        Text(title, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun HomePage(
    songs: List<Song>,
    playlists: Map<String, List<String>>,
    model: MainViewModel,
    onSelectPlaylist: (String) -> Unit,
    onAddSong: (Song) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { PageHeading("Inicio", "Toda tu música, siempre contigo") }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(174.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(gradient)
                    .clickable { songs.firstOrNull()?.let { model.play(it, songs) } }
                    .padding(24.dp)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "✦ TU BIBLIOTECA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = .85f)
                    )
                    Column {
                        Text(
                            "Escucha sin límites",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            "${songs.size} canciones reales en tu dispositivo",
                            color = Color.White.copy(alpha = .82f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color.White)
                        Text(
                            "  Reproducir mi música",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        item { SectionTitle("Añadidas recientemente") }
        if (songs.isEmpty()) {
            item {
                EmptyState(
                    "Todavía no hay canciones",
                    "Concede permiso de audio o descarga un archivo de audio directo."
                )
            }
        }
        items(songs.take(8), key = { "home_${it.uri}" }) { song ->
            SongRow(song, model, songs, onAddSong)
        }
        item { SectionTitle("Tus playlists") }
        if (playlists.isEmpty()) {
            item {
                EmptyState(
                    "Crea tu primera playlist",
                    "Desde Biblioteca puedes organizar las canciones."
                )
            }
        }
        items(playlists.keys.toList(), key = { "home_pl_$it" }) { name ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBg)
                    .clickable { onSelectPlaylist(name) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.QueueMusic, null, tint = Purple)
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(name, fontWeight = FontWeight.Bold)
                    Text(
                        "${playlists[name]?.size ?: 0} canciones",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPage(
    songs: List<Song>,
    favorites: Set<String>,
    model: MainViewModel,
    onAddSong: (Song) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todo") }
    val results = remember(songs, text, filter, favorites) {
        val query = text.trim()
        songs.filter { song ->
            (query.isEmpty() || listOf(song.title, song.artist, song.album)
                .any { it.contains(query, ignoreCase = true) }) &&
                (filter != "Favoritos" || favorites.contains(song.uri))
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { PageHeading("Buscar", "Encuentra tus archivos musicales locales") }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Canción, artista o álbum") },
                shape = RoundedCornerShape(19.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Todo", "Favoritos").forEach { key ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(key) }
                    )
                }
            }
        }
        item {
            Text("${results.size} resultados locales", color = Muted, fontSize = 13.sp)
        }
        items(results, key = { "search_${it.uri}" }) { song ->
            SongRow(song, model, results, onAddSong)
        }
        if (results.isEmpty()) {
            item {
                EmptyState(
                    "Sin resultados",
                    "Busca música que realmente esté guardada en tu teléfono."
                )
            }
        }
    }
}

@Composable
private fun LibraryPage(
    songs: List<Song>,
    favorites: Set<String>,
    playlists: Map<String, List<String>>,
    playlist: String?,
    model: MainViewModel,
    onPlaylist: (String?) -> Unit,
    onCreate: () -> Unit,
    onAddSong: (Song) -> Unit
) {
    var tab by remember { mutableStateOf("Canciones") }
    val available = if (playlist != null) {
        songs.filter { playlists[playlist].orEmpty().contains(it.uri) }
    } else when (tab) {
        "Favoritos" -> songs.filter { it.uri in favorites }
        else -> songs
    }

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            if (playlist == null) {
                PageHeading("Biblioteca", "Tus canciones, álbumes y favoritos")
            } else {
                TextButton(onClick = { onPlaylist(null) }) {
                    Icon(Icons.Default.ArrowBack, null)
                    Text("  Biblioteca / $playlist")
                }
            }
        }
        if (playlist == null) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Canciones", "Álbumes", "Favoritos", "Playlists").forEach { value ->
                        FilterChip(
                            selected = tab == value,
                            onClick = { tab = value },
                            label = { Text(value) }
                        )
                    }
                }
            }
            when (tab) {
                "Playlists" -> {
                    item {
                        Button(onClick = onCreate) {
                            Icon(Icons.Default.Add, null)
                            Text("  Crear playlist")
                        }
                    }
                    items(playlists.keys.toList(), key = { "lib_pl_$it" }) { name ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBg)
                                .clickable { onPlaylist(name) }
                                .padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueueMusic, null, tint = Purple)
                            Text("   $name", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${playlists[name]?.size ?: 0}", color = Muted)
                            IconButton(onClick = { model.deletePlaylist(name) }) {
                                Icon(Icons.Default.Delete, "Eliminar", tint = Muted)
                            }
                        }
                    }
                }
                "Álbumes" -> {
                    val albums = songs.groupBy { it.album.ifBlank { "Sin álbum" } }
                    items(albums.toList(), key = { "album_${it.first}" }) { (name, tracks) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg)
                                .clickable { tracks.firstOrNull()?.let { model.play(it, tracks) } }
                                .padding(18.dp)
                        ) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text(
                                "${tracks.size} canciones · ${tracks.firstOrNull()?.artist.orEmpty()}",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                else -> {
                    item {
                        Text("${available.size} canciones", fontSize = 13.sp, color = Muted)
                    }
                    items(available, key = { "library_${it.uri}" }) { song ->
                        SongRow(song, model, available, onAddSong)
                    }
                }
            }
        } else {
            item { Text("${available.size} canciones", color = Muted) }
            items(available, key = { "playlist_${it.uri}" }) { song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(song, model, available, onAddSong)
                    }
                    IconButton(onClick = { model.removeFromPlaylist(playlist, song.uri) }) {
                        Icon(Icons.Default.Close, "Quitar", tint = Muted)
                    }
                }
            }
        }
        if ((playlist != null && available.isEmpty()) ||
            (playlist == null && tab == "Playlists" && playlists.isEmpty())
        ) {
            item {
                EmptyState("Aquí aún no hay música", "Añade canciones desde la Biblioteca.")
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    model: MainViewModel,
    queue: List<Song>,
    onAdd: (Song) -> Unit
) {
    val favorites by model.favorites.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 69.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(gradient)
                .clickable { model.play(song, queue) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable { model.play(song, queue) }
        ) {
            Text(
                song.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                fontSize = 13.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { model.toggleFavorite(song.uri) }) {
            Icon(
                if (song.uri in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorito",
                tint = if (song.uri in favorites) Purple else Muted
            )
        }
        IconButton(onClick = { onAdd(song) }) {
            Icon(Icons.Default.PlaylistAdd, "Añadir a playlist", tint = Muted)
        }
    }
    HorizontalDivider(color = Color(0xFF272131), thickness = .5.dp)
}

@Composable
private fun DownloadsPage(
    downloads: List<LibraryStore.DownloadItem>,
    states: Map<String, WorkInfo>,
    model: MainViewModel
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val valid = remember(url) {
        try {
            val u = java.net.URI(url.trim())
            u.scheme in listOf("http", "https") && !u.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { PageHeading("Descargas", "Múltiples archivos, transferencia por segmentos") }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(21.dp))
                    .background(CardBg)
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Nueva descarga", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("URL directa del audio") },
                    placeholder = { Text("https://servidor.com/audio.mp3") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre del archivo") },
                    placeholder = { Text("Mi canción.mp3") }
                )
                Button(
                    enabled = valid && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        model.enqueue(url, name)
                        url = ""
                        name = ""
                    }
                ) {
                    Icon(Icons.Default.Download, null)
                    Text("  Agregar a la cola")
                }
                Text(
                    "Funciona con enlaces de archivos directos. No extrae audio de YouTube, Spotify ni archivos con DRM.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
        item { SectionTitle("Cola de descargas") }
        if (downloads.isEmpty()) {
            item {
                EmptyState(
                    "No hay descargas",
                    "Añade un enlace directo a MP3, M4A, OGG, FLAC o WAV."
                )
            }
        }
        items(downloads, key = { it.id }) { entry ->
            val info = states[entry.id]
            val state = info?.state
            val progress = info?.progress?.getInt(DownloadWorker.PROGRESS, 0) ?: 0
            val status = when (state) {
                WorkInfo.State.RUNNING -> "Descargando · $progress%"
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "En cola"
                WorkInfo.State.SUCCEEDED -> "Completado · Music/VeltraX"
                WorkInfo.State.FAILED -> info.outputData.getString("error") ?: "Falló"
                WorkInfo.State.CANCELLED -> "Pausado"
                else -> "Listo para reanudar"
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBg)
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.fileName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(status, color = Muted, fontSize = 12.sp, maxLines = 2)
                    }
                    when {
                        state == WorkInfo.State.RUNNING ||
                            state == WorkInfo.State.ENQUEUED ||
                            state == WorkInfo.State.BLOCKED -> {
                            IconButton(onClick = { model.pause(entry) }) {
                                Icon(Icons.Default.Pause, "Pausar")
                            }
                        }
                        state != WorkInfo.State.SUCCEEDED -> {
                            IconButton(onClick = { model.resume(entry) }) {
                                Icon(Icons.Default.Refresh, "Reanudar")
                            }
                        }
                    }
                    IconButton(onClick = { model.removeDownload(entry) }) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = Muted)
                    }
                }
                if (state == WorkInfo.State.RUNNING) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2B1E41))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(gradient)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
        ) {
            Text(
                song.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Text(song.artist, fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                "Reproducir o pausar",
                tint = Purple,
                modifier = Modifier.size(35.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, "Siguiente")
        }
    }
}

@Composable
private fun PlayerSheet(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    favorite: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavorite: () -> Unit
) {
    var scrub by remember(song.uri) { mutableFloatStateOf(-1f) }
    val percent = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val pulse = rememberInfiniteTransition(label = "vinyl")
    val rotation by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing)
        ),
        label = "vinylRotation"
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val discSize = maxWidth * .78f
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF9A4A9B),
                            Color(0xFF32172F),
                            Color.Black,
                            Color.Black
                        )
                    )
                )
                .padding(horizontal = 25.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        "Cerrar reproductor",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "REPRODUCIENDO",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favorito",
                        tint = if (favorite) Purple else Color.White
                    )
                }
            }
            Spacer(Modifier.height(15.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.weight(.55f))
            Box(
                Modifier
                    .size(discSize)
                    .shadow(24.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF5B3A70), Color(0xFF20162B), Color(0xFF09070D))
                        )
                    )
                    .then(if (playing) Modifier.rotate(rotation) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize(0.94f)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(50))
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF5B405F), Color(0xFF17121E), Color.Black)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize(0.79f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFE6A8D8),
                                        Color(0xFFB8A4E8),
                                        Color(0xFF5A3477)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            tint = Color.White.copy(alpha = .92f),
                            modifier = Modifier.size(discSize * .23f)
                        )
                    }
                }
                CircularProgressIndicator(
                    progress = { if (scrub >= 0f) scrub else percent },
                    modifier = Modifier
                        .fillMaxSize(0.99f)
                        .padding(5.dp),
                    color = Color(0xFFB8A5FF),
                    trackColor = Color.White.copy(alpha = .10f),
                    strokeWidth = 2.dp
                )
            }
            Spacer(Modifier.weight(.65f))
            Slider(
                value = if (scrub >= 0f) scrub else percent,
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    onSeek((scrub * duration).toLong())
                    scrub = -1f
                },
                enabled = duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFB8A5FF),
                    inactiveTrackColor = Color.White.copy(alpha = .22f)
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    clock(position),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = .65f)
                )
                Text(
                    clock(duration),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = .65f)
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(66.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Reproducir o pausar",
                        modifier = Modifier.size(35.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(description, color = Muted, fontSize = 13.sp)
    }
}

private fun clock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
