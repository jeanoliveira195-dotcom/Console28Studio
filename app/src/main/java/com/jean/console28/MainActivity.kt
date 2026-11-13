package com.jean.console28

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jean.console28.audio.ConsoleSettings
import com.jean.console28.audio.MicrophoneRecorder
import com.jean.console28.audio.RecordingInfo
import com.jean.console28.audio.StudioPlayer
import com.jean.console28.library.DeviceMusicLibrary
import com.jean.console28.library.MusicTrack
import com.jean.console28.library.PlaylistStore
import com.jean.console28.ui.StudioScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private var settings by mutableStateOf(ConsoleSettings.music())
    private val settingsRef = AtomicReference(settings)
    private lateinit var audio: StudioPlayer
    private lateinit var recorder: MicrophoneRecorder
    private lateinit var playlistStore: PlaylistStore

    private var tab by mutableStateOf(0)
    private var recording by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var title by mutableStateOf("Sua coleção de músicas")
    private var artist by mutableStateOf("Console 28 Studio")
    private var currentTrackId by mutableStateOf<Long?>(null)
    private var message by mutableStateOf("Sua biblioteca permanece somente neste aparelho.")
    private var recordings by mutableStateOf<List<RecordingInfo>>(emptyList())
    private var currentRecording by mutableStateOf<RecordingInfo?>(null)
    private var tracks by mutableStateOf<List<MusicTrack>>(emptyList())
    private var libraryDiagnostics by mutableStateOf("Nenhuma leitura executada")
    private var authorizedFolders by mutableStateOf(0)
    private var favorites by mutableStateOf<Set<Long>>(emptySet())
    private var playlists by mutableStateOf<Map<String, List<Long>>>(emptyMap())
    private var hasMusicPermission by mutableStateOf(false)
    private var libraryLoading by mutableStateOf(false)
    private var libraryError by mutableStateOf<String?>(null)
    private var exportFile: File? = null

    private val folderPrefs by lazy {
        getSharedPreferences("console28_music_folders", MODE_PRIVATE)
    }
    private fun musicFolders(): List<Uri> =
        folderPrefs.getStringSet("roots", emptySet()).orEmpty().map(Uri::parse)

    private val chooseFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val updated = musicFolders().map(Uri::toString).toMutableSet()
                updated.add(tree.toString())
                folderPrefs.edit().putStringSet("roots", updated).apply()
                authorizedFolders = updated.size
                message = "Pasta autorizada. Procurando músicas e subpastas..."
                refreshMusic()
            } catch (e: SecurityException) {
                libraryError = "Não foi possível acessar a pasta autorizada."
            }
        }
    }

    private val mediaHandler = Handler(Looper.getMainLooper())
    private var pendingRefresh = false
    private var observingAudio = false
    private val delayedRefresh = Runnable { if (hasMusicPermission) refreshMusic() }
    private val mediaObserver = object : ContentObserver(mediaHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            mediaHandler.removeCallbacks(delayedRefresh)
            mediaHandler.postDelayed(delayedRefresh, 1500L)
        }
    }


    private val chooseAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* SAF still grants access for this session. */ }
        audio.open(uri)
        title = uri.lastPathSegment?.substringAfterLast('/') ?: "Arquivo importado"
        artist = "Arquivo local"
        currentTrackId = null
        message = "Arquivo aberto. Você pode comparar o efeito com o original."
        updateDsp()
        tab = 0
    }

    private val musicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        hasMusicPermission = allowed
        if (allowed) { observeMusicChanges(); refreshMusic() } else {
            libraryError = "Permita acesso aos arquivos de áudio para mostrar as músicas do celular."
        }
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) beginRecording() else message = "A gravação exige acesso ao microfone."
    }

    private val saveWav = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val source = exportFile
        exportFile = null
        if (uri == null || source == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(source.exists()) { "Arquivo de origem não encontrado" }
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                    } ?: error("Não foi possível criar o arquivo de destino")
                }
                message = "WAV exportado. Importe-o no editor de vídeo."
            } catch (e: Exception) {
                message = "Falha ao exportar: ${e.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorder = MicrophoneRecorder(applicationContext)
        playlistStore = PlaylistStore(applicationContext)
        audio = StudioPlayer(applicationContext, settingsRef)
        favorites = playlistStore.favorites()
        playlists = playlistStore.playlists()
        recordings = recorder.recordings()
        authorizedFolders = musicFolders().size
        audio.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.removePrefix("track:")?.toLongOrNull()
                if (id != null) {
                    tracks.firstOrNull { it.id == id }?.let {
                        currentTrackId = it.id
                        title = it.title
                        artist = it.artist
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                message = "Não foi possível reproduzir: ${error.message ?: "formato não suportado"}"
            }
        })
        hasMusicPermission = isMusicAllowed()
        setContent {
            StudioScreen(
                tab = tab,
                onTab = { if (!recording) tab = it },
                settings = settings,
                onSettings = { s -> settings = s; updateDsp() },
                spectrum = { audio.spectrum.snapshot() },
                tracks = tracks,
                libraryDiagnostics = libraryDiagnostics,
                authorizedFolders = authorizedFolders,
                onAuthorizeFolder = { chooseFolder.launch(null) },
                favorites = favorites,
                playlists = playlists,
                hasMusicPermission = hasMusicPermission,
                libraryLoading = libraryLoading,
                libraryError = libraryError,
                onRequestMusicPermission = ::requestMusicPermission,
                onRefreshMusic = ::refreshMusic,
                onPlayTrack = ::playTrack,
                onFavorite = { id -> favorites = playlistStore.toggleFavorite(id) },
                onCreatePlaylist = { name ->
                    val created = playlistStore.create(name)
                    if (created) playlists = playlistStore.playlists()
                    created
                },
                onDeletePlaylist = { name -> playlistStore.delete(name); playlists = playlistStore.playlists() },
                onTogglePlaylistTrack = { name, id ->
                    playlistStore.toggleTrack(name, id)
                    playlists = playlistStore.playlists()
                },
                recording = recording,
                recordings = recordings,
                currentRecording = currentRecording,
                onChooseRecording = { currentRecording = it },
                onStartRecording = ::askToRecord,
                onStopRecording = { recorder.stop(); message = "Finalizando os arquivos WAV..." },
                onPlayRecording = ::playRecording,
                onExportProcessed = { exportWav(it.processed) },
                onExportOriginal = { it.original?.let(::exportWav) },
                onPickMusic = { chooseAudio.launch(arrayOf("audio/*")) },
                onTogglePlay = {
                    if (audio.player.isPlaying) audio.player.pause()
                    else if (audio.player.mediaItemCount > 0) audio.player.play()
                },
                onNext = { if (audio.player.hasNextMediaItem()) audio.player.seekToNextMediaItem() },
                onPrevious = {
                    if (audio.player.currentPosition > 3000L) audio.player.seekTo(0)
                    else if (audio.player.hasPreviousMediaItem()) audio.player.seekToPreviousMediaItem()
                    else audio.player.seekTo(0)
                },
                isPlaying = isPlaying,
                isProcessedPreview = audio.isProcessedPreview(),
                title = title,
                artist = artist,
                currentTrackId = currentTrackId,
                message = message,
                positionMs = { audio.player.currentPosition.coerceAtLeast(0L) },
                durationMs = { audio.player.duration.coerceAtLeast(0L) },
                onSeek = { ms -> audio.player.seekTo(ms) }
            )
        }
        if (hasMusicPermission) { observeMusicChanges(); refreshMusic() }
        else requestMusicPermission()
    }

    private fun isMusicAllowed(): Boolean = ContextCompat.checkSelfPermission(
        this,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestMusicPermission() {
        if (isMusicAllowed()) {
            hasMusicPermission = true
            observeMusicChanges()
            refreshMusic()
        } else {
            musicPermission.launch(if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun observeMusicChanges() {
        if (observingAudio || !isMusicAllowed()) return
        try {
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
            )
            contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"), true, mediaObserver
            )
            observingAudio = true
        } catch (_: SecurityException) {
            // Retomar o app também atualiza a lista, sem observador.
        }
    }

    override fun onResume() {
        super.onResume()
        hasMusicPermission = isMusicAllowed()
        if (hasMusicPermission && ::audio.isInitialized) {
            observeMusicChanges()
            refreshMusic()
        }
    }

    private fun refreshMusic() {
        if (!isMusicAllowed()) {
            hasMusicPermission = false
            libraryError = "Permissão de músicas necessária para indexar a biblioteca."
            return
        }
        if (libraryLoading) { pendingRefresh = true; return }
        libraryLoading = true
        libraryError = null
        lifecycleScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    DeviceMusicLibrary(applicationContext).scanDetailed(musicFolders())
                }
                tracks = found.tracks
                libraryDiagnostics = found.diagnostics
                authorizedFolders = musicFolders().size
                message = tracks.size.toString() + " arquivos encontrados no dispositivo."
            } catch (e: Exception) {
                libraryError = "Não foi possível ler a biblioteca: ${e.message ?: "erro desconhecido"}"
            } finally {
                libraryLoading = false
                if (pendingRefresh) { pendingRefresh = false; refreshMusic() }
            }
        }
    }

    private fun playTrack(track: MusicTrack, queue: List<MusicTrack>) {
        if (recording) return
        val position = queue.indexOfFirst { it.id == track.id }
        if (position < 0) return
        audio.playQueue(queue, position)
        currentTrackId = track.id
        title = track.title
        artist = track.artist
        updateDsp()
    }

    private fun updateDsp() {
        settingsRef.set(if (audio.isProcessedPreview()) settings.copy(enabled = false) else settings)
    }

    private fun askToRecord() {
        if (recording) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginRecording() {
        if (recording) return
        audio.player.pause()
        val started = recorder.start(settings) { result, error ->
            runOnUiThread {
                recording = false
                recordings = recorder.recordings()
                currentRecording = result ?: recordings.firstOrNull()
                message = if (error != null) "Falha ao gravar: $error"
                    else "Gravação concluída. WAV original e tratado prontos."
            }
        }
        if (started) {
            recording = true
            tab = 2
            message = "Gravando narração; ajustes congelados até salvar."
        }
    }

    private fun playRecording(r: RecordingInfo) {
        audio.open(Uri.fromFile(r.processed), alreadyProcessed = true)
        updateDsp()
        title = r.processed.name
        artist = "Narração tratada"
        currentTrackId = null
        currentRecording = r
        message = "Prévia tratada sem aplicar o filtro duas vezes."
        audio.player.play()
    }

    private fun exportWav(source: File) {
        exportFile = source
        saveWav.launch(source.name)
    }

    override fun onStop() {
        super.onStop()
        if (recorder.isRecording) recorder.stop() // Recording is foreground-only in v0.3.
    }

    override fun onDestroy() {
        mediaHandler.removeCallbacks(delayedRefresh)
        if (observingAudio) contentResolver.unregisterContentObserver(mediaObserver)
        recorder.stop()
        audio.release()
        super.onDestroy()
    }
}
