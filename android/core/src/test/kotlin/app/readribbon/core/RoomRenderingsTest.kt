@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Test

// "In this room" (A62): versions grouped by what they say, yours first, and
// the words of every other rendering that are not in yours.
// A port of core/Tests/RibbonCoreTests/RoomRenderingsTests.swift, case for case.
class RoomRenderingsTest {
    // The room of eleven, on four versions. Public-domain wording only: the
    // King James and American Standard are stand-ins inside the fixture.
    val bsb = TranslationID.bsb
    val web = TranslationID.web
    val kjv = TranslationID(rawValue = "kjv")
    val asv = TranslationID(rawValue = "asv")
    val nkjv = TranslationID.nkjv
    val niv = TranslationID.niv

    val me = Uuid.parse("00000000-0000-0000-0000-000000000001")
    val ruth = Uuid.parse("00000000-0000-0000-0000-000000000002")
    val ann = Uuid.parse("00000000-0000-0000-0000-000000000003")
    val ben = Uuid.parse("00000000-0000-0000-0000-000000000004")
    val caleb = Uuid.parse("00000000-0000-0000-0000-000000000005")
    val dana = Uuid.parse("00000000-0000-0000-0000-000000000006")
    val eli = Uuid.parse("00000000-0000-0000-0000-000000000007")
    val faith = Uuid.parse("00000000-0000-0000-0000-000000000008")
    val gabe = Uuid.parse("00000000-0000-0000-0000-000000000009")
    val hope = Uuid.parse("00000000-0000-0000-0000-00000000000a")
    val isaac = Uuid.parse("00000000-0000-0000-0000-00000000000b")

    val bsbShort = "Through Him"
    val webShort = "through him"
    val kjvShort = "by him"
    val asvShort = "through him"

    val bsbWhole = "Through Him all things were made, and without Him nothing was made that has been made."
    val webWhole = "All things were made through him. Without him, nothing was made that has been made."
    val kjvWhole = "All things were made by him; and without him was not any thing made that was made."
    val asvWhole = "All things were made through him; and without him was not anything made that hath been made."

