package com.borealis.dosey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.borealis.dosey.data.MedicationRepository
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.DoseyDatabase
import com.borealis.dosey.data.local.MedicationEntity
import com.borealis.dosey.ui.MedicationViewModel
import com.borealis.dosey.ui.theme.DoseyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val medicationViewModel: MedicationViewModel by viewModels {
        MedicationViewModel.Factory(
            MedicationRepository(
                DoseyDatabase.getInstance(applicationContext).medicationDao(),
                DoseyDatabase.getInstance(applicationContext).doseScheduleDao()
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DoseyTheme {
                DoseyHome(viewModel = medicationViewModel)
            }
        }
    }
}

@Composable
private fun DoseyHome(viewModel: MedicationViewModel) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    var showMedicationDialog by remember { mutableStateOf(false) }
    var medicationBeingEdited by remember { mutableStateOf<MedicationEntity?>(null) }
    var medicationPendingDeletion by remember { mutableStateOf<MedicationEntity?>(null) }
    var medicationForSchedule by remember { mutableStateOf<MedicationEntity?>(null) }

    val today = LocalDate.now().format(
        DateTimeFormatter.ofPattern(
            stringResource(R.string.date_pattern),
            Locale.getDefault()
        )
    ).replaceFirstChar { it.uppercase(Locale.getDefault()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    medicationBeingEdited = null
                    showMedicationDialog = true
                },
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("+", fontSize = 28.sp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = today,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.day_at_a_glance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = when (medications.size) {
                            0 -> stringResource(R.string.no_medications_yet)
                            1 -> stringResource(R.string.one_medication_registered)
                            else -> stringResource(
                                R.string.medications_registered,
                                medications.size
                            )
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = stringResource(R.string.doses_will_appear),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.my_medications),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = medications.size.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (medications.isEmpty()) {
                    EmptyMedicationCard(
                        onAddClick = {
                            medicationBeingEdited = null
                            showMedicationDialog = true
                        }
                    )
                } else {
                    medications.forEach { medication ->
                        MedicationCard(
                            medication = medication,
                            schedules = schedules.filter { it.medicationId == medication.id },
                            onManageSchedule = { medicationForSchedule = medication },
                            onEdit = {
                                medicationBeingEdited = medication
                                showMedicationDialog = true
                            },
                            onDelete = { medicationPendingDeletion = medication }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.upcoming_doses),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                val scheduledDoses = schedules.mapNotNull { schedule ->
                    medications.firstOrNull { it.id == schedule.medicationId }?.let { medication ->
                        Pair(schedule.minutesAfterMidnight, medication)
                    }
                }.sortedBy { it.first }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (scheduledDoses.isEmpty()) {
                            Text(
                                text = if (medications.isEmpty()) {
                                    stringResource(R.string.upcoming_doses_empty)
                                } else {
                                    stringResource(R.string.no_schedules_yet)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.today_schedule),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            scheduledDoses.forEach { (minutes, medication) ->
                                val time = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
                                Text(
                                    text = stringResource(R.string.dose_at_time, time, medication.name),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp))
        }
    }

    if (showMedicationDialog) {
        MedicationDialog(
            medication = medicationBeingEdited,
            onDismiss = {
                showMedicationDialog = false
                medicationBeingEdited = null
            },
            onSave = { name, dose, notes ->
                viewModel.saveMedication(medicationBeingEdited, name, dose, notes)
                showMedicationDialog = false
                medicationBeingEdited = null
            }
        )
    }

    medicationForSchedule?.let { medication ->
        DoseScheduleDialog(
            medication = medication,
            schedules = schedules.filter { it.medicationId == medication.id },
            onAdd = { minutes -> viewModel.addSchedule(medication.id, minutes) },
            onDelete = viewModel::deleteSchedule,
            onDismiss = { medicationForSchedule = null }
        )
    }

    medicationPendingDeletion?.let { medication ->
        AlertDialog(
            onDismissRequest = { medicationPendingDeletion = null },
            title = { Text(stringResource(R.string.delete_medication_title)) },
            text = {
                Text(stringResource(R.string.delete_medication_message, medication.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(medication)
                        medicationPendingDeletion = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationPendingDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyMedicationCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 32.sp)
            }

            Text(
                text = stringResource(R.string.lets_get_started),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.add_first_medication),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onAddClick) {
                Text(stringResource(R.string.add_medication))
            }
        }
    }
}

@Composable
private fun MedicationCard(
    medication: MedicationEntity,
    schedules: List<DoseScheduleEntity>,
    onManageSchedule: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = medication.doseDescription.ifBlank {
                        stringResource(R.string.dose_not_configured)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (medication.notes.isNotBlank()) {
                    Text(
                        text = medication.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (schedules.isNotEmpty()) {
                    Text(
                        text = schedules.sortedBy { it.minutesAfterMidnight }.joinToString(" · ") {
                            String.format(Locale.ROOT, "%02d:%02d", it.minutesAfterMidnight / 60, it.minutesAfterMidnight % 60)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onManageSchedule) {
                        Text(stringResource(R.string.manage_schedule))
                    }
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseScheduleDialog(
    medication: MedicationEntity,
    schedules: List<DoseScheduleEntity>,
    onAdd: (Int) -> Unit,
    onDelete: (DoseScheduleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var timeText by remember(medication.id) { mutableStateOf("") }
    var showInvalidTime by remember(medication.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title, medication.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.schedule_instructions), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {
                        timeText = it
                        showInvalidTime = false
                    },
                    label = { Text(stringResource(R.string.time_24h)) },
                    placeholder = { Text("08:30") },
                    singleLine = true,
                    isError = showInvalidTime
                )
                if (showInvalidTime) {
                    Text(
                        text = stringResource(R.string.invalid_time),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = {
                        val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(timeText.trim())
                        val hour = match?.groupValues?.get(1)?.toIntOrNull()
                        val minute = match?.groupValues?.get(2)?.toIntOrNull()
                        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                            showInvalidTime = true
                        } else {
                            onAdd(hour * 60 + minute)
                            timeText = ""
                            showInvalidTime = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_time))
                }
                if (schedules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_times_for_medication),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    schedules.sortedBy { it.minutesAfterMidnight }.forEach { schedule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                String.format(Locale.ROOT, "%02d:%02d", schedule.minutesAfterMidnight / 60, schedule.minutesAfterMidnight % 60),
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { onDelete(schedule) }) {
                                Text(stringResource(R.string.remove_time))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.save)) }
        }
    )
}

@Composable
private fun MedicationDialog(
    medication: MedicationEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, dose: String, notes: String) -> Unit
) {
    var name by remember(medication?.id) {
        mutableStateOf(medication?.name.orEmpty())
    }
    var dose by remember(medication?.id) {
        mutableStateOf(medication?.doseDescription.orEmpty())
    }
    var notes by remember(medication?.id) {
        mutableStateOf(medication?.notes.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (medication == null) R.string.new_medication
                    else R.string.edit_medication_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.medication_name_question))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.medication_name)) },
                    placeholder = { Text(stringResource(R.string.medication_name_example)) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text(stringResource(R.string.dose_description)) },
                    placeholder = { Text(stringResource(R.string.dose_example)) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_optional)) },
                    placeholder = { Text(stringResource(R.string.notes_example)) },
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), dose.trim(), notes.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(
                    stringResource(
                        if (medication == null) R.string.add else R.string.save
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun DoseyHomePreview() {
    DoseyTheme {
        Surface {
            // The preview stays independent of the database.
            EmptyMedicationCard(onAddClick = {})
        }
    }
}
