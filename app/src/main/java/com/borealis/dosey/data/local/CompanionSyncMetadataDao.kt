package com.borealis.dosey.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CompanionSyncMetadataDao {
    @Query("SELECT revision FROM companion_sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun getRevision(): Long?
}
