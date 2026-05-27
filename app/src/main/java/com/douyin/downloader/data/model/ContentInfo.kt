package com.douyin.downloader.data.model

sealed class ContentInfo {
    abstract val id: String
    abstract val title: String
    abstract val author: String
    abstract val cover: String

    data class Video(
        override val id: String,
        override val title: String,
        override val author: String,
        override val cover: String,
        val videoUrl: String,
    ) : ContentInfo()

    data class ImageGallery(
        override val id: String,
        override val title: String,
        override val author: String,
        override val cover: String,
        val images: List<String>,
        val musicUrl: String,
        val duration: Int,
    ) : ContentInfo()

    data class Animated(
        override val id: String,
        override val title: String,
        override val author: String,
        override val cover: String,
        val images: List<String>,
        val musicUrl: String,
        val duration: Int,
        val videoUrl: String,
    ) : ContentInfo()
}
