package com.borealis.dosey.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.borealis.dosey.data.MedicationRepository
import com.borealis.dosey.data.local.DoseLogEntity
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.MedicationEntity
import com.borealis.dosey.data.local.OneOffDoseEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(
    private val repository: MedicationRepository
) : ViewModel() {
    val oneOffDoses = repository.observeOneOffDoses().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val oneOffSuggestions = repository.observeOneOffSuggestions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val doseLogs = repository.observeDoseLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val schedules = repository.observeSchedules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val medications = repository.observeMedications().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun saveMedication(
        existing: MedicationEntity?,
        name: String,
        doseDescription: String,
        notes: String,
        stockQuantity: Int,
        lowStockThreshold: Int,
        unitsPerDose: Int
    ) {
        if (name.isBlank()) return
        if (stockQuantity < -1 || lowStockThreshold < 0 || unitsPerDose < 1) return

        viewModelScope.launch {
            if (existing == null) {
                repository.addMedication(name, doseDescription, notes, stockQuantity, lowStockThreshold, unitsPerDose)
            } else {
                repository.updateMedication(existing, name, doseDescription, notes, stockQuantity, lowStockThreshold, unitsPerDose)
            }
        }
    }

    fun refillMedication(medicationId: Long, units: Int) {
        if (units <= 0) return
        viewModelScope.launch {
            repository.refillMedication(medicationId, units)
        }
    }

    fun markDoseTaken(medication: MedicationEntity, date: String, minutes: Int) {
        viewModelScope.launch {
            repository.markDoseTaken(medication, date, minutes)
        }
    }

    fun recordOneOffDose(name: String, dose: String, date: String, minutes: Int) {
        if (name.isBlank() || minutes !in 0..1439) return
        viewModelScope.launch {
            repository.recordOneOffDose(name, dose, date, minutes)
        }
    }

    fun addSchedule(medicationId: Long, minutesAfterMidnight: Int) {
        if (minutesAfterMidnight !in 0..1439) return
        viewModelScope.launch {
            repository.addSchedule(medicationId, minutesAfterMidnight)
        }
    }

    fun deleteSchedule(schedule: DoseScheduleEntity) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule)
        }
    }

    fun deleteMedication(medication: MedicationEntity) {
        viewModelScope.launch {
            repository.deleteMedication(medication)
        }
    }

    class Factory(
        private val repository: MedicationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
                return MedicationViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
