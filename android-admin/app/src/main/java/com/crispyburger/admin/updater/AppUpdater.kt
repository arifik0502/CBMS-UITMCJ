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
import java.util.Locale

data class AppRelease(val version: String, val notes: String, val downloadUrl: String)

internal fun isAllowedApkDownloadResponse(contentType: String?, finalUrl: String): Boolean {
    val uri = runCatching { java.net.URI(finalUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    val trustedHost = host == "github.com" || host.endsWith(".githubusercontent.com")
    if (!trustedHost) return false

    val mimeType = contentType.orEmpty()
        .substringBefore(';')
        .trim()
        .lowercase(Locale.ROOT)
    val validMimeType = mimeType in setOf(
        "application/vnd.android.package-archive",
        "application/octet-stream",
        "application/zip"
    )
    val apkPath = uri.path?.substringAfterLast('/')?.endsWith(".apk", ignoreCase = true) == true
    return validMimeType || apkPath
}

object AppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/arifik0502/CBMS-UITMCJ/releases/latest"

    suspend fun check(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CBMS-Admin-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub release check failed (${connection.responseCode}).")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = release.optString("tag_name").removePrefix("v")
            val current = context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName.orEmpty().removePrefix("v")
            val apkUrl = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
            }.orEmpty()
            if (latest.isBlank() || apkUrl.isBlank() || compareVersions(latest, current) <= 0) {
                return@withContext null
            }
            AppRelease(latest, release.optString("body", "New CBMS Admin update available."), apkUrl)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
        val apk = File(directory, "cbms-admin-update.apk")
        val partialApk = File(directory, "cbms-admin-update.apk.part")
        if (partialApk.exists()) partialApk.delete()

        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CBMS-Admin-Android")
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("APK download failed (${connection.responseCode}).")
            }
            if (!isAllowedApkDownloadResponse(connection.contentType, connection.url.toString())) {
                throw IllegalStateException("GitHub returned an unexpected file instead of the CBMS APK.")
            }

            connection.inputStream.use { input ->
                partialApk.outputStream().use { output -> input.copyTo(output) }
            }

            if (partialApk.length() < 100_000L) {
                throw IllegalStateException("Downloaded APK appears incomplete.")
            }

            val archiveInfo = context.packageManager.getPackageArchiveInfo(partialApk.absolutePath, 0)
                ?: throw IllegalStateException("Downloaded file is not a valid Android APK.")
            if (archiveInfo.packageName != context.packageName) {
                throw IllegalStateException("Downloaded APK belongs to a different application.")
            }

            val currentInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentCode = if (Build.VERSION.SDK_INT >= 28) currentInfo.longVersionCode else currentInfo.versionCode.toLong()
            val downloadedCode = if (Build.VERSION.SDK_INT >= 28) archiveInfo.longVersionCode else archiveInfo.versionCode.toLong()
            if (downloadedCode <= currentCode) {
                throw IllegalStateException("Downloaded APK is not newer than the installed version.")
            }

            if (apk.exists() && !apk.delete()) {
                throw IllegalStateException("Unable to replace the previous downloaded update.")
            }
            if (!partialApk.renameTo(apk)) {
                throw IllegalStateException("Unable to prepare the downloaded APK for installation.")
            }
        } catch (error: Exception) {
            partialApk.delete()
            throw error
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
