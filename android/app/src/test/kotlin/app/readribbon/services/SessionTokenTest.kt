package app.readribbon.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * A Realtime socket outlives the token it was opened with — Ribbon's stays up
 * for as long as the room is on screen — and the server refuses an expired
 * one outright rather than asking for a new one. So every (re)join checks the
 * token first, and this is the check.
 */
class SessionTokenTest {

    private fun token(expSecondsFromNow: Long): String {
        val exp = (System.currentTimeMillis() / 1000) + expSecondsFromNow
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"auth0|x","exp":$exp}""".toByteArray())
        return "header.$payload.signature"
    }

    @Test
    fun testATokenWithHoursLeftIsLeftAlone() {
        assertFalse(RemoteSync.expiresSoon(token(3600)))
    }

    @Test
    fun testATokenAboutToEndIsRefreshedFirst() {
        assertTrue(RemoteSync.expiresSoon(token(10)))
    }

    @Test
    fun testAnAlreadyExpiredTokenIsRefreshedFirst() {
        assertTrue(RemoteSync.expiresSoon(token(-5)))
    }

    /**
     * Unreadable means "let the server say so": the alternative is refreshing
     * on every single join because one claim could not be parsed.
     */
    @Test
    fun testAnUnreadableTokenIsTakenOnTrust() {
        assertFalse(RemoteSync.expiresSoon("not-a-jwt"))
        assertFalse(RemoteSync.expiresSoon("a.b.c"))
        assertFalse(RemoteSync.expiresSoon(""))
    }
}
