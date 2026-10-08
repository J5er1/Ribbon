package app.readribbon.design

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scripture's style takes a weight now (A68), and at Book it is the style it
 * always was.
 *
 * `RibbonType.scripture` is not only the page's: the theme's body text, the
 * shelf, the chooser, the tour, the reflection card and the original panel
 * all set Scripture with it, and none of them asks for a weight. Each of them
 * has to come out exactly as before — field for field, the same face on the
 * same axis settings — and the original panel most of all, where a word that
 * differs between versions is set in Medium (A62) and would lose that if
 * Scripture around it grew heavier. And Bold Text, which the page and the
 * Text screen's preview fold into the weight themselves, is read as the
 * platform reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScriptureAtBookIsTodaysTest {

    @get:Rule val compose = createComposeRule()

    @Test fun atBookTheStyleIsTodaysFieldForField() {
        lateinit var unasked: TextStyle
        lateinit var book: TextStyle
        compose.setContent {
            unasked = RibbonType.scripture(19f)
            book = RibbonType.scripture(19f, weight = 400)
        }
        compose.waitForIdle()
        // As `scripture(size)` was written before it took a weight.
        val today = TextStyle(
            fontFamily = RibbonFonts.literata(FontWeight.Normal, 19f),
            fontSize = 19.sp,
            lineHeight = (19f * 1.62f).sp,
        )
        assertEquals(today, unasked)
        assertEquals(today, book)
        assertNull("no weight named on the style, as before", book.fontWeight)
    }

    @Test fun anotherWeightIsDrawnByTheFaceAndNamedOnTheStyle() {
        lateinit var heavier: TextStyle
        compose.setContent { heavier = RibbonType.scripture(24f, weight = 470) }
        compose.waitForIdle()
        assertEquals(FontWeight(470), heavier.fontWeight)
        val face = (heavier.fontFamily as FontListFontFamily).fonts.single()
        assertEquals("Literata's own axis, at 470", FontWeight(470), face.weight)
        assertEquals(RibbonFonts.literata(FontWeight(470), 24f), heavier.fontFamily)
    }

    /**
     * Bold Text, as the page reads it: on only when the system has asked for
     * more ink. A configuration that has said nothing is undefined — a large
     * positive number — and is off, as the platform's own font resolver
     * reads it; read as "more than nothing", it would set every page heavy.
     */
    @Test fun boldTextIsOnOnlyWhenTheSystemAsksForIt() {
        var asked by mutableStateOf(Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED)
        var read: Boolean? = null
        compose.setContent {
            val base = LocalConfiguration.current
            val configuration = Configuration(base).apply { fontWeightAdjustment = asked }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                read = rememberBoldText()
            }
        }
        compose.waitForIdle()
        assertEquals("undefined is off", false, read)
        asked = 0
        compose.waitForIdle()
        assertEquals("nothing added is off", false, read)
        asked = 300
        compose.waitForIdle()
        assertEquals("Bold Text's 300 is on", true, read)
    }
}
