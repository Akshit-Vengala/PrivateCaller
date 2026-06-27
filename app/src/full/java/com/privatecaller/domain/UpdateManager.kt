package com.privatecaller.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.privatecaller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release that is newer than the installed build. */
data class ReleaseInfo(
    val versionName: String,   // tag without a leading "v", e.g. "1.1"
    val notes: String,         // release body / changelog
    val apkUrl: String?,       // direct .apk asset download, if attached
    val apkSizeBytes: Long,
    val pageUrl: String,       // the release page (fallback when no APK asset)
)

/** Result of checking GitHub for a newer release. */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * Checks the project's GitHub Releases for a newer version, downloads the
 * attached APK, and hands it to the system installer. Repo coordinates come
 * from [BuildConfig.GITHUB_OWNER] / [BuildConfig.GITHUB_REPO].
 */
class UpdateManager(private val context: Context) {

    private val latestReleaseApi: String
        get() = "https://api.github.com/repos/" +
            "${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    /** Queries GitHub for the latest release and compares it to this build. */
    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(latestReleaseApi)
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val versionName = tag.removePrefix("v").removePrefix("V").trim()
            val notes = json.optString("body").trim()
            val pageUrl = json.optString("html_url")

            // The updater only ships in the "full" edition, so it must fetch the
            // FULL apk — never the playstore one if both are attached. Prefer an
            // asset whose name contains "full"; fall back to the first .apk for
            // releases that attach a single, generically-named APK.
            var apkUrl: String? = null
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                val apks = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .filter { it.getString("name").endsWith(".apk", ignoreCase = true) }
                val chosen = apks.firstOrNull {
                    it.getString("name").contains("full", ignoreCase = true)
                } ?: apks.firstOrNull()
                if (chosen != null) {
                    apkUrl = chosen.getString("browser_download_url")
                    apkSize = chosen.optLong("size")
                }
            }

            val release = ReleaseInfo(versionName, notes, apkUrl, apkSize, pageUrl)
            if (isNewer(versionName, BuildConfig.VERSION_NAME)) UpdateCheck.Available(release)
            else UpdateCheck.UpToDate
        } catch (e: Exception) {
            UpdateCheck.Failed(e.message ?: "Couldn't reach GitHub")
        }
    }

    /**
     * Downloads [url] into the app cache, reporting 0f..1f progress. Returns the
     * downloaded APK file. Runs on IO; cancellation-safe via the calling scope.
     */
    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clear stale downloads so we never install an old/partial file.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "PrivateCaller-update.apk")

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("Download failed (HTTP ${conn.responseCode})")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 }
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total != null) {
                                onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
                out
            } finally {
                conn.disconnect()
            }
        }

    /** Launches the system package installer for a downloaded APK. */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        context.startActivity(intent)
    }

    /** Whether the app may install APKs without first sending the user to settings. */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Settings screen to grant "install unknown apps" for PrivateCaller. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the release page in a browser (fallback when no APK is attached). */
    fun openReleasePage(pageUrl: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PrivateCaller")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** True if [remote] is a higher dotted version than [local] (e.g. 1.2 > 1.1). */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = versionParts(remote)
        val l = versionParts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun versionParts(v: String): List<Int> =
        v.split('.', '-', '_').mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
}
