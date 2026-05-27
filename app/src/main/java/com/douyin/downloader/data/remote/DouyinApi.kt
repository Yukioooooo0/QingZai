package com.douyin.downloader.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DouyinApi @Inject constructor(private val client: OkHttpClient) {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 " +
                "Mobile/15E148 Safari/604.1"
        private const val REFERER = "https://www.douyin.com/"
    }

    suspend fun resolveShareUrl(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .head()
            .build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("空响应")
        }
    }

    suspend fun downloadBytes(url: String, timeoutSeconds: Int = 30): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()
            client.newBuilder()
                .connectTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.bytes() ?: throw Exception("空响应")
                }
        }

    suspend fun downloadWithProgress(
        url: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("空响应")
            val total = body.contentLength().takeIf { it != -1L }
            val input = body.byteStream()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
            output.toByteArray()
        }
    }

    suspend fun streamTo(
        url: String,
        output: java.io.OutputStream,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        client.newBuilder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val body = response.body ?: throw Exception("空响应")
                val total = body.contentLength().takeIf { it != -1L }
                val input = body.byteStream()
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
                downloaded
            }
    }
}
