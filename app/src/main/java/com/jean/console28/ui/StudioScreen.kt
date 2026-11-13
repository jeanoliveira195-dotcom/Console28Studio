package com.jean.console28.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jean.console28.audio.ConsoleSettings
import com.jean.console28.audio.RecordingInfo
import com.jean.console28.library.MusicTrack
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.max

// Anodized charcoal, aged champagne brass and the amber LCD of late-70s/80s hi-fi.
private val Ink = Color(0xFF121716)
private val Panel = Color(0xFF202927)
private val PanelDeep = Color(0xFF181F1E)
private val Border = Color(0xFF3A4540)
private val Brass = Color(0xFFD4B47B)
private val Light = Color(0xFFF3EBDD)
private val Soft = Color(0xFFAAB2A9)
private val Accent = Color(0xFFE5C68D)
private val Danger = Color(0xFFE48A7D)

@Composable
fun StudioScreen(
    tab: Int,
    onTab: (Int) -> Unit,
    settings: ConsoleSettings,
    onSettings: (ConsoleSettings) -> Unit,
    tracks: List<MusicTrack>,
    favorites: Set<Long>,
    playlists: Map<String, List<Long>>,
    hasMusicPermission: Boolean,
    libraryLoading: Boolean,
    libraryError: String?,
    onRequestMusicPermission: () -> Unit,
    onRefreshMusic: () -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onFavorite: (Long) -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onDeletePlaylist: (String) -> Unit,
    onTogglePlaylistTrack: (String, Long) -> Unit,
    recording: Boolean,
    recordings: List<RecordingInfo>,
    currentRecording: RecordingInfo?,
    onChooseRecording: (RecordingInfo) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: (RecordingInfo) -> Unit,
    onExportProcessed: (RecordingInfo) -> Unit,
    onExportOriginal: (RecordingInfo) -> Unit,
    onPickMusic: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    isPlaying: Boolean,
    isProcessedPreview: Boolean,
    title: String,
    artist: String,
    currentTrackId: Long?,
    message: String,
    positionMs: () -> Long,
    durationMs: () -> Long,
    onSeek: (Long) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var chosenCollection by rememberSaveable { mutableStateOf("Todas") }
    var newPlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var fineTuneOpen by rememberSaveable { mutableStateOf(false) }
    val collections = listOf("Todas", "Adicionadas", "Favoritas") + playlists.keys.sorted()
    LaunchedEffect(playlists) {
        if (chosenCollection !in collections) chosenCollection = "Todas"
    }
    val baseTracks = when (chosenCollection) {
        "Favoritas" -> tracks.filter { it.id in favorites }
        "Adicionadas" -> tracks.sortedByDescending { it.addedSeconds }
        "Todas" -> tracks
        else -> {
            val byId = tracks.associateBy { it.id }
            playlists[chosenCollection].orEmpty().mapNotNull { byId[it] }
        }
    }
    val displayed = baseTracks.filter {
        search.isBlank() || it.title.contains(search, true) ||
            it.artist.contains(search, true) || it.album.contains(search, true)
    }
    val scheme = darkColorScheme(
        primary = Brass, onPrimary = Ink, background = Ink,
        surface = Panel, onSurface = Light, onBackground = Light,
        secondary = Accent, outline = Border
    )

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                Column {
                    if (tab != 0) {
                        MiniPlayer(title, artist, isPlaying, onTogglePlay, onNext)
                    }
                    NavigationBar(containerColor = PanelDeep, tonalElevation = 0.dp) {
                        listOf(
                            Triple("Biblioteca", Icons.Rounded.LibraryMusic, 0),
                            Triple("Playlists", Icons.Rounded.QueueMusic, 1),
                            Triple("Estúdio", Icons.Rounded.Mic, 2)
                        ).forEach { (label, icon, index) ->
                            NavigationBarItem(
                                selected = tab == index, onClick = { onTab(index) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brass, selectedTextColor = Brass,
                                    indicatorColor = Color(0xFF343E37),
                                    unselectedIconColor = Soft, unselectedTextColor = Soft
                                )
                            )
                        }
                    }
                }
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding)
                    .padding(horizontal = 18.dp)
            ) {
                Header()
                when (tab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                NowPlaying(title, artist, isPlaying, isProcessedPreview,
                                    currentTrackId != null && currentTrackId in favorites,
                                    onTogglePlay, onPrevious, onNext,
                                    onFavorite = { currentTrackId?.let(onFavorite) },
                                    positionMs, durationMs, onSeek)
                            }
                            item {
                                QuickSoundStrip(settings, onSettings)
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Label("SUA COLEÇÃO")
                                        Text("${tracks.size} faixas no dispositivo", color = Soft, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = onRefreshMusic, enabled = !libraryLoading) {
                                        Icon(Icons.Rounded.Refresh, "Atualizar biblioteca", tint = Brass)
                                    }
                                }
                            }
                            item {
                                LibraryPermission(hasMusicPermission, libraryLoading, libraryError,
                                    onRequestMusicPermission, onPickMusic)
                            }
                            item {
                                SearchBox(search, { search = it })
                            }
                            item {
                                CollectionTabs(collections, chosenCollection, { chosenCollection = it },
                                    onCreate = { newPlaylistDialog = true })
                            }
                            if (displayed.isEmpty()) {
                                item {
                                    EmptyState(if (search.isNotBlank()) "Nenhuma faixa corresponde à busca."
                                    else if (tracks.isEmpty()) "Suas músicas aparecerão aqui após a leitura."
                                    else "Ainda não há músicas nesta coleção.")
                                }
                            }
                            items(displayed, key = { it.id }) { track ->
                                TrackRow(track, track.id == currentTrackId,
                                    track.id in favorites,
                                    onPlay = { onPlayTrack(track, displayed) },
                                    onFavorite = { onFavorite(track.id) },
                                    onAdd = { addToPlaylistTrack = track })
                            }
                            item { Footnote() }
                        }
                    }
                    1 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Playlists", color = Light, fontWeight = FontWeight.SemiBold, fontSize = 25.sp)
                                        Text("Suas seleções, guardadas offline", color = Soft, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { newPlaylistDialog = true }) {
                                        Icon(Icons.Rounded.AddCircleOutline, "Criar playlist", tint = Brass)
                                    }
                                }
                            }
                            item {
                                CollectionTabs(collections, chosenCollection, { chosenCollection = it },
                                    onCreate = { newPlaylistDialog = true })
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(chosenCollection, color = Light, fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (chosenCollection in playlists) {
                                        TextButton(onClick = { onDeletePlaylist(chosenCollection) }) {
                                            Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(16.dp))
                                            Text(" Excluir", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            item { SearchBox(search, { search = it }) }
                            if (displayed.isEmpty()) {
                                item { EmptyState("Nenhuma faixa aqui. Adicione músicas usando o botão + ao lado delas.") }
                            }
                            items(displayed, key = { it.id }) { track ->
                                TrackRow(track, track.id == currentTrackId,
                                    track.id in favorites,
                                    onPlay = { onPlayTrack(track, displayed) },
                                    onFavorite = { onFavorite(track.id) },
                                    onAdd = { addToPlaylistTrack = track })
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Estúdio de gravação", color = Light, fontSize = 25.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text("Narração limpa, com o caráter do nosso circuito analógico.",
                                color = Soft, fontSize = 13.sp)
                            RecorderCard(recording, currentRecording, recordings, onStartRecording,
                                onStopRecording, onChooseRecording, onPlayRecording,
                                onExportProcessed, onExportOriginal)
                            AnalogControls(settings, onSettings, !recording, fineTuneOpen,
                                onExpand = { fineTuneOpen = !fineTuneOpen })
                            Text(message, color = Soft, fontSize = 12.sp)
                            Footnote()
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (newPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { newPlaylistDialog = false },
            title = { Text("Nova playlist") },
            text = {
                Column {
                    OutlinedTextField(name, onValueChange = { name = it; error = false },
                        label = { Text("Nome da playlist") }, singleLine = true,
                        isError = error)
                    if (error) Text("Digite outro nome; ele pode já estar em uso.", color = Danger, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                if (onCreatePlaylist(name)) {
                    chosenCollection = name.trim().take(48)
                    newPlaylistDialog = false
                    onTab(1)
                } else error = true
            }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { newPlaylistDialog = false }) { Text("Cancelar") } }
        )
    }

    addToPlaylistTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { addToPlaylistTrack = null },
            title = { Text("Adicionar à playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(track.title, color = Soft, maxLines = 1)
                    if (playlists.isEmpty()) Text("Crie uma playlist para organizar suas faixas.", color = Soft)
                    playlists.forEach { (name, ids) ->
                        val inList = track.id in ids
                        TextButton(onClick = { onTogglePlaylistTrack(name, track.id) },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(if (inList) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                                contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(if (inList) "Remover" else "Adicionar", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addToPlaylistTrack = null }) { Text("Concluir") } },
            dismissButton = { TextButton(onClick = { addToPlaylistTrack = null; newPlaylistDialog = true }) {
                Text("Nova playlist")
            } }
        )
    }
}

@Composable
private fun QuickSoundStrip(s: ConsoleSettings, update: (ConsoleSettings) -> Unit) {
    Surface(shape = RoundedCornerShape(13.dp), color = PanelDeep,
        border = BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Tune, null, tint = Brass, modifier = Modifier.size(18.dp))
            Text("COR", color = Brass, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            listOf("Voz" to ConsoleSettings.narration(),
                "Música" to ConsoleSettings.music(), "Vintage" to ConsoleSettings.vintage())
                .forEach { (label, preset) ->
                    TextButton(onClick = { update(preset) },
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(label, color = if (s == preset) Accent else Soft, fontSize = 11.sp)
                    }
                }
            Switch(checked = s.enabled, onCheckedChange = { update(s.copy(enabled = it)) },
                modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 19.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Brass, Color(0xFF755E37)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.GraphicEq, null, tint = Ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("CONSOLE  28", color = Brass, fontWeight = FontWeight.Black,
                letterSpacing = 2.6.sp, fontSize = 19.sp)
            Text("ANALOG AUDIO  /  PERSONAL EDITION", color = Soft,
                letterSpacing = 1.3.sp, fontSize = 9.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF9FC18D)))
    }
}

@Composable
private fun NowPlaying(
    title: String, artist: String, isPlaying: Boolean, isProcessed: Boolean,
    isFavorite: Boolean, onTogglePlay: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onFavorite: () -> Unit,
    positionMs: () -> Long, durationMs: () -> Long, onSeek: (Long) -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            position = positionMs()
            duration = durationMs()
            delay(450L)
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Panel,
        border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("DIGITAL CD  •  ANALOG SOUND", modifier = Modifier.weight(1f))
                Text("STEREO", color = Soft, letterSpacing = 1.sp, fontSize = 10.sp)
            }
            Box(
                Modifier.fillMaxWidth().height(174.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF111816), Color(0xFF253029)))),
                contentAlignment = Alignment.Center
            ) {
                DiscArt(Modifier.size(145.dp))
                Text("CONSOLE / 28", color = Accent, letterSpacing = 1.4.sp, fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = Light, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Soft, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favoritar música", tint = if (isFavorite) Danger else Soft)
                }
            }
            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (duration > 0) onSeek((it * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(), enabled = duration > 0
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(position), color = Soft, fontSize = 11.sp)
                Text(clock(duration), color = Soft, fontSize = 11.sp)
            }
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Light, modifier = Modifier.size(29.dp))
                }
                Spacer(Modifier.width(21.dp))
                FilledIconButton(onClick = onTogglePlay,
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brass,
                        contentColor = Ink)) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (isPlaying) "Pausar" else "Reproduzir", modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(21.dp))
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", tint = Light, modifier = Modifier.size(29.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (isProcessed) Soft else Brass))
                Spacer(Modifier.width(7.dp))
                Text(if (isProcessed) "WAV JÁ PROCESSADO" else "RUST DSP  ·  EMULAÇÃO ANALÓGICA",
                    color = Soft, fontSize = 10.sp, letterSpacing = .7.sp)
            }
        }
    }
}

