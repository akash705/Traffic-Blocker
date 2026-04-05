package com.akash.apptrafficblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved blocking configuration that can be quickly activated.
 * Stores the target packages, their display names, and per-app blocking modes.
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated package names */
    val packages: String,
    /** Pipe-separated "pkg|name" entries, same format as PrefsManager */
    val appNames: String,
    /** Pipe-separated "pkg|mode" entries, same format as PrefsManager */
    val blockingModes: String,
    val createdAt: Long = System.currentTimeMillis()
)
