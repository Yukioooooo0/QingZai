package com.douyin.downloader.domain.usecase

import android.net.Uri
import com.douyin.downloader.data.repository.DownloadRepository
import javax.inject.Inject

class DownloadVideoUseCase @Inject constructor(
    private val downloadRepo: DownloadRepository,
) {
    suspend operator fun invoke(
        videoUrl: String,
        filename: String = "douyin_video.mp4",
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): Uri {
        return downloadRepo.saveVideoStream(videoUrl, filename, onProgress)
    }
}
