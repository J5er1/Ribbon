package app.readribbon.design

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// The four faces (brand brief §9):
//   Scripture & reading — Literata. Must never feel like an interface.
//   Interface — Alegreya Sans.
//   Metadata — Alegreya Sans SC: true small caps, the way a book sets a
//   running head. No monospace anywhere; everything a mono face would have
//   carried goes into small caps instead.
//   Display — Cesso in the brand; Cesso is an Adobe face that cannot be
//   embedded (build book §12.1, open question §16.12), so in-app display
//   type is Literata standing in. Documented in docs/deviations.md.
//
// §12.2 maps M3 Expressive's emphasised type roles onto these: Literata for
// display and reading body, Alegreya Sans for everything else, and small
// caps from a real face rather than a textTransform.

/**
 * The faces, loaded from the assets the build copies out of the shared
 * resources. Initialised once from [RibbonFonts.init] in the Application, so
 * a FontFamily is a plain stable value everywhere else and never has to be
 * rebuilt during recomposition.
 */
object RibbonFonts {

    private lateinit var assets: AssetManager

    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    /**
     * Literata is a variable face on two axes. Weight is set per style;
     * optical size is set from the rendered point size, which is the whole
     * reason to carry a variable font — a 34 pt book name and a 17 pt verse
     * want genuinely different letterforms, not the same one scaled.
     */
    fun literata(
        weight: FontWeight = FontWeight.Normal,
        opticalSize: Float = 14f,
        italic: Boolean = false,
    ): FontFamily = FontFamily(
        Font(
            path = if (italic) "fonts/Literata-Italic.ttf" else "fonts/Literata.ttf",
            assetManager = assets,
            weight = weight,
            style = if (italic) FontStyle.Italic else FontStyle.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.opticalSizing(opticalSize.sp),
            ),
        ),
    )

    val sans: FontFamily by lazy {
        FontFamily(
            Font("fonts/AlegreyaSans-Regular.ttf", assets, FontWeight.Normal),
            Font("fonts/AlegreyaSans-Italic.ttf", assets, FontWeight.Normal, FontStyle.Italic),
            Font("fonts/AlegreyaSans-Medium.ttf", assets, FontWeight.Medium),
            Font("fonts/AlegreyaSans-Bold.ttf", assets, FontWeight.Bold),
        )
    }

    /** True small caps — a real face, never a textTransform (§12.2). */
    val sansSmallCaps: FontFamily by lazy {
        FontFamily(
            Font("fonts/AlegreyaSansSC-Regular.ttf", assets, FontWeight.Normal),
            Font("fonts/AlegreyaSansSC-Medium.ttf", assets, FontWeight.Medium),
        )
    }
}

object RibbonType {

    /**
     * Scripture body — sized by the reader (S20) and scaled by the system
     * font scale. Nothing in the reading surface uses a fixed point size
     * (§11), which is why this takes the size rather than owning it.
     */
    @Composable
    fun scripture(size: Float): TextStyle {
        val family = remember(size) { RibbonFonts.literata(FontWeight.Normal, size) }
        return TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            lineHeight = (size * 1.62f).sp,
        )
    }

    /** Display — book names, the finishing line. Literata standing in for Cesso. */
    @Composable
    fun display(size: Float = 34f, weight: FontWeight = FontWeight.Medium): TextStyle {
        val family = remember(size, weight) { RibbonFonts.literata(weight, size) }
        return TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            lineHeight = (size * 1.2f).sp,
        )
    }

    /** Interface text. */
    fun ui(size: Float = 17f, weight: FontWeight = FontWeight.Normal): TextStyle = TextStyle(
        fontFamily = RibbonFonts.sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.4f).sp,
    )

    /**
     * True small caps. Set strings in normal sentence case; the face does
     * the capitalisation work. The tracking matches the iOS build's
     * kerning of 7.5% of the size, so a running head sets identically on
     * both platforms.
     */
    fun smallCaps(
        size: Float = 13f,
        weight: FontWeight = FontWeight.Normal,
    ): TextStyle = TextStyle(
        fontFamily = RibbonFonts.sansSmallCaps,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = (size * 0.075f).sp,
    )
}

/** Scales a design point size by the system font scale, for the places that
 *  need a raw number rather than a TextStyle (the reading measure's own
 *  layout maths). */
@Composable
fun scaledSp(size: Float): TextUnit {
    LocalDensity.current // recompose when the scale changes
    return size.sp
}
