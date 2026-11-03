package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Text components switched from JSON strings to NBT in 1.20.3 (protocol 765).
 * Getting this boundary wrong makes system chat and kick reasons render blank
 * on 1.20.3/1.20.4 servers.
 */
class ProtocolTextTest {

    @Test
    fun textComponentsUseNbtFrom1_20_3() {
        assertFalse(Protocol.textComponentsAreNbt(764), "1.20.2 still uses JSON")
        assertTrue(Protocol.textComponentsAreNbt(765), "1.20.3 switched to NBT")
        assertTrue(Protocol.textComponentsAreNbt(766))
        assertTrue(Protocol.textComponentsAreNbt(775))
    }
}
