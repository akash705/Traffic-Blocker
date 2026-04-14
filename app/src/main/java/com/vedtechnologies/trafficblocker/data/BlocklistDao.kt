package com.vedtechnologies.trafficblocker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {

    @Query("SELECT * FROM blocklist_sources ORDER BY name ASC")
    fun getAll(): Flow<List<BlocklistSource>>

    @Query("SELECT * FROM blocklist_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<BlocklistSource>

    @Query("SELECT * FROM blocklist_sources WHERE id = :id")
    suspend fun getById(id: Int): BlocklistSource?

    @Insert
    suspend fun insert(source: BlocklistSource): Long

    @Update
    suspend fun update(source: BlocklistSource)

    @Delete
    suspend fun delete(source: BlocklistSource)

    @Query("DELETE FROM blocklist_sources WHERE id = :id")
    suspend fun deleteById(id: Int)
}