@Composable
private fun DiscArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * .47f
        val center = this.center
        drawCircle(brush = Brush.sweepGradient(listOf(
            Color(0xFF9D988B), Color(0xFFDED7C6), Color(0xFF786F67),
            Color(0xFFBCB3A2), Color(0xFFECE3C9), Color(0xFF9D988B)
        )), radius = radius)
        for (factor in listOf(.82f, .70f, .57f)) {
            drawCircle(color = Color(0x55FFFFFF), radius = radius * factor,
                style = Stroke(width = 1.1f))
        }
        drawCircle(color = Color(0xFF3F453D), radius = radius * .27f)
        drawCircle(color = Brass, radius = radius * .21f)
        drawCircle(color = Ink, radius = radius * .065f)
        drawLine(Color(0x55FFFFFF), Offset(center.x - radius * .68f, center.y),
            Offset(center.x - radius * .39f, center.y), 1f, cap = StrokeCap.Round)
    }
}

@Composable
private fun MiniPlayer(title: String, artist: String, isPlaying: Boolean,
                       onTogglePlay: () -> Unit, onNext: () -> Unit) {
    Surface(color = Panel, border = BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(39.dp).clip(RoundedCornerShape(9.dp))
                .background(PanelDeep), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Album, null, tint = Brass)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Light, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(artist, color = Soft, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pausar" else "Tocar", tint = Brass)
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Próxima", tint = Soft) }
        }
    }
}

