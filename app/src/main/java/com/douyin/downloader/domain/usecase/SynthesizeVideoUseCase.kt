package com.douyin.downloader.domain.usecase

import android.net.Uri
import com.douyin.downloader.data.repository.DownloadRepository
import javax.inject.Inject

class SynthesizeVideoUseCase @Inject constructor(
    private val downloadRepo: DownloadRepository,
) {
    suspend fun fromImages(
        imageUrls: List<String>,
        musicUrl: String?,
        duration: Int,
        filename: String = "douyin_note.mp4",
    ): Uri {
        return downloadRepo.synthesizeVideo(imageUrls, musicUrl, duration, filename)
    }

    suspend fun mergeWithMusic(
        videoUrl: String,
        musicUrl: String?,
        filename: String = "douyin_animated.mp4",
    ): Uri {
        return downloadRepo.mergeVideoMusic(videoUrl, musicUrl, filename)
    }
}
