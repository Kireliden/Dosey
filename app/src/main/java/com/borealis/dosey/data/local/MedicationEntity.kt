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
    val createdAt: Long = System.currentTimeMillis()
)
