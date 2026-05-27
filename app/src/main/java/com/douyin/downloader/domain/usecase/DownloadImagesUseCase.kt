package com.douyin.downloader.domain.usecase

import android.net.Uri
import com.douyin.downloader.data.remote.DouyinApi
import com.douyin.downloader.data.repository.DownloadRepository
import javax.inject.Inject

class DownloadImagesUseCase @Inject constructor(
    private val api: DouyinApi,
    private val downloadRepo: DownloadRepository,
) {
    suspend fun downloadSingle(imageUrl: String, filename: String): Uri {
        val bytes = api.downloadBytes(imageUrl)
        return downloadRepo.saveImage(bytes, filename)
    }

    suspend fun downloadMultiple(
        imageUrls: List<String>,
        filenames: List<String>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Uri> {
        val total = imageUrls.size
        return imageUrls.mapIndexed { i, url ->
            val uri = downloadSingle(url, filenames[i])
            onProgress(i + 1, total)
            uri
        }
    }
}
