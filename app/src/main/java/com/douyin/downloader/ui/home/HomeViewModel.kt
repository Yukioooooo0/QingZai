package com.douyin.downloader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.local.DownloadTaskDao
import com.douyin.downloader.data.local.DownloadTaskEntity
import com.douyin.downloader.data.local.HistoryDao
import com.douyin.downloader.data.local.HistoryEntity
import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.domain.usecase.DownloadImagesUseCase
import com.douyin.downloader.domain.usecase.DownloadVideoUseCase
import com.douyin.downloader.domain.usecase.ParseUrlUseCase
import com.douyin.downloader.domain.usecase.SynthesizeVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val parseUrlUseCase: ParseUrlUseCase,
    private val downloadVideoUseCase: DownloadVideoUseCase,
    private val downloadImagesUseCase: DownloadImagesUseCase,
    private val synthesizeVideoUseCase: SynthesizeVideoUseCase,
    private val historyDao: HistoryDao,
    private val downloadTaskDao: DownloadTaskDao,
) : ViewModel() {

    enum class TaskStatus { PENDING, DOWNLOADING, DONE, ERROR }

    data class DownloadTask(
        val id: Long,
        val name: String,
        val status: TaskStatus,
        val progress: Float? = null,
        val error: String? = null,
    )

    data class UiState(
        val inputUrl: String = "",
        val isLoading: Boolean = false,
        val loadingMessage: String = "",
        val error: String? = null,
        val contentInfo: ContentInfo? = null,
        val galleryIndex: Int = 0,
        val selectedImages: Set<Int> = emptySet(),
        val history: List<HistoryEntity> = emptyList(),
        val downloadTasks: List<DownloadTask> = emptyList(),
        val downloadHistory: List<DownloadTaskEntity> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val taskIdCounter = AtomicLong(0)
    private val downloadSemaphore = Semaphore(2)

    init {
        viewModelScope.launch {
            historyDao.getRecent().collect { items ->
                _uiState.update { it.copy(history = items) }
            }
        }
        viewModelScope.launch {
            downloadTaskDao.getRecent().collect { items ->
                _uiState.update { it.copy(downloadHistory = items) }
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(inputUrl = url, error = null) }
    }

    fun onPaste(text: String) {
        _uiState.update { it.copy(inputUrl = text) }
        onParse()
    }

    fun onParse() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isEmpty()) return

        _uiState.update { it.copy(isLoading = true, loadingMessage = "正在解析...", error = null, contentInfo = null) }

        viewModelScope.launch {
            try {
                val info = parseUrlUseCase(url)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        contentInfo = info,
                        galleryIndex = 0,
                        selectedImages = getImages(info).indices.toSet(),
                    )
                }
                addToHistory(info)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "解析失败")
                }
            }
        }
    }

    private fun enqueueDownload(name: String, block: suspend (taskId: Long) -> Unit) {
        val id = taskIdCounter.incrementAndGet()
        val task = DownloadTask(id = id, name = name, status = TaskStatus.PENDING)
        _uiState.update { it.copy(downloadTasks = it.downloadTasks + task) }

        viewModelScope.launch {
            downloadSemaphore.withPermit {
                updateTask(id) { it.copy(status = TaskStatus.DOWNLOADING) }
                try {
                    block(id)
                    updateTask(id) { it.copy(status = TaskStatus.DONE, progress = 1f) }
                    downloadTaskDao.insert(DownloadTaskEntity(name = name, status = "DONE"))
                    removeTask(id)
                } catch (e: Exception) {
                    updateTask(id) { it.copy(status = TaskStatus.ERROR, error = e.message) }
                    downloadTaskDao.insert(DownloadTaskEntity(name = name, status = "ERROR", error = e.message))
                }
            }
        }
    }

    private fun updateTask(id: Long, transform: (DownloadTask) -> DownloadTask) {
        _uiState.update { state ->
            state.copy(downloadTasks = state.downloadTasks.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    private fun removeTask(id: Long) {
        _uiState.update { state ->
            state.copy(downloadTasks = state.downloadTasks.filterNot { it.id == id })
        }
    }

    fun onGalleryIndexChanged(index: Int) {
        _uiState.update { it.copy(galleryIndex = index) }
    }

    fun onImageToggled(index: Int) {
        _uiState.update { state ->
            val selected = state.selectedImages.toMutableSet()
            if (index in selected) selected.remove(index) else selected.add(index)
            state.copy(selectedImages = selected)
        }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            val images = getImages(state.contentInfo)
            val allSelected = state.selectedImages.size == images.size
            state.copy(selectedImages = if (allSelected) emptySet() else images.indices.toSet())
        }
    }

    fun onDownloadVideo() {
        val info = _uiState.value.contentInfo
        val videoUrl = when (info) {
            is ContentInfo.Video -> info.videoUrl
            is ContentInfo.Animated -> info.videoUrl
            else -> return
        }
        if (videoUrl.isEmpty()) return

        val filename = when (info) {
            is ContentInfo.Video -> "douyin_${info.id}.mp4"
            is ContentInfo.Animated -> "douyin_${info.id}.mp4"
            else -> "douyin_video.mp4"
        }
        enqueueDownload("下载视频") { taskId ->
            downloadVideoUseCase(videoUrl, filename) { downloaded, total ->
                if (total != null) {
                    updateTask(taskId) { it.copy(progress = downloaded.toFloat() / total) }
                }
            }
        }
    }

    fun onDownloadCurrentImage() {
        val state = _uiState.value
        val images = getImages(state.contentInfo)
        if (images.isEmpty()) return
        val url = images[state.galleryIndex]
        val id = getContentId(state.contentInfo)

        enqueueDownload("下载图片") {
            downloadImagesUseCase.downloadSingle(url, "douyin_${id}_${state.galleryIndex + 1}.jpg")
        }
    }

    fun onDownloadSelectedImages() {
        val state = _uiState.value
        val images = getImages(state.contentInfo)
        if (images.isEmpty()) return
        val selected = state.selectedImages.sorted()
        if (selected.isEmpty()) return
        val urls = selected.map { images[it] }
        val filenames = selected.map { "douyin_${getContentId(state.contentInfo)}_${it + 1}.jpg" }
        val total = urls.size

        enqueueDownload("下载图片 $total 张") { taskId ->
            downloadImagesUseCase.downloadMultiple(urls, filenames) { completed, _ ->
                updateTask(taskId) { it.copy(progress = completed.toFloat() / total) }
            }
        }
        _uiState.update { it.copy(selectedImages = emptySet()) }
    }

    fun onSynthesizeVideo() {
        val info = _uiState.value.contentInfo
        val images = getImages(info)
        if (images.isEmpty()) return

        val musicUrl = when (info) {
            is ContentInfo.ImageGallery -> info.musicUrl
            is ContentInfo.Animated -> info.musicUrl
            else -> null
        }
        val duration = when (info) {
            is ContentInfo.ImageGallery -> info.duration
            is ContentInfo.Animated -> info.duration
            else -> 0
        }
        val id = getContentId(info)

        enqueueDownload("合成视频") {
            synthesizeVideoUseCase.fromImages(images, musicUrl, duration, "douyin_${id}.mp4")
        }
    }

    fun onMergeAnimatedVideo() {
        val info = _uiState.value.contentInfo as? ContentInfo.Animated ?: return
        if (info.videoUrl.isEmpty()) return

        enqueueDownload("合并音乐") {
            synthesizeVideoUseCase.mergeWithMusic(info.videoUrl, info.musicUrl, "douyin_${info.id}.mp4")
        }
    }

    fun onHistoryItemClicked(entity: HistoryEntity) {
        val info = when (entity.type) {
            "video" -> ContentInfo.Video(
                id = entity.id,
                title = entity.title,
                author = entity.author,
                cover = entity.cover,
                videoUrl = entity.videoUrl,
            )
            "image" -> ContentInfo.ImageGallery(
                id = entity.id,
                title = entity.title,
                author = entity.author,
                cover = entity.cover,
                images = parseJsonArray(entity.images),
                musicUrl = entity.musicUrl,
                duration = entity.duration,
            )
            "animated" -> ContentInfo.Animated(
                id = entity.id,
                title = entity.title,
                author = entity.author,
                cover = entity.cover,
                images = parseJsonArray(entity.images),
                musicUrl = entity.musicUrl,
                duration = entity.duration,
                videoUrl = entity.videoUrl,
            )
            else -> return
        }
        _uiState.update {
            it.copy(
                contentInfo = info,
                galleryIndex = 0,
                selectedImages = getImages(info).indices.toSet(),
                error = null,
            )
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun onClearDownloadHistory() {
        viewModelScope.launch {
            downloadTaskDao.clearAll()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun addToHistory(info: ContentInfo) {
        viewModelScope.launch {
            val entity = HistoryEntity(
                id = info.id,
                type = when (info) {
                    is ContentInfo.Video -> "video"
                    is ContentInfo.ImageGallery -> "image"
                    is ContentInfo.Animated -> "animated"
                },
                title = info.title,
                author = info.author,
                cover = info.cover,
                videoUrl = when (info) {
                    is ContentInfo.Video -> info.videoUrl
                    is ContentInfo.Animated -> info.videoUrl
                    else -> ""
                },
                images = toJsonArray(getImages(info)),
                musicUrl = when (info) {
                    is ContentInfo.ImageGallery -> info.musicUrl
                    is ContentInfo.Animated -> info.musicUrl
                    else -> ""
                },
                duration = when (info) {
                    is ContentInfo.ImageGallery -> info.duration
                    is ContentInfo.Animated -> info.duration
                    else -> 0
                },
            )
            historyDao.insert(entity)
        }
    }

    private fun getImages(info: ContentInfo?): List<String> = when (info) {
        is ContentInfo.ImageGallery -> info.images
        is ContentInfo.Animated -> info.images
        else -> emptyList()
    }

    private fun getContentId(info: ContentInfo?): String = info?.id ?: "unknown"

    private fun toJsonArray(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
