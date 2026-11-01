package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Login Start must carry the profile UUID from Mojang, which is dashless hex. */
class ProtocolUuidTest {
    @Test
    fun parsesDashlessProfileUuid() {
        val uuid = Protocol.uuidFromString("069a79f444e94726a5befca90e38aaf5")
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", uuid?.toString())
    }

    @Test
    fun acceptsDashedUuidToo() {
        val uuid = Protocol.uuidFromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", uuid?.toString())
    }

    @Test
    fun rejectsMalformedValues() {
        assertNull(Protocol.uuidFromString("AFKBot"))
        assertNull(Protocol.uuidFromString(""))
        assertNull(Protocol.uuidFromString("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"))
    }
}
