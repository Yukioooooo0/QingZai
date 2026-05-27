package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.ContentInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HtmlParser @Inject constructor() {

    fun extractIds(url: String): Pair<String, String> {
        Regex("""/(?:share/)?video/(\d+)""").find(url)?.let {
            return "video" to it.groupValues[1]
        }
        Regex("""/(?:share/)?note/(\d+)""").find(url)?.let {
            return "note" to it.groupValues[1]
        }
        Regex("""item_ids=(\d+)""").find(url)?.let {
            return "video" to it.groupValues[1]
        }
        throw IllegalArgumentException("无法从链接中提取ID")
    }

    fun extractRouterData(html: String): String {
        val marker = "window._ROUTER_DATA"
        val startIdx = html.indexOf(marker)
        if (startIdx < 0) throw ValueError("无法解析页面数据")

        val eqIdx = html.indexOf("=", startIdx)
        if (eqIdx < 0) throw ValueError("无法解析页面数据")

        val braceStart = html.indexOf("{", eqIdx)
        if (braceStart < 0) throw ValueError("无法解析页面数据")

        var depth = 0
        for (i in braceStart until html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) {
                val rawJson = html.substring(braceStart, i + 1)
                return decodeUnicodeEscapes(rawJson)
            }
        }
        throw ValueError("无法解析页面数据")
    }

    fun parseVideoInfo(routerData: String, videoId: String): ContentInfo.Video {
        val playMatch = Regex(""""(https?://[^"]*playwm[^"]*)"""").find(routerData)
            ?: throw ValueError("无法找到视频地址")
        val noWmUrl = playMatch.groupValues[1].replace("playwm", "play")

        val title = extractField(routerData, "desc")
        val author = extractField(routerData, "nickname")
        val cover = extractCover(routerData)

        return ContentInfo.Video(
            id = videoId,
            title = title,
            author = author,
            cover = cover,
            videoUrl = noWmUrl,
        )
    }

    fun parseNoteInfo(routerData: String, noteId: String): NoteRawData {
        val images = extractImages(routerData)
        if (images.isEmpty()) throw ValueError("无法找到图片")

        val musicUrl = Regex(""""uri"\s*:\s*"(https?://[^"]*\.mp3[^"]*)"""")
            .find(routerData)?.groupValues?.get(1) ?: ""

        val duration = Regex(""""duration"\s*:\s*(\d+)""")
            .find(routerData)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val title = extractField(routerData, "desc")
        val author = extractField(routerData, "nickname")
        val cover = extractCover(routerData).ifEmpty { images.first() }

        return NoteRawData(
            noteId = noteId,
            title = title,
            author = author,
            cover = cover,
            images = images,
            musicUrl = musicUrl,
            duration = duration,
        )
    }

    fun findDouyinvodUrl(text: String): String {
        val match = Regex("""https?://[^"]*douyinvod[^"]*""").find(text)
        return match?.value ?: ""
    }

    private fun extractImages(routerData: String): List<String> {
        val start = routerData.indexOf(""""images":[{""")
        if (start < 0) return emptyList()

        val bracketStart = routerData.indexOf("[", start)
        if (bracketStart < 0) return emptyList()

        var depth = 0
        val end = minOf(bracketStart + 200_000, routerData.length)
        for (j in bracketStart until end) {
            when (routerData[j]) {
                '[' -> depth++
                ']' -> depth--
            }
            if (depth == 0) {
                val imagesStr = routerData.substring(bracketStart, j + 1)
                return Regex(""""url_list"\s*:\s*\[\s*"(https?://[^"]+)"""")
                    .findAll(imagesStr)
                    .map { it.groupValues[1] }
                    .toList()
            }
        }
        return emptyList()
    }

    private fun extractField(data: String, field: String): String {
        val match = Regex(""""$field"\s*:\s*"(.*?)"""").find(data)
        return match?.groupValues?.get(1)?.let { decodeDouyinText(it) } ?: ""
    }

    private fun extractCover(data: String): String {
        val match = Regex(""""cover".*?"url_list"\s*:\s*\[\s*"(.*?)"""").find(data)
        return match?.groupValues?.get(1) ?: ""
    }

    fun decodeDouyinText(text: String): String {
        return try {
            decodeUnicodeEscapes(text)
        } catch (_: Exception) {
            text
        }
    }

    private fun decodeUnicodeEscapes(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (i + 5 < text.length && text[i] == '\\' && text[i + 1] == 'u') {
                val hex = text.substring(i + 2, i + 6)
                try {
                    val codePoint = hex.toInt(16)
                    sb.append(codePoint.toChar())
                    i += 6
                    continue
                } catch (_: NumberFormatException) { }
            }
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }

    data class NoteRawData(
        val noteId: String,
        val title: String,
        val author: String,
        val cover: String,
        val images: List<String>,
        val musicUrl: String,
        val duration: Int,
    )

    class ValueError(message: String) : IllegalArgumentException(message)
}
