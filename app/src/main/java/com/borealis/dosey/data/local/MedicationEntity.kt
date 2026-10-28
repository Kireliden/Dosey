package com.borealis.dosey.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val doseDescription: String = "",
    val notes: String = "",
    // -1 means stock tracking is disabled for this medication.
    @ColumnInfo(defaultValue = "-1")
    val stockQuantity: Int = -1,
    @ColumnInfo(defaultValue = "5")
    val lowStockThreshold: Int = 5,
    @ColumnInfo(defaultValue = "1")
    val unitsPerDose: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
