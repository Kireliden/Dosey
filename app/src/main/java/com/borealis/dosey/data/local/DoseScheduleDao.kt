package com.borealis.dosey.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseScheduleDao {
    @Query("SELECT * FROM dose_schedules ORDER BY minutesAfterMidnight ASC")
    fun observeAll(): Flow<List<DoseScheduleEntity>>

    @Query("SELECT * FROM dose_schedules ORDER BY medicationId ASC, minutesAfterMidnight ASC")
    suspend fun getAllForSnapshot(): List<DoseScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(schedule: DoseScheduleEntity): Long

    @Delete
    suspend fun delete(schedule: DoseScheduleEntity)
}
