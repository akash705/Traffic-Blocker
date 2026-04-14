package com.vedtechnologies.trafficblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocklist_sources")
data class BlocklistSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val name: String,
    val enabled: Boolean = true,
    val domainCount: Int = 0,
    val lastUpdated: Long? = null,
    val lastError: String? = null
)
