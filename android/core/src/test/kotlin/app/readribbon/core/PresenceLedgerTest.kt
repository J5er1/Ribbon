package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

// Who is in a room, a connection at a time.
// A port of core/Tests/RibbonCoreTests/PresenceLedgerTests.swift, case for case.
class PresenceLedgerTest {
    private fun c(ref: String?, meta: String) = PresenceLedger.Connection(ref, meta)

    @Test
    fun testASecondPhoneLeavingKeepsThePerson() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "phone"))))
        room.apply(joins = mapOf("ana" to listOf(c("2", "tablet"))), leaves = emptyMap())
        room.apply(joins = emptyMap(), leaves = mapOf("ana" to listOf(c("2", "tablet"))))
        assertEquals("phone", room.latest("ana"))
    }

    @Test
    fun testAReconnectWhoseOldSocketLeavesLateKeepsThePerson() {
        // The new socket joins; the old one times out after it.
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "old socket"))))
        room.apply(joins = mapOf("ana" to listOf(c("2", "new socket"))), leaves = emptyMap())
        room.apply(joins = emptyMap(), leaves = mapOf("ana" to listOf(c("1", "old socket"))))
        assertEquals("new socket", room.latest("ana"))
        assertEquals(1, room.connections("ana").size)
    }

    @Test
    fun testATrackReplacesItsOwnConnection() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "verse 1"))))
        room.apply(
            joins = mapOf("ana" to listOf(c("2", "verse 2"))),
            leaves = mapOf("ana" to listOf(c("1", "verse 1"))),
        )
        assertEquals(listOf("verse 2"), room.connections("ana").map { it.meta })
    }

    @Test
    fun testTheLastConnectionLeavingTakesThePerson() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "phone")), "ben" to listOf(c("3", "phone"))))
        room.apply(joins = emptyMap(), leaves = mapOf("ana" to listOf(c("1", "phone"))))
        assertNull(room.latest("ana"))
        assertEquals(setOf("ben"), room.keys)
    }

    @Test
    fun testTheLatestIsWhicheverConnectionSpokeLast() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "phone, verse 1"), c("2", "tablet"))))
        room.apply(
            joins = mapOf("ana" to listOf(c("4", "phone, verse 2"))),
            leaves = mapOf("ana" to listOf(c("1", "phone, verse 1"))),
        )
        assertEquals("phone, verse 2", room.latest("ana"))
        assertEquals("tablet", room.latest("ana") { it == "tablet" })
    }

    @Test
    fun testALeaveNamingNoConnectionTakesThePerson() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "phone"), c("2", "tablet"))))
        room.apply(joins = emptyMap(), leaves = mapOf("ana" to listOf(c(null, "?"))))
        assertNull(room.latest("ana"))
    }

    @Test
    fun testALeaveForSomeoneNotHereIsNothing() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "phone"))))
        room.apply(joins = emptyMap(), leaves = mapOf("ben" to listOf(c("9", "phone"))))
        assertEquals(setOf("ana"), room.keys)
    }

    @Test
    fun testAStateReplacesTheWholeRoom() {
        val room = PresenceLedger<String>()
        room.reset(mapOf("ana" to listOf(c("1", "phone"))))
        room.reset(mapOf("ben" to listOf(c("3", "phone")), "cy" to emptyList()))
        assertEquals(setOf("ben"), room.keys)
        assertEquals(listOf("phone"), room.allMetas)
    }
}
