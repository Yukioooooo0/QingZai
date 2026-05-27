package com.douyin.downloader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.douyin.downloader.data.model.ContentInfo

@Composable
fun HomeScreen(
    sharedUrl: String? = null,
    viewModel: HomeViewModel,
    onNavigateToDownloads: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrEmpty()) {
            viewModel.onPaste(sharedUrl)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val images = getImagesFromContent(state.contentInfo)
            if (images.isNotEmpty() && state.selectedImages.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.onDownloadSelectedImages()
                        onNavigateToDownloads()
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("下载所选 (${state.selectedImages.size})") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                // Title
                Text(
                    text = "媒体解析器",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.inputUrl,
                        onValueChange = viewModel::onUrlChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("粘贴视频或图片链接...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Button(
                        onClick = viewModel::onParse,
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("解析")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Content area: empty / loading / result
                when {
                    state.isLoading -> {
                        LoadingState()
                    }
                    state.contentInfo != null -> {
                        ResultContent(
                            info = state.contentInfo!!,
                            selectedImages = state.selectedImages,
                            onImageToggled = viewModel::onImageToggled,
                            onSelectAll = viewModel::onSelectAll,
                            onDownloadVideo = {
                                viewModel.onDownloadVideo()
                                onNavigateToDownloads()
                            },
                            onDownloadCurrentImage = {
                                viewModel.onDownloadCurrentImage()
                                onNavigateToDownloads()
                            },
                            onSynthesizeVideo = {
                                viewModel.onSynthesizeVideo()
                                onNavigateToDownloads()
                            },
                            onMergeAnimatedVideo = {
                                viewModel.onMergeAnimatedVideo()
                                onNavigateToDownloads()
                            },
                        )
                    }
                    else -> {
                        EmptyState()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "输入链接以获取媒体内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在解析链接，请稍候...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultContent(
    info: ContentInfo,
    selectedImages: Set<Int>,
    onImageToggled: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadCurrentImage: () -> Unit,
    onSynthesizeVideo: () -> Unit,
    onMergeAnimatedVideo: () -> Unit,
) {
    when (info) {
        is ContentInfo.Video -> {
            VideoResult(info, onDownloadVideo)
        }
        is ContentInfo.ImageGallery -> {
            ImageGridResult(
                info = info,
                images = info.images,
                selectedImages = selectedImages,
                onImageToggled = onImageToggled,
                onSelectAll = onSelectAll,
                onSynthesizeVideo = onSynthesizeVideo,
            )
        }
        is ContentInfo.Animated -> {
            if (info.videoUrl.isNotEmpty()) {
                AnimatedVideoResult(info, onMergeAnimatedVideo)
            } else {
                ImageGridResult(
                    info = info,
                    images = info.images,
                    selectedImages = selectedImages,
                    onImageToggled = onImageToggled,
                    onSelectAll = onSelectAll,
                    onSynthesizeVideo = onSynthesizeVideo,
                )
            }
        }
    }
}

@Composable
private fun VideoResult(info: ContentInfo.Video, onDownload: () -> Unit) {
    Column {
        CoverImage(cover = info.cover, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        if (info.author.isNotEmpty()) {
            Text(
                text = "@${info.author}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = info.title.ifEmpty { "无标题" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("下载视频")
        }
    }
}

@Composable
private fun AnimatedVideoResult(info: ContentInfo.Animated, onDownload: () -> Unit) {
    Column {
        CoverImage(cover = info.cover, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        if (info.author.isNotEmpty()) {
            Text(
                text = "@${info.author}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = info.title.ifEmpty { "无标题" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("下载视频（含音乐）")
        }
    }
}

@Composable
private fun CoverImage(cover: String, modifier: Modifier = Modifier) {
    if (cover.isNotEmpty()) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImageGridResult(
    info: ContentInfo,
    images: List<String>,
    selectedImages: Set<Int>,
    onImageToggled: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSynthesizeVideo: () -> Unit,
) {
    Column {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "解析结果",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (selectedImages.size == images.size) "取消全选" else "全选",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSelectAll() },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Author + title
        if (info.author.isNotEmpty()) {
            Text(
                text = "@${info.author}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (info.title.isNotEmpty()) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Grid with capsule synthesize button overlay
        Box(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                itemsIndexed(images) { index, imageUrl ->
                    MediaGridCard(
                        imageUrl = imageUrl,
                        isSelected = index in selectedImages,
                        onClick = { onImageToggled(index) },
                        isVideo = false,
                    )
                }
            }

            // Capsule button at bottom-left
            if (info is ContentInfo.ImageGallery || info is ContentInfo.Animated) {
                Button(
                    onClick = onSynthesizeVideo,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text("合成视频", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MediaGridCard(
    imageUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isVideo: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Video play overlay
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            // Checkbox
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.3f),
                    )
                    .then(
                        if (!isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun getImagesFromContent(info: ContentInfo?): List<String> = when (info) {
    is ContentInfo.ImageGallery -> info.images
    is ContentInfo.Animated -> info.images
    else -> emptyList()
}
