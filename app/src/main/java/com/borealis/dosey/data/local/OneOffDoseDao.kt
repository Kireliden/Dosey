package com.borealis.dosey.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OneOffDoseDao {
    @Query("SELECT * FROM one_off_dose_logs ORDER BY scheduledDate DESC, minutesAfterMidnight DESC")
    fun observeAll(): Flow<List<OneOffDoseEntity>>

    @Query("SELECT * FROM one_off_dose_logs GROUP BY medicationName COLLATE NOCASE ORDER BY MAX(recordedAt) DESC")
    fun observeSuggestions(): Flow<List<OneOffDoseEntity>>

    @Insert
    suspend fun insert(dose: OneOffDoseEntity)
}
