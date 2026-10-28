package com.borealis.dosey.data.local

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
    val stockQuantity: Int = -1,
    val lowStockThreshold: Int = 5,
    val unitsPerDose: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