@Composable
private fun LibraryPermission(
    granted: Boolean, loading: Boolean, error: String?, askPermission: () -> Unit,
    onPickMusic: () -> Unit
) {
    if (!granted || error != null || loading) {
        Surface(color = PanelDeep, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Border)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when {
                    loading -> "Lendo músicas do aparelho..."
                    error != null -> error
                    else -> "Autorize o acesso para encontrar músicas salvas no dispositivo."
                }, color = Soft, fontSize = 12.sp)
                if (!granted) {
                    TextButton(onClick = askPermission) { Text("Permitir acesso às músicas") }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, null, tint = Soft, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text("Não apareceu?", color = Soft, fontSize = 11.sp)
        TextButton(onClick = onPickMusic, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text("Abrir arquivo manualmente", fontSize = 11.sp)
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar música, álbum ou artista", color = Soft, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Soft) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Rounded.Close, "Limpar busca", tint = Soft)
            }
        },
        singleLine = true, shape = RoundedCornerShape(13.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brass, unfocusedBorderColor = Border,
            focusedTextColor = Light, unfocusedTextColor = Light,
            focusedContainerColor = PanelDeep, unfocusedContainerColor = PanelDeep
        )
    )
}

@Composable
private fun CollectionTabs(
    choices: List<String>, selected: String, onSelect: (String) -> Unit, onCreate: () -> Unit
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { name ->
            FilterChip(
                selected = selected == name, onClick = { onSelect(name) },
                label = { Text(name, maxLines = 1, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF4A4032),
                    selectedLabelColor = Accent, containerColor = PanelDeep,
                    labelColor = Soft
                ),
                border = BorderStroke(1.dp, if (selected == name) Brass else Border)
            )
        }
        FilterChip(selected = false, onClick = onCreate, label = {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
            Text(" Criar", fontSize = 12.sp)
        }, colors = FilterChipDefaults.filterChipColors(labelColor = Brass),
            border = BorderStroke(1.dp, Border))
    }
}

