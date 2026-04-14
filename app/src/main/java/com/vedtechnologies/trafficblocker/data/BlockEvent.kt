package com.vedtechnologies.trafficblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val eventType: String, // "BLOCKED" or "UNBLOCKED"
    val timestamp: Long = System.currentTimeMillis()
)
