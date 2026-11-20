package com.crispyburger.admin.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads a short-lived signed receipt URL into a bitmap so it can be shown in-app. */
object ReceiptLoader {
    private const val MAX_BYTES = 12 * 1024 * 1024

    suspend fun download(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                val bytes = connection.inputStream.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return@withContext null
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
