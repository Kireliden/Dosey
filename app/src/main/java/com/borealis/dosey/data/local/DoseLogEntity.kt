package com.borealis.dosey.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId", "scheduledDate", "minutesAfterMidnight"], unique = true)]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val medicationName: String,
    val scheduledDate: String,
    val minutesAfterMidnight: Int,
    val status: String,
    val recordedAt: Long = System.currentTimeMillis()
)
