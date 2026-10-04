package app.readribbon.reading

import app.readribbon.app.Copy
import app.readribbon.core.Person
import app.readribbon.core.TranslationID
import app.readribbon.core.displayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "In this room" (A62, §13.3), worked out without a screen: who is in which
 * block, in what order, what the names beside the faces say, and what
 * TalkBack reads. The grouping itself is the core's (RoomRenderingsTest).
 *
 * The King James and American Standard here are stand-ins with their own
 * public-domain wording; neither is a version the app carries.
 */
class RoomSectionTest {

    private val kjv = TranslationID("kjv")
    private val asv = TranslationID("asv")

    private fun name(version: TranslationID) = when (version) {
        kjv -> "King James"
        asv -> "American Standard"
        else -> version.displayName
    }

    private val short = mapOf(
        TranslationID.bsb to "Through Him",
        TranslationID.web to "through him",
        kjv to "by him",
        asv to "through him",
    )

    private val me = RoomPerson(Person(name = "Jonathan"), TranslationID.bsb)
    private fun person(name: String, version: TranslationID) = RoomPerson(Person(name = name), version)

    private val ruth = person("Ruth Alderman", TranslationID.web)

    /** Eleven people on four versions, given in no particular order. */
    private val eleven = listOf(
        ruth,
        person("Isaac", TranslationID.bsb),
        person("Ann Brooke", kjv),
        person("Hope", asv),
        person("Ben", kjv),
        person("Caleb", TranslationID.bsb),
        person("Dana", asv),
        person("Eli", TranslationID.web),
        person("Faith", TranslationID.bsb),
        person("Gabe", kjv),
    )

    private fun section(others: List<RoomPerson>, said: Map<TranslationID, String?> = short) =
        roomSection(
            yours = TranslationID.bsb,
            me = me,
            others = others,
            saying = { version -> said[version]?.let { RoomSaid(it) } },
            versionName = ::name,
        )

    @Test fun aRoomOnYourVersionSaysNothing() {
        val others = listOf(person("Caleb", TranslationID.bsb), person("Faith", TranslationID.bsb))
        assertEquals(RoomSection.Omitted, section(others))
        assertEquals(RoomSection.Omitted, section(emptyList()))
    }

    @Test fun oneOtherWhoAgreesIsNamed() {
        val agrees = section(listOf(ruth)) as RoomSection.Agrees
        assertEquals("Ruth reads these words as you do.", agrees.agreement.line)
        assertEquals(listOf("Ruth"), agrees.agreement.faces.map { it.name })
        assertEquals(agrees.agreement.line, agrees.agreement.spoken)
    }

    @Test fun everyoneAgreesInOneLineWithThreeFaces() {
        val others = listOf(ruth, person("Dana", asv), person("Eli", TranslationID.web), person("Caleb", TranslationID.bsb))
        val agrees = section(others, short - kjv) as RoomSection.Agrees
        assertEquals(Copy.ROOM_ALL_AGREE, agrees.agreement.line)
        // By name, and never you: the line is about the others.
        assertEquals(listOf("Caleb", "Dana", "Eli"), agrees.agreement.faces.map { it.name })
        assertEquals(
            "Everyone here reads these words as you do. Caleb, Dana, Eli and Ruth.",
            agrees.agreement.spoken,
        )
    }

    @Test fun elevenOnFourVersionsIsTwoBlocks() {
        val blocks = (section(eleven) as RoomSection.Blocks).blocks
        assertEquals(2, blocks.size)

        val yours = blocks[0]
        assertEquals(RoomBlock.Kind.Yours, yours.kind)
        // Your version, then the others that say the same, in the order of
        // the first person by name who reads each.
        assertEquals("Yours · Berean Standard, American Standard and World English", yours.label)
        assertEquals("Through Him", yours.words)
        assertTrue(yours.differing.isEmpty())
        assertEquals(
            listOf("you", "Caleb", "Faith", "Isaac", "Dana", "Hope", "Eli", "Ruth"),
            yours.readers.map { it.name },
        )
        assertEquals(me.person.id, yours.faces.first().id)
        assertEquals(3, yours.faces.size)
        assertEquals("you, Caleb, Faith and others", yours.names)
        assertFalse("never a numeral", yours.names.any(Char::isDigit))
        assertEquals(
            "Through Him. Yours · Berean Standard, American Standard and World English. " +
                "you, Caleb, Faith, Isaac, Dana, Hope, Eli and Ruth.",
            yours.spoken,
        )

        val kingJames = blocks[1]
        assertEquals(RoomBlock.Kind.Other, kingJames.kind)
        assertEquals("King James", kingJames.label)
        assertEquals("by him", kingJames.words)
        assertEquals(listOf("by" to true, " him" to false), kingJames.runs)
        assertEquals("Ann, Ben and Gabe", kingJames.names)
        assertEquals("by him. King James. Ann, Ben and Gabe.", kingJames.spoken)
    }

    @Test fun aVersionNotOnThisPhoneIsItsOwnBlockAndKeepsTheRoomFromAgreeing() {
        val others = listOf(ruth, person("Ann Brooke", TranslationID.niv))
        val section = section(others, short + (TranslationID.niv to null))
        assertTrue(section.waitsForAVersion)
        val blocks = (section as RoomSection.Blocks).blocks
        assertEquals(listOf(RoomBlock.Kind.Yours, RoomBlock.Kind.NotOnThisPhone), blocks.map { it.kind })
        assertEquals("Yours · Berean Standard and World English", blocks[0].label)
        assertEquals(TranslationID.niv.displayName, blocks[1].label)
        assertEquals(Copy.ORIGINAL_NOT_ON_THIS_PHONE, blocks[1].words)
        assertEquals(listOf("Ann"), blocks[1].readers.map { it.name })
    }

    @Test fun aWholeVerseStandingInIsNotMarkedWordByWord() {
        val section = roomSection(
            yours = TranslationID.bsb,
            me = me,
            others = listOf(person("Ann", TranslationID.nkjv)),
            saying = { version ->
                if (version == TranslationID.bsb) RoomSaid("Through Him") else RoomSaid("All things were made through Him", wholeVerse = true)
            },
        )
        val other = (section as RoomSection.Blocks).blocks[1]
        assertTrue(other.isWholeVerse)
        assertTrue(other.differing.isEmpty())
        assertFalse(section.waitsForAVersion)
    }

    @Test fun aReaderWithNoNameIsSomeone() {
        val blocks = (section(listOf(person("  ", kjv))) as RoomSection.Blocks).blocks
        assertEquals(listOf(Copy.SOMEONE), blocks[1].readers.map { it.name })
    }

    @Test fun theSameRoomReadsTheSameWayInAnyOrder() {
        assertEquals(section(eleven), section(eleven.reversed()))
    }

    @Test fun eachVersionIsAskedOnce() {
        val asked = mutableListOf<TranslationID>()
        roomSection(TranslationID.bsb, me, eleven, saying = { asked += it; short[it]?.let(::RoomSaid) }, versionName = ::name)
        assertEquals(asked.distinct(), asked)
    }

    @Test fun namesStopAtThree() {
        assertEquals("you", namesBeside(listOf("you")))
        assertEquals("you, Ann and Ben", namesBeside(listOf("you", "Ann", "Ben")))
        assertEquals("you, Ann, Ben and others", namesBeside(listOf("you", "Ann", "Ben", "Caleb")))
    }

    @Test fun aSentenceIsClosedOnce() {
        assertEquals("made that was made.” King James.", sentences(listOf("made that was made.”", "King James")))
        assertEquals("Who? you.", sentences(listOf("Who?", " ", "you")))
    }
}
