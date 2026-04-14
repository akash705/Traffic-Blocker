package com.vedtechnologies.trafficblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_query_logs")
data class DnsQueryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val packageName: String?,
    val blocked: Boolean,
    val queryType: String, // "A", "AAAA", etc.
    val timestamp: Long = System.currentTimeMillis()
)
