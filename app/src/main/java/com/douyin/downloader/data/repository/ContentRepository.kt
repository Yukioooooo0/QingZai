package com.douyin.downloader.data.repository

import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.remote.AnimatedVideoResolver
import com.douyin.downloader.data.remote.DouyinApi
import com.douyin.downloader.data.remote.HtmlParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: DouyinApi,
    private val parser: HtmlParser,
    private val animatedResolver: AnimatedVideoResolver,
) {
    suspend fun parseUrl(rawUrl: String): ContentInfo {
        var url = rawUrl.trim()
        val urlMatch = Regex("""https?://\S+""").find(url)
        if (urlMatch != null) {
            url = urlMatch.value
        }

        if ("v.douyin.com" in url) {
            url = api.resolveShareUrl(url)
        }

        val (type, id) = parser.extractIds(url)

        return if (type == "video") {
            fetchVideoInfo(id)
        } else {
            fetchNoteInfo(id)
        }
    }

    private suspend fun fetchVideoInfo(videoId: String): ContentInfo.Video {
        val shareUrl = "https://www.iesdouyin.com/share/video/$videoId/"
        val html = api.fetchPage(shareUrl)
        val routerData = parser.extractRouterData(html)
        return parser.parseVideoInfo(routerData, videoId)
    }

    private suspend fun fetchNoteInfo(noteId: String): ContentInfo {
        val shareUrl = "https://www.iesdouyin.com/share/note/$noteId/"
        val html = api.fetchPage(shareUrl)
        val routerData = parser.extractRouterData(html)
        val noteData = parser.parseNoteInfo(routerData, noteId)

        val isAnimated = noteData.images.size == 1

        if (isAnimated) {
            var videoUrl = ""
            try {
                videoUrl = animatedResolver.resolve(noteId)
            } catch (_: Exception) { }

            if (videoUrl.isEmpty()) {
                videoUrl = parser.findDouyinvodUrl(routerData)
            }

            return ContentInfo.Animated(
                id = noteData.noteId,
                title = noteData.title,
                author = noteData.author,
                cover = noteData.cover,
                images = noteData.images,
                musicUrl = noteData.musicUrl,
                duration = noteData.duration,
                videoUrl = videoUrl,
            )
        }

        return ContentInfo.ImageGallery(
            id = noteData.noteId,
            title = noteData.title,
            author = noteData.author,
            cover = noteData.cover,
            images = noteData.images,
            musicUrl = noteData.musicUrl,
            duration = noteData.duration,
        )
    }
}
