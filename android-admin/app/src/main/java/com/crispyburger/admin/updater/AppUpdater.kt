package com.crispyburger.admin.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val notes: String, val downloadUrl: String)

object AppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/arifik0502/CBMS-UITMCJ/releases/latest"

    suspend fun check(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CBMS-Admin-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub release check failed (${connection.responseCode}).")
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = release.optString("tag_name").removePrefix("v")
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().removePrefix("v")
            val apkUrl = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
            }.orEmpty()
            if (latest.isBlank() || apkUrl.isBlank() || compareVersions(latest, current) <= 0) return@withContext null
            AppRelease(latest, release.optString("body", "New CBMS Admin update available."), apkUrl)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
        val apk = File(directory, "cbms-admin-update.apk")
        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "CBMS-Admin-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("APK download failed (${connection.responseCode}).")
            val contentType = connection.contentType.orEmpty()
            if (!contentType.contains("apk", true) && !connection.url.path.endsWith(".apk", true)) {
                throw IllegalStateException("GitHub did not return an APK file.")
            }
            connection.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
            if (apk.length() < 100_000L) {
                apk.delete()
                throw IllegalStateException("Downloaded APK appears incomplete.")
            }
        } finally {
            connection.disconnect()
        }
        apk
    }

    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            throw InstallPermissionRequired()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split(".").map { it.toIntOrNull() ?: 0 }
        val right = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

class InstallPermissionRequired : Exception("Allow CBMS Admin to install updates, then tap Update again.")
