package com.akash.apptrafficblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {

    @Insert
    suspend fun insert(event: BlockEvent)

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<BlockEvent>>

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEvent(): BlockEvent?

    @Query("DELETE FROM block_events")
    suspend fun clearAll()
}
