package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A failed refresh must only send the user through device-code sign-in when the
 * refresh token is actually dead. Treating a 5xx as permanent silently logs the
 * account out every time Microsoft has a hiccup.
 */
class AuthRefreshTest {

    @Test
    fun revokedOrInvalidGrantNeedsANewSignIn() {
        assertTrue(MicrosoftAuth.refreshFailureIsPermanent(400, "invalid_grant"))
        assertTrue(MicrosoftAuth.refreshFailureIsPermanent(401, null))
        assertTrue(MicrosoftAuth.refreshFailureIsPermanent(400, "invalid_client"))
        assertTrue(MicrosoftAuth.refreshFailureIsPermanent(400, "authorization_declined"))
    }

    @Test
    fun serverAndRateLimitErrorsAreTransient() {
        assertFalse(MicrosoftAuth.refreshFailureIsPermanent(500, "server_error"))
        assertFalse(MicrosoftAuth.refreshFailureIsPermanent(503, null))
        assertFalse(MicrosoftAuth.refreshFailureIsPermanent(429, "temporarily_unavailable"))
    }
}
