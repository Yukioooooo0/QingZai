package com.douyin.downloader.domain.usecase

import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.repository.ContentRepository
import javax.inject.Inject

class ParseUrlUseCase @Inject constructor(
    private val repository: ContentRepository,
) {
    suspend operator fun invoke(rawUrl: String): ContentInfo {
        return repository.parseUrl(rawUrl)
    }
}
