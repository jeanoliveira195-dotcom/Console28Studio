package com.jean.console28.library

import org.junit.Assert.*
import org.junit.Test

class AudioCatalogRulesTest {
    @Test fun faixa_curta_sem_metadados_pode_ser_incluida() {
        assertTrue(AudioCatalogRules.audioCandidate("intro.mp3", "audio/mpeg"))
        assertTrue(AudioCatalogRules.audioCandidate("som.WAV", null))
    }

    @Test fun aceita_formatos_variados_independentemente_do_mime() {
        for (name in listOf("a.flac","b.OPUS","c.m4a","d.ogg","e.aac")) {
            assertTrue(name, AudioCatalogRules.audioCandidate(name, null))
        }
    }

    @Test fun nao_confunde_imagens_e_documentos() {
        assertFalse(AudioCatalogRules.audioCandidate("cover.jpg", "image/jpeg"))
        assertFalse(AudioCatalogRules.audioCandidate("documento.pdf", "application/pdf"))
    }

    @Test fun corrige_metadados_ausentes() {
        assertEquals("uma musica", AudioCatalogRules.title(null, "uma musica.mp3"))
        assertEquals("Faixa com tag", AudioCatalogRules.title("Faixa com tag", "arquivo.wav"))
        assertEquals("Faixa sem título", AudioCatalogRules.title(null, null))
        assertEquals("Artista desconhecido",
            AudioCatalogRules.knownText("<unknown>", "Artista desconhecido"))
    }
}
