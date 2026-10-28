package com.borealis.dosey.data

import com.borealis.dosey.data.local.DoseLogDao
import com.borealis.dosey.data.local.DoseLogEntity
import com.borealis.dosey.data.local.DoseScheduleDao
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.MedicationDao
import com.borealis.dosey.data.local.MedicationEntity
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val doseScheduleDao: DoseScheduleDao,
    private val doseLogDao: DoseLogDao
) {
    fun observeMedications(): Flow<List<MedicationEntity>> =
        medicationDao.observeAll()

    suspend fun addMedication(
        name: String,
        doseDescription: String,
        notes: String,
        stockQuantity: Int,
        lowStockThreshold: Int,
        unitsPerDose: Int
    ) {
        medicationDao.insert(
            MedicationEntity(
                name = name.trim(),
                doseDescription = doseDescription.trim(),
                notes = notes.trim(),
                stockQuantity = stockQuantity,
                lowStockThreshold = lowStockThreshold,
                unitsPerDose = unitsPerDose
            )
        )
    }

    suspend fun updateMedication(
        medication: MedicationEntity,
        name: String,
        doseDescription: String,
        notes: String,
        stockQuantity: Int,
        lowStockThreshold: Int,
        unitsPerDose: Int
    ) {
        medicationDao.update(
            medication.copy(
                name = name.trim(),
                doseDescription = doseDescription.trim(),
                notes = notes.trim(),
                stockQuantity = stockQuantity,
                lowStockThreshold = lowStockThreshold,
                unitsPerDose = unitsPerDose
            )
        )
    }

    suspend fun refillMedication(medicationId: Long, units: Int) {
        if (units <= 0) return
        medicationDao.addStock(medicationId, units)
    }

    fun observeDoseLogs(): Flow<List<DoseLogEntity>> = doseLogDao.observeAll()

    fun observeSchedules(): Flow<List<DoseScheduleEntity>> =
        doseScheduleDao.observeAll()

    suspend fun addSchedule(medicationId: Long, minutesAfterMidnight: Int) {
        doseScheduleDao.insert(
            DoseScheduleEntity(
                medicationId = medicationId,
                minutesAfterMidnight = minutesAfterMidnight
            )
        )
    }

    suspend fun deleteSchedule(schedule: DoseScheduleEntity) {
        doseScheduleDao.delete(schedule)
    }

    suspend fun deleteMedication(medication: MedicationEntity) {
        medicationDao.delete(medication)
    }
}
