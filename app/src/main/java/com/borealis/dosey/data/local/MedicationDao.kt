package com.borealis.dosey.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :medicationId LIMIT 1")
    suspend fun findById(medicationId: Long): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("UPDATE medications SET stockQuantity = MAX(0, stockQuantity - :units) WHERE id = :medicationId AND stockQuantity >= 0")
    suspend fun deductStock(medicationId: Long, units: Int)

    @Query("UPDATE medications SET stockQuantity = stockQuantity + :units WHERE id = :medicationId AND stockQuantity >= 0")
    suspend fun addStock(medicationId: Long, units: Int)

    @Delete
    suspend fun delete(medication: MedicationEntity)
}
