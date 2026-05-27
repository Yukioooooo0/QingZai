package com.douyin.downloader.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
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

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: "download"

        return try {
            when (type) {
                TYPE_VIDEO -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    downloadVideoUseCase(url, filename)
                }
                TYPE_IMAGE -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    downloadImagesUseCase.downloadSingle(url, filename)
                }
                TYPE_ZIP -> {
                    val urls = inputData.getStringArray(KEY_URLS) ?: return Result.failure()
                    val filenames = urls.indices.map { "${filename}_${it + 1}.jpg" }
                    downloadImagesUseCase.downloadMultiple(urls.toList(), filenames)
                }
                TYPE_SYNTHESIZE -> {
                    val urls = inputData.getStringArray(KEY_URLS) ?: return Result.failure()
                    val musicUrl = inputData.getString(KEY_MUSIC_URL)
                    val duration = inputData.getInt(KEY_DURATION, 0)
                    synthesizeVideoUseCase.fromImages(urls.toList(), musicUrl, duration, filename)
                }
                TYPE_MERGE -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    val musicUrl = inputData.getString(KEY_MUSIC_URL)
                    synthesizeVideoUseCase.mergeWithMusic(url, musicUrl, filename)
                }
                else -> return Result.failure()
            }
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_URL = "url"
        const val KEY_URLS = "urls"
        const val KEY_FILENAME = "filename"
        const val KEY_MUSIC_URL = "music_url"
        const val KEY_DURATION = "duration"

        const val TYPE_VIDEO = "video"
        const val TYPE_IMAGE = "image"
        const val TYPE_ZIP = "zip"
        const val TYPE_SYNTHESIZE = "synthesize"
        const val TYPE_MERGE = "merge"
    }
}
