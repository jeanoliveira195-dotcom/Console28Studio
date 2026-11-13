package com.jean.console28.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Tracks indexed by Android's MediaStore (phone storage and removable media). */
data class MusicTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedSeconds: Long,
    val albumId: Long
)

class DeviceMusicLibrary(private val context: Context) {
    /** Call on Dispatchers.IO, after READ_MEDIA_AUDIO/READ_EXTERNAL_STORAGE permission. */
    fun scan(): List<MusicTrack> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.ALBUM_ID
        )
        val result = ArrayList<MusicTrack>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            val idAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idAt)
                val duration = cursor.getLong(durationAt)
                // Avoid ringtones/notifications; short music is accessible using file import.
                if (duration < 15_000L) continue
                val title = cursor.getString(titleAt)?.takeUnless { it.isBlank() }
                    ?: "Faixa sem título"
                val artist = cursor.getString(artistAt)?.takeUnless {
                    it.isBlank() || it.equals("<unknown>", ignoreCase = true)
                } ?: "Artista desconhecido"
                val album = cursor.getString(albumAt)?.takeUnless {
                    it.isBlank() || it.equals("<unknown>", ignoreCase = true)
                } ?: "Álbum desconhecido"
                result.add(MusicTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = title, artist = artist, album = album,
                    durationMs = duration, addedSeconds = cursor.getLong(addedAt),
                    albumId = cursor.getLong(albumIdAt)
                ))
            }
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
}
