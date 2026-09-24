package app.readribbon

import android.app.Application
import app.readribbon.design.RibbonFonts
import app.readribbon.services.Notifications
import app.readribbon.services.Push

class RibbonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The faces come out of the assets the build copies from the shared
        // resources; binding them once here keeps every FontFamily a plain
        // stable value rather than something rebuilt during recomposition.
        RibbonFonts.init(this)

        // The five notification channels (S19). Here rather than anywhere
        // else because this is the only place that runs before anything could
        // post and after the process exists — and because a channel must
        // exist before its first notification or the post is dropped without
        // a word. Creating one that is already there updates its name and
        // leaves the importance the person chose alone, so this is safe to
        // run on every launch and is not gated on a flag.
        Notifications.createChannels(this)

        // Push (S19), when the build carries a Firebase configuration. Before
        // anything else can ask for a token, and before a message that
        // started this process is handed to the service.
        Push.configure(this)
    }
}
