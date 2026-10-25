package com.douyin.downloader.domain.usecase

import android.net.Uri
import com.douyin.downloader.data.repository.DownloadRepository
import javax.inject.Inject

class SynthesizeVideoUseCase @Inject constructor(
    private val downloadRepo: DownloadRepository,
) {
    suspend fun mergeWithMusic(
        videoUrl: String,
        musicUrl: String?,
        filename: String = "douyin_animated.mp4",
    ): Uri {
        return downloadRepo.mergeVideoMusic(videoUrl, musicUrl, filename)
    }
}
