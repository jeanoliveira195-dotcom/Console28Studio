package com.jean.console28.library

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

/** Item indexado no armazenamento compartilhado do Android. IDs MediaStore
 * são mantidos para preservar compatibilidade com as playlists existentes. */
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

data class LibraryScanResult(
    val tracks: List<MusicTrack>,
    val mediaStoreCount: Int,
    val folderCount: Int,
    val diagnostics: String
)

class DeviceMusicLibrary(private val context: Context) {

    /** Complementa a consulta atual com outras unidades e pastas SAF autorizadas. */
    fun scanDetailed(folderUris: List<Uri>): LibraryScanResult {
        val base = scan()
        val tracks = LinkedHashMap<Long, MusicTrack>()
        base.forEach { tracks[it.id] = it }
        val report = arrayListOf("MediaStore: ${base.size}")
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val volumes = try {
                MediaStore.getExternalVolumeNames(context).sorted()
            } catch (_: Exception) { emptyList() }
            for (volume in volumes) {
                try {
                    val collection = MediaStore.Audio.Media.getContentUri(volume)
                    val before = tracks.size
                    val cols = arrayOf(
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.ALBUM_ID
                    )
                    context.contentResolver.query(collection, cols, null, null, null)?.use { c ->
                        val idAt = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val nameAt = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                        val titleAt = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                        val artistAt = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                        val albumAt = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                        val durationAt = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                        val dateAt = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                        val albumIdAt = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                        while (c.moveToNext()) {
                            val nativeId = c.getLong(idAt)
                            if (nativeId < 0) continue
                            val isPrimary = volume == MediaStore.VOLUME_EXTERNAL_PRIMARY
                            val id = if (isPrimary) nativeId
                                else LibraryIds.stableId("volume:${volume}:${nativeId}")
                            if (tracks.containsKey(id)) continue
                            tracks[id] = MusicTrack(
                                id = id,
                                uri = ContentUris.withAppendedId(collection, nativeId),
                                title = AudioCatalogRules.title(
                                    c.safeString(titleAt), c.safeString(nameAt)),
                                artist = AudioCatalogRules.knownText(
                                    c.safeString(artistAt), "Artista desconhecido"),
                                album = AudioCatalogRules.knownText(
                                    c.safeString(albumAt), "Álbum desconhecido"),
                                durationMs = c.safeLong(durationAt),
                                addedSeconds = c.safeLong(dateAt),
                                albumId = c.safeLong(albumIdAt)
                            )
                        }
                    }
                    report.add("${volume}: +${tracks.size-before}")
                } catch (_: SecurityException) {
                    report.add("${volume}: acesso restrito")
                } catch (_: IllegalArgumentException) {
                    report.add("${volume}: indisponível")
                }
            }
        }
        val mediaTotal = tracks.size
        var folderTotal = 0
        for (tree in folderUris) {
            try {
                for (track in DocumentTreeMusicSource(context).scan(tree)) {
                    if (tracks.putIfAbsent(track.id, track) == null) folderTotal++
                }
            } catch (_: Exception) {
                report.add("Pasta autorizada: acesso indisponível")
            }
        }
        report.add("Pastas: +${folderTotal}")
        val sorted = tracks.values.sortedWith(compareBy<MusicTrack> {
            it.title.lowercase(java.util.Locale.ROOT)
        }.thenBy { it.artist.lowercase(java.util.Locale.ROOT) })
        return LibraryScanResult(sorted, mediaTotal, folderTotal, report.joinToString(" · "))
    }

    /** Lista todo áudio acessível e indexado no armazenamento compartilhado.
     * Não filtra por duração, IS_MUSIC, artista, álbum ou diretório.
     * Arquivos privados de outros apps, protegidos ou sem indexação pelo Android
     * não podem ser acessados automaticamente com READ_MEDIA_AUDIO. */
    fun scan(): List<MusicTrack> {
        val items = LinkedHashMap<Long, MusicTrack>()
        scanAudioTable(items)
        try {
            scanFilesFallback(items)
        } catch (_: SecurityException) {
            // Alguns dispositivos restringem a tabela Files; Audio.Media continua válida.
        } catch (_: IllegalArgumentException) {
            // Provedores diferentes podem rejeitar seleção ou colunas do fallback.
        } catch (_: UnsupportedOperationException) {
        }
        return items.values.sortedWith(
            compareBy<MusicTrack> { it.title.lowercase(java.util.Locale.ROOT) }
                .thenBy { it.artist.lowercase(java.util.Locale.ROOT) }
        )
    }

    private fun scanAudioTable(items: MutableMap<Long, MusicTrack>) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.ALBUM_ID
        )
        context.contentResolver.query(collection, columns, null, null, null)?.use { cursor ->
            val idAt = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleAt = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val filenameAt = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistAt = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumAt = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationAt = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val addedAt = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdAt = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idAt)
                if (id < 0) continue
                // Sem limite mínimo: faixas curtas e arquivos sem metadados devem aparecer.
                items[id] = MusicTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = AudioCatalogRules.title(
                        cursor.safeString(titleAt), cursor.safeString(filenameAt)
                    ),
                    artist = AudioCatalogRules.knownText(
                        cursor.safeString(artistAt), "Artista desconhecido"
                    ),
                    album = AudioCatalogRules.knownText(
                        cursor.safeString(albumAt), "Álbum desconhecido"
                    ),
                    durationMs = cursor.safeLong(durationAt),
                    addedSeconds = cursor.safeLong(addedAt),
                    albumId = cursor.safeLong(albumIdAt)
                )
            }
        }
    }

    private fun scanFilesFallback(items: MutableMap<Long, MusicTrack>) {
        // Fallback para áudios visíveis em Files mas mal classificados.
        // Não contorna o scoped storage e nem acessa downloads privados.
        val collection = MediaStore.Files.getContentUri("external")
        val name = MediaStore.MediaColumns.DISPLAY_NAME
        val mime = MediaStore.MediaColumns.MIME_TYPE
        val columns = arrayOf(
            MediaStore.Files.FileColumns._ID, name, mime,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE
        )
        val expressions = listOf("$mime LIKE ?") +
            AudioCatalogRules.extensions.map { "$name LIKE ?" }
        val where = expressions.joinToString(" OR ", "(", ")")
        val args = arrayOf("audio/%") + AudioCatalogRules.extensions.map { "%$it" }
        context.contentResolver.query(collection, columns, where, args, null)?.use { cursor ->
            val idAt = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val filenameAt = cursor.getColumnIndex(name)
            val mimeAt = cursor.getColumnIndex(mime)
            val addedAt = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeAt = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idAt)
                if (id < 0 || items.containsKey(id)) continue
                val filename = cursor.safeString(filenameAt)
                if (!AudioCatalogRules.audioCandidate(filename, cursor.safeString(mimeAt))) continue
                if (sizeAt >= 0 && cursor.safeLong(sizeAt) == 0L) continue
                items[id] = MusicTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = AudioCatalogRules.title(null, filename),
                    artist = "Artista desconhecido",
                    album = "Arquivos locais",
                    durationMs = 0L,
                    addedSeconds = cursor.safeLong(addedAt),
                    albumId = 0L
                )
            }
        }
    }

    private fun Cursor.safeString(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun Cursor.safeLong(index: Int): Long =
        if (index < 0 || isNull(index)) 0L else getLong(index)
}
