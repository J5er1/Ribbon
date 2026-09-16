package app.readribbon.design

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit

// Whether the room takes its colour from the wallpaper (deviation A18).
//
// This is the one preference that cannot live in `AppSettings` with the
// others, and the reason is timing rather than taste: `AppState` is a JSON
// file read off disk on a background dispatcher, so a theme that waited for
// it would paint one palette on the first frame and a different one a
// moment later. A repaint on launch is exactly the "instability on the
// front door" S01 forbids.
//
// So it is a two-value preference file, read synchronously before the first
// composition, held as snapshot state so the toggle takes effect on the
// frame it is flipped, and written through on every change. Nothing else
// belongs here.

private const val FILE = "ribbon.appearance"
private const val KEY_WALLPAPER = "wallpaper_colour"

/**
 * The appearance preferences, live.
 *
 * @param context any context; the preference file is process-wide.
 */
@Stable
class Appearance(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private var stored: Boolean by mutableStateOf(prefs.getBoolean(KEY_WALLPAPER, true))

    /**
     * Take the room's colour from the wallpaper.
     *
     * Defaults to on. Material You is why this app is built on Android at
     * all (owner's call, deviation A18), so declining it is the choice that
     * has to be made rather than the one that has to be found.
     *
     * Reading it is snapshot state, so the theme recomposes on the frame it
     * changes; writing it goes to disk in the same breath, because the next
     * launch reads the file before anything is composed at all.
     */
    var wallpaperColour: Boolean
        get() = stored
        set(value) {
            if (value == stored) return
            stored = value
            prefs.edit { putBoolean(KEY_WALLPAPER, value) }
        }
}

/**
 * The appearance, hung off the composition so the one screen that changes it
 * can just ask.
 *
 * There is no sensible default — an `Appearance` needs a context — so this
 * fails loudly rather than silently pretending the wallpaper is declined.
 */
val LocalAppearance = staticCompositionLocalOf<Appearance> {
    error("No Appearance in the composition. RibbonTheme provides it.")
}
