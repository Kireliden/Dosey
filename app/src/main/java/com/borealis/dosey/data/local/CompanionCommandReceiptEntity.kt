package com.borealis.dosey.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companion_command_receipts")
data class CompanionCommandReceiptEntity(
    @PrimaryKey val commandId: String,
    val responseJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
