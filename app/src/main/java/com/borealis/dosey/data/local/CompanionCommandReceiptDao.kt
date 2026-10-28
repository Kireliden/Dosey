package com.borealis.dosey.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CompanionCommandReceiptDao {
    @Query("SELECT * FROM companion_command_receipts WHERE commandId = :commandId LIMIT 1")
    suspend fun find(commandId: String): CompanionCommandReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: CompanionCommandReceiptEntity)
}
