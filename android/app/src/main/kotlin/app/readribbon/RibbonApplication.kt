package app.readribbon

import android.app.Application
import app.readribbon.design.RibbonFonts

class RibbonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The faces come out of the assets the build copies from the shared
        // resources; binding them once here keeps every FontFamily a plain
        // stable value rather than something rebuilt during recomposition.
        RibbonFonts.init(this)
    }
}
