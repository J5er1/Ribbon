@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Auth0ServiceTest {

    @Test
    fun testComputeUuidV5MatchesRfc4122AndPostgres() {
        val sub = "auth0|64f2a1b2c3d4e5f6"
        val expected = Uuid.parse("33d67bad-a595-55a4-9b5d-c37b352f9006")
        val generated = Auth0Service.computeUuidV5(sub)
        assertEquals(expected, generated)
    }

    @Test
    fun testParseIdTokenWithExplicitUserUuid() {
        val userUuid = "4286068b-43ba-5380-bd10-dc774d43f5f3"
        val email = "reader@example.com"
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"auth0|12345","email":"$email","user_uuid":"$userUuid"}""".toByteArray()
        )
        val token = "$header.$payload.signature"

        val parsed = Auth0Service.parseIdToken(token)
        assertEquals(Uuid.parse(userUuid), parsed.userUuid)
        assertEquals(email, parsed.email)
    }

    @Test
    fun testParseIdTokenWithDeterministicFallback() {
        val sub = "auth0|64f2a1b2c3d4e5f6"
        val expectedUuid = Uuid.parse("33d67bad-a595-55a4-9b5d-c37b352f9006")
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"$sub"}""".toByteArray()
        )
        val token = "$header.$payload.signature"

        val parsed = Auth0Service.parseIdToken(token)
        assertEquals(expectedUuid, parsed.userUuid)
        assertNull(parsed.email)
    }
}
