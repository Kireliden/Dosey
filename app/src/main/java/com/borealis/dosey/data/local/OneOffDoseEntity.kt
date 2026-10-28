package com.borealis.dosey.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "one_off_dose_logs")
data class OneOffDoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationName: String,
    val doseDescription: String,
    val scheduledDate: String,
    val minutesAfterMidnight: Int,
    val recordedAt: Long = System.currentTimeMillis()
)
