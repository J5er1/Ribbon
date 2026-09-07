import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Copies Scripture, the three OFL faces and the paper grain out of the
 * shared resources (ios/Ribbon/Resources) and into the Android assets.
 *
 * They live in the repo once. Two copies of 11 MB of Scripture would be two
 * things to keep in step, and the second one would eventually be the stale
 * one — so the Android build takes the iOS app's copy rather than owning a
 * fork of it.
 */
@CacheableTask
abstract class SyncRibbonAssets @Inject constructor(
    objects: ObjectFactory,
    private val fs: FileSystemOperations,
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sharedResources: DirectoryProperty = objects.directoryProperty()

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun sync() {
        val from = sharedResources.get()
        fs.sync {
            into(outputDirectory)
            from(from.dir("Scripture")) { into("scripture") }
            from(from.dir("Fonts")) {
                into("fonts")
                include("*.ttf")
                // Android assets take the brackets fine, but a variable font
                // named "Literata[opsz,wght].ttf" is a filename nobody wants
                // to type twice.
                rename("""Literata\[opsz,wght]\.ttf""", "Literata.ttf")
                rename("""Literata-Italic\[opsz,wght]\.ttf""", "Literata-Italic.ttf")
            }
            from(from.dir("Fonts")) {
                into("fonts")
                include("OFL-*.txt")
            }
            from(from) {
                include("PaperGrain.png")
                rename { "paper_grain.png" }
            }
        }
    }
}
