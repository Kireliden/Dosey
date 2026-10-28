package com.borealis.dosey.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.borealis.dosey.data.MedicationRepository
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.MedicationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(
    private val repository: MedicationRepository
) : ViewModel() {
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
        notes: String
    ) {
        if (name.isBlank()) return

        viewModelScope.launch {
            if (existing == null) {
                repository.addMedication(name, doseDescription, notes)
            } else {
                repository.updateMedication(existing, name, doseDescription, notes)
            }
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
