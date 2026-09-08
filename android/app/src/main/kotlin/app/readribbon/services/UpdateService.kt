package app.readribbon.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.readribbon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String = "",
    val apkUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float, val info: ReleaseInfo) : UpdateState
    data class ReadyToInstall(val info: ReleaseInfo, val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

object UpdateService {

    private const val VERSION_JSON_URL =
        "https://github.com/J5er1/Ribbon/releases/download/latest/version.json"
    private const val GITHUB_API_LATEST_RELEASE =
        "https://api.github.com/repos/J5er1/Ribbon/releases/tags/latest"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks whether a newer build of Ribbon is available on GitHub Releases.
     * Returns the [ReleaseInfo] if [ReleaseInfo.versionCode] > [BuildConfig.VERSION_CODE], or null.
     */
    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        // First try the static version.json asset (direct CDN fetch, zero API rate limits).
        val directInfo = fetchVersionJson(VERSION_JSON_URL)
        if (directInfo != null) {
            return@withContext if (directInfo.versionCode > BuildConfig.VERSION_CODE) directInfo else null
        }

        // Fallback: check GitHub API release endpoint.
        val apiInfo = fetchFromGitHubApi(GITHUB_API_LATEST_RELEASE)
        if (apiInfo != null && apiInfo.versionCode > BuildConfig.VERSION_CODE) {
            apiInfo
        } else {
            null
        }
    }

    private fun fetchVersionJson(urlString: String): ReleaseInfo? {
        return runCatching {
            val connection = openConnectionWithRedirects(urlString)
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<ReleaseInfo>(body)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fetchFromGitHubApi(urlString: String): ReleaseInfo? {
        return runCatching {
            val connection = openConnectionWithRedirects(urlString)
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                val publishedAt = root["published_at"]?.jsonPrimitive?.content ?: ""
                val assets = root["assets"]?.toString() ?: ""
                // Find browser_download_url for app-debug.apk
                val downloadUrl = if (assets.contains("app-debug.apk")) {
                    "https://github.com/J5er1/Ribbon/releases/download/latest/app-debug.apk"
                } else {
                    return null
                }
                // When version.json isn't present, release publish date presence indicates a build
                ReleaseInfo(
                    versionCode = BuildConfig.VERSION_CODE + 1,
                    versionName = "latest",
                    publishedAt = publishedAt,
                    apkUrl = downloadUrl,
                )
            } else {
                null
            }
        }.getOrNull()
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Downloads the APK file to the app's cache directory and streams byte progress.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, "ribbon-update.apk")
        if (apkFile.exists()) apkFile.delete()

        val connection = openConnectionWithRedirects(apkUrl)
        val contentLength = connection.contentLengthLong

        connection.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        onProgress(bytesRead.toFloat() / contentLength.toFloat())
                    }
                }
            }
        }
        apkFile
    }

    /**
     * Dispatches the system package installer Intent via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var connection: HttpURLConnection
        for (i in 0 until 5) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Ribbon-Android/${BuildConfig.VERSION_NAME}")

            val status = connection.responseCode
            if (status in listOf(
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308,
                )
            ) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (!newUrl.isNullOrEmpty()) {
                    currentUrl = newUrl
                    continue
                }
            }
            return connection
        }
        throw java.io.IOException("Too many redirects: $initialUrl")
    }
}
