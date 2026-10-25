package com.douyin.downloader.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.douyin.downloader.data.remote.DouyinApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: DouyinApi,
) {
    // FFmpegKit 是重 CPU 操作：全局串行化，避免多个合成任务同时编码互相拖慢（实测并发 6 个时每个仅 ~0.2x 速度）
    private val ffmpegMutex = Mutex()

    suspend fun saveVideo(bytes: ByteArray, filename: String): Uri =
        saveToMediaStore(bytes, filename, "video/mp4", MediaStore.Downloads.EXTERNAL_CONTENT_URI)

    suspend fun saveVideoStream(
        videoUrl: String,
        filename: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): Uri = withContext(Dispatchers.IO) {
        val uri = createMediaStoreUri(filename, "video/mp4", MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            api.streamTo(videoUrl, output, onProgress)
        } ?: throw Exception("无法写入文件")
        finalizeMediaStoreUri(uri)
        uri
    }

    suspend fun saveImage(bytes: ByteArray, filename: String): Uri =
        saveToMediaStore(bytes, filename, "image/*", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

    suspend fun saveImagesZip(imageUrls: List<String>, filename: String): Uri =
        withContext(Dispatchers.IO) {
            val buf = java.io.ByteArrayOutputStream()
            ZipOutputStream(buf).use { zos ->
                imageUrls.forEachIndexed { i, url ->
                    val bytes = api.downloadBytes(url)
                    val ext = inferImageExt(url)
                    zos.putNextEntry(ZipEntry("image_${i + 1}$ext"))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            saveToMediaStore(buf.toByteArray(), filename, "application/zip", MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }

    suspend fun mergeVideoMusic(
        videoUrl: String,
        musicUrl: String?,
        filename: String,
    ): Uri = withContext(Dispatchers.IO) {
        if (musicUrl.isNullOrEmpty()) {
            val bytes = api.downloadBytes(videoUrl, timeoutSeconds = 60)
            return@withContext saveToMediaStore(bytes, filename, "video/mp4", MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }

        val tmpDir = File(context.cacheDir, "dy_merge_${System.currentTimeMillis()}")
        tmpDir.mkdirs()

        try {
            val videoFile = File(tmpDir, "video.mp4")
            videoFile.writeBytes(api.downloadBytes(videoUrl, timeoutSeconds = 60))

            val musicFile = File(tmpDir, "music.mp3")
            musicFile.writeBytes(api.downloadBytes(musicUrl))

            val outputFile = File(tmpDir, "output.mp4")
            val command = "-i ${videoFile.absolutePath} -i ${musicFile.absolutePath} " +
                "-c:v copy -c:a aac -b:a 192k -map 0:v:0 -map 1:a:0 -shortest " +
                outputFile.absolutePath

            val session = ffmpegMutex.withLock { FFmpegKit.execute(command) }
            if (session.returnCode.isValueError) {
                throw Exception("ffmpeg 合并失败: ${session.output.takeLast(500)}")
            }

            val bytes = outputFile.readBytes()
            saveToMediaStore(bytes, filename, "video/mp4", MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun saveToMediaStore(
        bytes: ByteArray,
        filename: String,
        mimeType: String,
        collection: Uri,
    ): Uri {
        val uri = createMediaStoreUri(filename, mimeType, collection)
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw Exception("无法写入文件")
        finalizeMediaStoreUri(uri)
        return uri
    }

    private fun createMediaStoreUri(filename: String, mimeType: String, collection: Uri): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        return context.contentResolver.insert(collection, contentValues)
            ?: throw Exception("无法创建 MediaStore 条目")
    }

    private fun finalizeMediaStoreUri(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
        }
    }

    private fun inferImageExt(url: String): String {
        val lower = url.lowercase()
        return when {
            ".jpeg" in lower || ".jpg" in lower -> ".jpg"
            ".png" in lower -> ".png"
            else -> ".webp"
        }
    }
}
