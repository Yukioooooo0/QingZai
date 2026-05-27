package com.douyin.downloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: String, // "DONE" or "ERROR"
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
