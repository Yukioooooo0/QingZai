package com.douyin.downloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val author: String,
    val cover: String,
    val videoUrl: String,
    val images: String,
    val musicUrl: String,
    val duration: Int,
    val timestamp: Long = System.currentTimeMillis(),
)
