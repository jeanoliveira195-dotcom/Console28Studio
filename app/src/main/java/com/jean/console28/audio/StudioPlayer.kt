package com.jean.console28.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.jean.console28.library.MusicTrack
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class StudioPlayer(context: Context, private val settings: AtomicReference<ConsoleSettings>) {
    val player: ExoPlayer
    private var previewProcessed = false

    init {
        val sinkFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(ConsoleAudioProcessor(settings)))
                .setEnableFloatOutput(false)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
        }
        player = ExoPlayer.Builder(context, sinkFactory).build().also {
            it.repeatMode = Player.REPEAT_MODE_ALL
            it.setHandleAudioBecomingNoisy(true)
        }
    }

    fun playQueue(songs: List<MusicTrack>, startAt: Int) {
        if (songs.isEmpty()) return
        previewProcessed = false
        player.setMediaItems(songs.map { track ->
            MediaItem.Builder().setUri(track.uri).setMediaId("track:${track.id}").build()
        }, startAt.coerceIn(0, songs.lastIndex), 0L)
        player.prepare()
        player.play()
    }

    fun open(uri: Uri, alreadyProcessed: Boolean = false) {
        previewProcessed = alreadyProcessed
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
    }

    fun isProcessedPreview(): Boolean = previewProcessed
    fun release() = player.release()
}