@Composable
private fun TrackRow(
    track: MusicTrack, isCurrent: Boolean, favorite: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit, onAdd: () -> Unit
) {
    Surface(
        color = if (isCurrent) Color(0xFF2B362E) else PanelDeep,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isCurrent) Brass.copy(alpha = .5f) else Border.copy(alpha = .7f))
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 67.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 11.dp).size(43.dp).clip(RoundedCornerShape(9.dp))
                .background(Panel), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Brass.copy(alpha = .85f))
            }
            Column(Modifier.weight(1f).clickable(onClick = onPlay).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text(track.title, color = if (isCurrent) Accent else Light,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium)
                Text("${track.artist} · ${clock(track.durationMs)}", color = Soft,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(39.dp)) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favoritar", tint = if (favorite) Danger else Soft,
                    modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(39.dp)) {
                Icon(Icons.Rounded.Add, "Adicionar a playlist", tint = Brass,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = PanelDeep,
        border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Brass, modifier = Modifier.size(32.dp))
            Text(text, color = Soft, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun RecorderCard(
    recording: Boolean, current: RecordingInfo?, recordings: List<RecordingInfo>,
    start: () -> Unit, stop: () -> Unit,
    onChoose: (RecordingInfo) -> Unit, onPreview: (RecordingInfo) -> Unit,
    onProcessed: (RecordingInfo) -> Unit, onOriginal: (RecordingInfo) -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) {
        if (recording) {
            elapsed = 0
            while (true) { delay(1000L); elapsed++ }
        }
    }
    PanelCard {
        Label("DECK DE GRAVAÇÃO  /  01")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape)
                .background(if (recording) Danger else Color(0xFF9FC18D)))
            Spacer(Modifier.width(10.dp))
            Text(if (recording) "REC  ${clock(elapsed * 1000L)}" else "PRONTO PARA GRAVAR",
                fontWeight = FontWeight.Bold, color = if (recording) Danger else Light,
                letterSpacing = 1.2.sp)
        }
        Text("Microfone · mono · WAV 16 bits · 48 kHz (ou 44,1 kHz)",
            color = Soft, fontSize = 12.sp)
        Text("Grava duas versões: original e tratada pelo motor Rust.",
            color = Soft, fontSize = 12.sp)
        Button(onClick = if (recording) stop else start,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording) Danger else Brass,
                contentColor = Ink
            )) {
            Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text(if (recording) "PARAR E SALVAR" else "INICIAR GRAVAÇÃO")
        }
    }
    if (current != null) {
        PanelCard {
            Label("ÚLTIMA NARRAÇÃO")
            Text(current.processed.name, color = Light, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text("${current.sampleRate} Hz  ·  ${sizeInMB(current.processed)}",
                color = Soft, fontSize = 12.sp)
            OutlinedButton(onClick = { onPreview(current) }, modifier = Modifier.fillMaxWidth(),
                enabled = !recording) { Icon(Icons.Rounded.PlayArrow, null); Text(" Ouvir tratada") }
            Button(onClick = { onProcessed(current) }, modifier = Modifier.fillMaxWidth(),
                enabled = !recording) { Icon(Icons.Rounded.FileDownload, null); Text(" Exportar WAV para vídeo") }
            OutlinedButton(onClick = { onOriginal(current) }, modifier = Modifier.fillMaxWidth(),
                enabled = !recording && current.original != null) { Text("Exportar original") }
        }
    }
    if (recordings.isNotEmpty()) {
        PanelCard {
            Label("GRAVAÇÕES ANTERIORES")
            recordings.take(15).forEach { item ->
                TextButton(onClick = { onChoose(item) }, enabled = !recording,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(item.processed.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AnalogControls(
    s: ConsoleSettings, update: (ConsoleSettings) -> Unit,
    enabled: Boolean, expanded: Boolean, onExpand: () -> Unit
) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("ANALOG COLOR ENGINE")
                Text("Processamento nativo em Rust", color = Soft, fontSize = 11.sp)
            }
            Switch(s.enabled, onCheckedChange = { update(s.copy(enabled = it)) }, enabled = enabled)
        }
        HorizontalDivider(color = Border)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Voz" to ConsoleSettings.narration(),
                "Música" to ConsoleSettings.music(),
                "Vintage" to ConsoleSettings.vintage()).forEach { (label, preset) ->
                OutlinedButton(onClick = { update(preset) }, enabled = enabled,
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Tune, null)
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "Ocultar ajuste fino" else "Abrir ajuste fino")
        }
        if (expanded) {
            ControlSlider("Saturação", s.drive, enabled) { update(s.copy(drive = it)) }
            ControlSlider("Calor / graves", s.warmth, enabled) { update(s.copy(warmth = it)) }
            ControlSlider("Presença", s.presence, enabled) { update(s.copy(presence = it)) }
            ControlSlider("Brilho", s.air, enabled) { update(s.copy(air = it)) }
            ControlSlider("Mistura", s.mix, enabled) { update(s.copy(mix = it)) }
            Text("Saída: ${String.format(Locale.US, "%+.1f dB", s.outputDb)}", color = Light)
            Slider(value = s.outputDb.coerceIn(-18f, 6f),
                onValueChange = { update(s.copy(outputDb = it)) },
                valueRange = -18f..6f, enabled = enabled)
        }
        Text("Efeito inspirado em consoles históricos; não é uma cópia calibrada da Neve 8028.",
            color = Soft, fontSize = 11.sp)
    }
}

@Composable
private fun ControlSlider(label: String, value: Float, enabled: Boolean, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Light, fontSize = 12.sp)
        Text("${(value * 100).toInt()}%", color = Brass, fontSize = 12.sp)
    }
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onValue, enabled = enabled)
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Brass, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp, modifier = modifier)
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Panel,
        border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content)
    }
}

@Composable
private fun Footnote() {
    Text("Áudio local e offline. O player usa arquivos indexados pelo Android. " +
        "O efeito não altera os arquivos originais.", color = Soft,
        fontSize = 10.sp, lineHeight = 15.sp)
}

private fun clock(milliseconds: Long): String {
    val seconds = max(0L, milliseconds) / 1000L
    return "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

private fun sizeInMB(file: File): String = "%.1f MB".format(Locale.US,
    file.length().toDouble() / (1024.0 * 1024.0))
