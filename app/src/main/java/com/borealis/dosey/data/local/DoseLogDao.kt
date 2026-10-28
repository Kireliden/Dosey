package com.borealis.dosey.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs ORDER BY scheduledDate DESC, minutesAfterMidnight DESC")
    fun observeAll(): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs ORDER BY scheduledDate DESC, minutesAfterMidnight DESC")
    suspend fun getAllForSnapshot(): List<DoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DoseLogEntity)

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId AND scheduledDate = :date AND minutesAfterMidnight = :minutes LIMIT 1")
    suspend fun find(medicationId: Long, date: String, minutes: Int): DoseLogEntity?
}
