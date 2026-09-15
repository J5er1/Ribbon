package app.readribbon.services

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `version.json` the android workflow publishes beside the APK is the
 * only update signal that actually reaches a phone.
 *
 * Its two fallbacks are both dead ends by construction: a GitHub release's
 * `published_at` does not move when its assets are replaced (the `latest`
 * release still reads the day it was first cut), and the release title is
 * the fixed string "Latest Android Preview", which carries no version to
 * parse. So if this manifest stops decoding, `checkForUpdate` returns null
 * for every build forever and nobody is told there is a new one — silently,
 * with a perfectly green workflow.
 *
 * The literal below is what the workflow's heredoc emits, field for field.
 * If one moves, the other has to.
 */
class ReleaseManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Exactly the bytes `.github/workflows/android.yml` writes. */
    private fun manifest(runNumber: Int) =
        """{"versionCode":$runNumber,"versionName":"0.1.$runNumber","apkUrl":""" +
            """"https://github.com/J5er1/Ribbon/releases/download/latest/app-debug.apk"}"""

    @Test
    fun testTheWorkflowsManifestDecodes() {
        val info = json.decodeFromString<ReleaseInfo>(manifest(84))
        assertEquals(84, info.versionCode)
        assertEquals("0.1.84", info.versionName)
        assertEquals(
            "https://github.com/J5er1/Ribbon/releases/download/latest/app-debug.apk",
            info.apkUrl,
        )
    }

    /**
     * The workflow omits `publishedAt` — there is nothing useful to put in
     * it — so the field has to stay defaulted or every manifest fails to
     * decode.
     */
    @Test
    fun testPublishedAtIsOptional() {
        assertEquals("", json.decodeFromString<ReleaseInfo>(manifest(84)).publishedAt)
    }

    /**
     * `versionCode` is the run number on both sides: the APK gets it from
     * GITHUB_RUN_NUMBER in app/build.gradle.kts, the manifest from
     * github.run_number. A newer run must read as newer than an older
     * install, which is the entire comparison `checkForUpdate` makes.
     */
    @Test
    fun testANewerRunReadsAsNewer() {
        val installed = json.decodeFromString<ReleaseInfo>(manifest(78)).versionCode
        val published = json.decodeFromString<ReleaseInfo>(manifest(84)).versionCode
        assertEquals(true, published > installed)
    }
}
