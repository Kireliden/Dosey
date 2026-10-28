package com.borealis.dosey.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Internal monotonic revision for the data snapshot shared with Companion.
 * SQLite triggers update this row for every mutation to a snapshot table.
 */
@Entity(tableName = "companion_sync_metadata")
data class CompanionSyncMetadataEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    @ColumnInfo(defaultValue = "1")
    val revision: Long = INITIAL_REVISION
) {
    companion object {
        const val SINGLETON_ID = 1
        const val INITIAL_REVISION = 1L
    }
}
