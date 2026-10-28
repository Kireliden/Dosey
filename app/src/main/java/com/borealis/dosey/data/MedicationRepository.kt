package com.borealis.dosey.data

import com.borealis.dosey.data.local.MedicationDao
import com.borealis.dosey.data.local.MedicationEntity
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao
) {
    fun observeMedications(): Flow<List<MedicationEntity>> =
        medicationDao.observeAll()

    suspend fun addMedication(name: String, doseDescription: String, notes: String) {
        medicationDao.insert(
            MedicationEntity(
                name = name.trim(),
                doseDescription = doseDescription.trim(),
                notes = notes.trim()
            )
        )
    }

    suspend fun updateMedication(
        medication: MedicationEntity,
        name: String,
        doseDescription: String,
        notes: String
    ) {
        medicationDao.update(
            medication.copy(
                name = name.trim(),
                doseDescription = doseDescription.trim(),
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteMedication(medication: MedicationEntity) {
        medicationDao.delete(medication)
    }
}
