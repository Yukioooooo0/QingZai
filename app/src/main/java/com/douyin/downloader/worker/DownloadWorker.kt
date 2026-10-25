package com.douyin.downloader.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.douyin.downloader.App
import com.douyin.downloader.R
import com.douyin.downloader.domain.usecase.DownloadImagesUseCase
import com.douyin.downloader.domain.usecase.DownloadVideoUseCase
import com.douyin.downloader.domain.usecase.SynthesizeVideoUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadVideoUseCase: DownloadVideoUseCase,
    private val downloadImagesUseCase: DownloadImagesUseCase,
    private val synthesizeVideoUseCase: SynthesizeVideoUseCase,
) : CoroutineWorker(context, params) {

    private val notificationId: Int
        get() = inputData.getString(KEY_URL)?.hashCode()?.and(0xFFFF) ?: id.hashCode()

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: "download"
        val name = inputData.getString(KEY_NAME) ?: "下载任务"

        showNotification(name, 0f)

        return try {
            when (type) {
                TYPE_VIDEO -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    downloadVideoUseCase(url, filename) { downloaded, total ->
                        if (total != null && total > 0) {
                            val progress = (downloaded.toFloat() / total).coerceAtMost(0.99f)
                            setProgressAsync(Data.Builder().putFloat(KEY_PROGRESS, progress).build())
                            showNotification(name, progress)
                        }
                    }
                }
                TYPE_IMAGE -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    downloadImagesUseCase.downloadSingle(url, filename)
                    setProgressAsync(Data.Builder().putFloat(KEY_PROGRESS, 1f).build())
                }
                TYPE_ZIP -> {
                    val urls = inputData.getStringArray(KEY_URLS) ?: return Result.failure()
                    val total = urls.size
                    val filenames = urls.indices.map { "${filename}_${it + 1}.jpg" }
                    downloadImagesUseCase.downloadMultiple(urls.toList(), filenames) { completed, _ ->
                        val progress = (completed.toFloat() / total).coerceAtMost(0.99f)
                        setProgressAsync(Data.Builder().putFloat(KEY_PROGRESS, progress).build())
                        showNotification(name, progress)
                    }
                }
                TYPE_MERGE -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    val musicUrl = inputData.getString(KEY_MUSIC_URL)
                    synthesizeVideoUseCase.mergeWithMusic(url, musicUrl, filename)
                    setProgressAsync(Data.Builder().putFloat(KEY_PROGRESS, 1f).build())
                }
                else -> return Result.failure()
            }
            showDoneNotification(name)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                showErrorNotification(name, e.message)
                Result.failure(Data.Builder().putString("error", e.message ?: "未知错误").build())
            }
        }
    }

    private fun showNotification(name: String, progress: Float) {
        try {
            val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
                .setContentTitle(name)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOnlyAlertOnce(true)
                .setProgress(100, (progress * 100).toInt(), progress <= 0f)
                .setContentText(if (progress > 0f) "${(progress * 100).toInt()}%" else "准备中...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // 没有通知权限时忽略
        }
    }

    private fun showDoneNotification(name: String) {
        try {
            val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
                .setContentTitle(name)
                .setContentText("下载完成")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun showErrorNotification(name: String, error: String?) {
        try {
            val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
                .setContentTitle(name)
                .setContentText(error ?: "下载失败")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_URL = "url"
        const val KEY_URLS = "urls"
        const val KEY_FILENAME = "filename"
        const val KEY_MUSIC_URL = "music_url"
        const val KEY_NAME = "name"
        const val KEY_PROGRESS = "progress"

        const val TYPE_VIDEO = "video"
        const val TYPE_IMAGE = "image"
        const val TYPE_ZIP = "zip"
        const val TYPE_MERGE = "merge"

        fun buildData(
            type: String,
            filename: String,
            name: String,
            url: String? = null,
            urls: Array<String>? = null,
            musicUrl: String? = null,
        ): Data {
            val builder = Data.Builder()
                .putString(KEY_TYPE, type)
                .putString(KEY_FILENAME, filename)
                .putString(KEY_NAME, name)
            url?.let { builder.putString(KEY_URL, it) }
            urls?.let { arr -> builder.putStringArray(KEY_URLS, Array<String?>(arr.size) { arr[it] }) }
            musicUrl?.let { builder.putString(KEY_MUSIC_URL, it) }
            return builder.build()
        }
    }
}
