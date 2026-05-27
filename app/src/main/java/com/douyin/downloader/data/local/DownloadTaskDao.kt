package com.douyin.downloader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY timestamp DESC LIMIT 50")
    fun getRecent(): Flow<List<DownloadTaskEntity>>

    @Insert
    suspend fun insert(entity: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks")
    suspend fun clearAll()
}
