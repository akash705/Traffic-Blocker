package com.akash.apptrafficblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsQueryLogDao {

    @Insert
    suspend fun insert(log: DnsQueryLog)

    @Query("SELECT * FROM dns_query_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 200): Flow<List<DnsQueryLog>>

    @Query("SELECT * FROM dns_query_logs WHERE blocked = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getBlockedOnly(limit: Int = 200): Flow<List<DnsQueryLog>>

    @Query("SELECT * FROM dns_query_logs WHERE blocked = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getAllowedOnly(limit: Int = 200): Flow<List<DnsQueryLog>>

    @Query("SELECT * FROM dns_query_logs WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT :limit")
    fun getByPackage(pkg: String, limit: Int = 200): Flow<List<DnsQueryLog>>

    @Query("SELECT COUNT(*) FROM dns_query_logs WHERE blocked = 1")
    fun getBlockedCount(): Flow<Int>

    @Query("DELETE FROM dns_query_logs")
    suspend fun clearAll()

    @Query("DELETE FROM dns_query_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