    fun eleven(bsbWords: String, webWords: String, kjvWords: String, asvWords: String): RoomRenderings =
        RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, bsbWords, listOf(me, caleb, faith, isaac)),
            others = listOf(
                RoomRenderings.Said(web, webWords, listOf(ruth, eli)),
                RoomRenderings.Said(kjv, kjvWords, listOf(ann, ben, gabe)),
                RoomRenderings.Said(asv, asvWords, listOf(dana, hope)),
            ),
        )

    fun r(start: Int, end: Int) = TextRange(start, end)

    // The room of eleven

    @Test
    fun testElevenShort() {
        val room = eleven(bsbShort, webShort, kjvShort, asvShort)
        assertEquals(
            listOf(
                RoomRenderings.Group(
                    versions = listOf(bsb, web, asv), phrase = "Through Him", differing = emptyList(),
                    readers = listOf(me, caleb, faith, isaac, ruth, eli, dana, hope), isYours = true,
                ),
                RoomRenderings.Group(
                    versions = listOf(kjv), phrase = "by him", differing = listOf(r(0, 2)),
                    readers = listOf(ann, ben, gabe), isYours = false,
                ),
            ),
            room.groups,
        )
        assertNull(room.notOnThisPhone)
        assertFalse(room.allAgree)
        assertFalse(room.isOneVersion)
        assertEquals(11, room.groups.flatMap { it.readers }.size)
    }

    @Test
    fun testElevenWholeVerse() {
        val room = eleven(bsbWhole, webWhole, kjvWhole, asvWhole)
        assertEquals(listOf(listOf(bsb), listOf(web), listOf(kjv), listOf(asv)), room.groups.map { it.versions })
        assertEquals(listOf(true, false, false, false), room.groups.map { it.isYours })
        assertEquals(listOf(bsbWhole, webWhole, kjvWhole, asvWhole), room.groups.map { it.phrase })
        assertEquals(
            listOf(listOf(me, caleb, faith, isaac), listOf(ruth, eli), listOf(ann, ben, gabe), listOf(dana, hope)),
            room.groups.map { it.readers },
        )
        assertEquals(emptyList(), room.groups[0].differing)
        // "through him", moved to after "All things were made".
        assertEquals(listOf(r(21, 28), r(29, 32)), room.groups[1].differing)
        // "by", "him", "not", "any", "thing", "was".
        assertEquals(
            listOf(r(21, 23), r(24, 27), r(49, 52), r(53, 56), r(57, 62), r(73, 76)),
            room.groups[2].differing,
        )
        // "through", "him", "not", "anything", "hath".
        assertEquals(listOf(r(21, 28), r(29, 32), r(54, 57), r(58, 66), r(77, 81)), room.groups[3].differing)
        val marked = room.groups[2].differing.map { kjvWhole.substring(it.start, it.end) }
        assertEquals(listOf("by", "him", "not", "any", "thing", "was"), marked)
        assertFalse(room.allAgree)
    }

    // What counts as the same words

    @Test
    fun testCapitalsAreIgnored() {
        val room = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, "Through Him", listOf(me)),
            others = listOf(RoomRenderings.Said(web, "through him", listOf(ruth))),
        )
        assertEquals(1, room.groups.size)
        assertEquals(listOf(bsb, web), room.groups[0].versions)
        assertEquals("Through Him", room.groups[0].phrase)
        assertTrue(room.allAgree)
        assertEquals(emptyList(), RoomRenderings.differing("THROUGH HIM", "Through Him"))
    }

    @Test
    fun testPunctuationIsIgnored() {
        val room = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, "Through Him, all things were made.", listOf(me)),
            others = listOf(
                RoomRenderings.Said(web, "through him; all things\nwere made", listOf(ruth)),
                RoomRenderings.Said(asv, "“Through him — all things were made!”", listOf(dana)),
            ),
        )
        assertEquals(1, room.groups.size)
        assertEquals(listOf(bsb, web, asv), room.groups[0].versions)
        assertTrue(room.allAgree)
        // An apostrophe is not a difference either: both say "lords".
        assertEquals(emptyList(), RoomRenderings.differing("the Lord’s", "the Lord's"))
    }

    @Test
    fun testReorderingDiffers() {
        // The same words as yours, in another order, are another rendering.
        val room = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, bsbWhole, listOf(me)),
            others = listOf(RoomRenderings.Said(web, webWhole, listOf(ruth))),
        )
        assertEquals(2, room.groups.size)
        assertEquals(listOf(r(21, 28), r(29, 32)), room.groups[1].differing)
        assertFalse(room.allAgree)
        // Of two words swapped, one is on the common subsequence; the other,
        // passed over first, is the difference.
        assertEquals(listOf(r(0, 3)), RoomRenderings.differing("him through", "Through Him"))
    }

    @Test
    fun testDifferingEdges() {
        assertEquals(emptyList(), RoomRenderings.differing("", "Through Him"))
        assertEquals(listOf(r(0, 2), r(3, 6)), RoomRenderings.differing("by him", ""))
        assertEquals(listOf(r(0, 2)), RoomRenderings.differing("by him", "Through Him"))
        assertEquals(emptyList(), RoomRenderings.differing("Through Him", "Through Him"))
    }

    // Who and in what order

    @Test
    fun testOtherVersionsThatAgreeShareABlock() {
        val room = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, bsbShort, listOf(me)),
            others = listOf(
                RoomRenderings.Said(kjv, "by him", listOf(ann)),
                RoomRenderings.Said(web, webShort, listOf(ruth)),
                RoomRenderings.Said(asv, "By Him.", listOf(dana)),
            ),
        )
        assertEquals(
            listOf(
                RoomRenderings.Group(
                    versions = listOf(bsb, web), phrase = bsbShort, differing = emptyList(),
                    readers = listOf(me, ruth), isYours = true,
                ),
                RoomRenderings.Group(
                    versions = listOf(kjv, asv), phrase = "by him", differing = listOf(r(0, 2)),
                    readers = listOf(ann, dana), isYours = false,
                ),
            ),
            room.groups,
        )
    }

    @Test
    fun testReadersYoursFirst() {
        // Yours leads even when the room lists another version first; in your
        // block your version's readers (you first) lead those that agree.
        val room = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, bsbShort, listOf(me, caleb)),
            others = listOf(
                RoomRenderings.Said(kjv, kjvShort, listOf(ann, ben, gabe)),
                RoomRenderings.Said(web, webShort, listOf(ruth, eli)),
                RoomRenderings.Said(bsb, bsbShort, listOf(faith)),
            ),
        )
        assertEquals(listOf(true, false), room.groups.map { it.isYours })
        assertEquals(listOf(bsb, web), room.groups[0].versions)
        // A version given twice keeps its readers together.
        assertEquals(listOf(me, caleb, faith, ruth, eli), room.groups[0].readers)
        assertEquals(me, room.groups[0].readers.first())
        assertEquals(listOf(ann, ben, gabe), room.groups[1].readers)
    }

    // Versions not on this phone

    @Test
    fun testNotOnThisPhoneIsKeptApart() {
        val room = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, bsbShort, listOf(me)),
            others = listOf(
                RoomRenderings.Said(web, webShort, listOf(ruth)),
                RoomRenderings.Said(nkjv, null, listOf(ann)),
                RoomRenderings.Said(niv, null, listOf(ben)),
                RoomRenderings.Said(nkjv, null, listOf(gabe)),
            ),
        )
        assertEquals(1, room.groups.size)
        assertEquals(listOf(bsb, web), room.groups[0].versions)
        assertEquals(
            RoomRenderings.Unavailable(versions = listOf(nkjv, niv), readers = listOf(ann, gabe, ben)),
            room.notOnThisPhone,
        )
        // What could not be compared cannot be said to agree.
        assertFalse(room.allAgree)
        assertFalse(room.isOneVersion)
    }

    // Agreement

    @Test
    fun testRoomOfOneVersion() {
        val alone = RoomRenderings.of(yours = RoomRenderings.Said(bsb, bsbShort, listOf(me)), others = emptyList())
        assertEquals(
            listOf(
                RoomRenderings.Group(
                    versions = listOf(bsb), phrase = bsbShort, differing = emptyList(),
                    readers = listOf(me), isYours = true,
                ),
            ),
            alone.groups,
        )
        assertTrue(alone.allAgree)
        assertTrue(alone.isOneVersion)

        val together = RoomRenderings.of(
            yours = RoomRenderings.Said(bsb, bsbWhole, listOf(me)),
            others = listOf(RoomRenderings.Said(bsb, bsbWhole, listOf(ruth, ann))),
        )
        assertEquals(1, together.groups.size)
        assertEquals(listOf(bsb), together.groups[0].versions)
        assertEquals(listOf(me, ruth, ann), together.groups[0].readers)
        assertTrue(together.isOneVersion)
    }

    @Test
    fun testAllAgreeAcrossVersions() {
        val room = eleven(bsbShort, webShort, "Through him", asvShort)
        assertEquals(1, room.groups.size)
        assertEquals(listOf(bsb, web, kjv, asv), room.groups[0].versions)
        assertEquals(11, room.groups[0].readers.size)
        assertTrue(room.allAgree)
        assertFalse(room.isOneVersion)
    }
}
