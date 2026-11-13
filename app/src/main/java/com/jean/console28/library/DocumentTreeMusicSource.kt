package com.jean.console28.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/** Importa automaticamente os arquivos de uma pasta e todas as suas subpastas
 * após uma única autorização explícita do usuário (SAF). */
class DocumentTreeMusicSource(private val context: Context) {
    fun scan(tree: Uri): List<MusicTrack> {
        val tracks = ArrayList<MusicTrack>()
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(tree))
        val seen = HashSet<String>()
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        while (pending.isNotEmpty() && seen.size < 100_000) {
            val parent = pending.removeFirst()
            if (!seen.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                val idAt = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameAt = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeAt = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeAt = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val timeAt = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val docId = c.getString(idAt) ?: continue
                    val mime = if (mimeAt >= 0 && !c.isNull(mimeAt)) c.getString(mimeAt) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (seen.size + pending.size < 100_000) pending.addLast(docId)
                        continue
                    }
                    val filename = if (nameAt >= 0 && !c.isNull(nameAt)) c.getString(nameAt) else null
                    if (!AudioCatalogRules.audioCandidate(filename, mime)) continue
                    if (sizeAt >= 0 && !c.isNull(sizeAt) && c.getLong(sizeAt) == 0L) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    val date = if (timeAt >= 0 && !c.isNull(timeAt)) c.getLong(timeAt)/1000L else 0L
                    tracks.add(MusicTrack(
                        id = LibraryIds.stableId("tree:${uri}"),
                        uri = uri,
                        title = AudioCatalogRules.title(null, filename),
                        artist = "Artista desconhecido",
                        album = "Pasta autorizada",
                        durationMs = 0L,
                        addedSeconds = date,
                        albumId = 0L
                    ))
                }
            }
        }
        return tracks
    }
}

/** Hash estável usado apenas para itens SAF e IDs de unidades adicionais. */
internal object LibraryIds {
    fun stableId(value: String): Long {
        var hash = 0xcbf29ce484222325UL
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
        }
        return (hash.toLong() and ((1L shl 61)-1)) or (1L shl 61)
    }
}
