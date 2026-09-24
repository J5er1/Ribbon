@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.readribbon.MainActivity
import app.readribbon.R
import app.readribbon.core.Bible
import app.readribbon.core.DateInterval
import app.readribbon.core.FireState
import app.readribbon.core.Handiwork
import app.readribbon.core.Reading
import app.readribbon.core.Room
import app.readribbon.design.Brand
import app.readribbon.fire.FirePainter
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The fire on the home screen (S24) — the widget §12.2 names, with the same
// content as iOS's: the room's fire at its state on the unlit ground, and
// the book's name in small caps. Nothing else, and never a number.
//
// Drawn rather than composed. Glance turns a composable into RemoteViews,
// and the fire is a painting a RemoteViews cannot hold — so it is painted
// into a bitmap by the room's own `FirePainter`, held at one instant, with
// the book's name set under it in the room's own small-caps face (a
// RemoteViews TextView cannot carry an app's font either). One bitmap, one
// description for TalkBack, one tap that opens the room.
//
// The fire cools without the app. What the app leaves here is the open
// reading's handiwork — the thing a state is worked out from, not a state —
// and the widget works the state out again every time the system asks it
// to update, an hour apart at most, from the same engine the room uses.
// A room with no book open is the unlit ground and nothing on it: an empty
// state is a reproach (§08), so the widget is absent rather than empty.

@Serializable
private data class FireSnapshot(
    val roomID: String,
    val book: String? = null,
    val handiwork: Handiwork? = null,
    /** The room's quiet days, as the spans they bank the fire over, in ms. */
    val banked: List<Pair<Long, Long>> = emptyList(),
)

object FireWidget {
    private const val FILE = "fire_widget.json"
    private const val SIDE = 360
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Leave the widget what it draws from, and redraw it only if that
     * changed. Called when the app goes away and after every pull: the home
     * screen cannot be seen while the app is open.
     */
    fun update(context: Context, room: Room?, reading: Reading?, banked: List<DateInterval>) {
        val snapshot = room?.let {
            FireSnapshot(
                roomID = it.id.toString(),
                book = reading?.let { open -> Bible.book(open.bookID)?.name },
                handiwork = reading?.handiwork,
                banked = banked.map { span ->
                    span.start.toEpochMilliseconds() to span.end.toEpochMilliseconds()
                },
            )
        }
        // A widget is never worth a failure anywhere else: whatever goes
        // wrong here leaves the home screen as it was.
        runCatching {
            val file = File(context.filesDir, FILE)
            val text = snapshot?.let { json.encodeToString(FireSnapshot.serializer(), it) }
            val before = runCatching { file.readText() }.getOrNull()
            if (text == before) return
            if (text == null) file.delete() else file.writeText(text)
            redraw(context)
        }
    }

    private fun redraw(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, FireWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(context))
    }

    private fun read(context: Context): FireSnapshot? = runCatching {
        json.decodeFromString(FireSnapshot.serializer(), File(context.filesDir, FILE).readText())
    }.getOrNull()

    /** The widget as it is now. */
    fun views(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.fire_widget)
        val snapshot = read(context)
        val handiwork = snapshot?.handiwork
        val book = snapshot?.book
        if (snapshot == null || handiwork == null || book == null) {
            views.setImageViewBitmap(R.id.fire_widget_image, null)
            views.setContentDescription(R.id.fire_widget_image, null)
        } else {
            val banked = snapshot.banked.map { (start, end) ->
                DateInterval(Instant.fromEpochMilliseconds(start), Instant.fromEpochMilliseconds(end))
            }
            val state = handiwork.state(now = Clock.System.now(), bankedIntervals = banked)
            views.setImageViewBitmap(R.id.fire_widget_image, paint(context, state, handiwork, book))
            views.setContentDescription(
                R.id.fire_widget_image, "The fire is ${state.displayName}. $book.")
        }
        snapshot?.let { views.setOnClickPendingIntent(android.R.id.background, open(context, it.roomID)) }
        return views
    }

    /** The room's own fire, held at one instant, and the book's name under it. */
    private fun paint(context: Context, state: FireState, handiwork: Handiwork, book: String): Bitmap {
        val image = ImageBitmap(SIDE, SIDE)
        val canvas = androidx.compose.ui.graphics.Canvas(image)
        CanvasDrawScope().draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(SIDE.toFloat(), SIDE.toFloat()),
        ) {
            drawRect(Brand.ground)
            inset(left = 36f, top = 28f, right = 36f, bottom = 92f) {
                FirePainter.draw(
                    into = this, time = 402.7,
                    state = state, scale = handiwork.scale, coalDepth = handiwork.coalDepth,
                )
            }
        }
        val bitmap = image.asAndroidBitmap()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching {
                Typeface.createFromAsset(context.assets, "fonts/AlegreyaSansSC-Regular.ttf")
            }.getOrDefault(Typeface.SANS_SERIF)
            textSize = 30f
            letterSpacing = 0.075f
            color = Brand.muted.toArgb()
            textAlign = Paint.Align.CENTER
        }
        // A long name is set smaller rather than cut: "Song of Songs" is a
        // name, and a name is never truncated.
        val room = SIDE - 48f
        val width = text.measureText(book)
        if (width > room) text.textSize *= room / width
        android.graphics.Canvas(bitmap).drawText(book, SIDE / 2f, SIDE - 40f, text)
        return bitmap
    }

    private fun open(context: Context, roomID: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Notifications.ACTION_OPEN
            putExtra(Notifications.EXTRA_ROOM, roomID)
        }
        return PendingIntent.getActivity(
            context, roomID.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * The system's side of the widget. Asked to update when a widget is placed
 * and then on the hour, at most — which is when the fire's state is worked
 * out again from what the app left.
 */
class FireWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, FireWidget.views(context))
    }
}
