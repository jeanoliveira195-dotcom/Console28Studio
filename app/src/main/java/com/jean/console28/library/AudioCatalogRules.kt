package com.jean.console28.library

/** Regras puras para identificação e exibição de áudio. */
internal object AudioCatalogRules {
    val extensions = listOf(
        ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".opus",
        ".wma", ".aiff", ".aif", ".amr", ".3gp", ".ape", ".mka"
    )

    fun audioCandidate(filename: String?, mimeType: String?): Boolean =
        mimeType?.startsWith("audio/", ignoreCase = true) == true ||
            (filename?.let { name -> extensions.any { name.endsWith(it, ignoreCase = true) } } == true)

    fun knownText(value: String?, fallback: String): String =
        value?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true)
                && !it.equals("unknown", ignoreCase = true)
                && it != "-"
        } ?: fallback

    fun title(metadataTitle: String?, filename: String?): String {
        val fromTag = knownText(metadataTitle, "")
        if (fromTag.isNotEmpty()) return fromTag
        val fromFile = filename?.substringAfterLast('/')?.trim()
            ?.substringBeforeLast('.', "")?.takeIf { it.isNotBlank() }
        return fromFile ?: filename?.takeIf { it.isNotBlank() } ?: "Faixa sem título"
    }
}
