@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.reading

import app.readribbon.services.PresentPerson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A follow keeps the screen on only while the person followed is here and
 * reading (A64): a follow that went dark every minute was following nobody,
 * and one left on for someone who has gone would only drain the battery.
 */
class FollowKeepsScreenOnTest {
    private val ruth = Uuid.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val ben = Uuid.parse("bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee")

    @Test
    fun followingSomeoneHereAndReadingKeepsItOn() {
        assertTrue(followKeepsScreenOn(ruth, listOf(PresentPerson(id = ruth, name = "Ruth"))))
    }

    @Test
    fun notFollowingLetsItSleep() {
        assertFalse(followKeepsScreenOn(null, listOf(PresentPerson(id = ruth, name = "Ruth"))))
    }

    @Test
    fun theirGoingIdleOrAwayLetsItSleep() {
        assertFalse(followKeepsScreenOn(ruth, listOf(PresentPerson(id = ruth, name = "Ruth", isIdle = true))))
        assertFalse(followKeepsScreenOn(ruth, listOf(PresentPerson(id = ben, name = "Ben"))))
        assertFalse(followKeepsScreenOn(ruth, emptyList()))
    }
}
