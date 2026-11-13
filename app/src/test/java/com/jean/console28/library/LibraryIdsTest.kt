package com.jean.console28.library

import org.junit.Assert.*
import org.junit.Test

class LibraryIdsTest {
    @Test fun ids_sao_estaveis() {
        assertEquals(LibraryIds.stableId("tree:documento1"), LibraryIds.stableId("tree:documento1"))
    }
    @Test fun arquivos_diferentes_tem_ids_diferentes() {
        assertNotEquals(LibraryIds.stableId("tree:a.mp3"), LibraryIds.stableId("tree:b.mp3"))
    }
    @Test fun id_e_positivo() {
        assertTrue(LibraryIds.stableId("volume:sd:123") > 0L)
    }
}
