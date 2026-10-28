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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.borealis.dosey.companion.CompanionService
import com.borealis.dosey.companion.CompanionNetworkServer
import com.borealis.dosey.companion.CompanionCredentialStore
import com.borealis.dosey.companion.CompanionPairingManager
import com.borealis.dosey.data.MedicationRepository
import com.borealis.dosey.data.local.DoseLogEntity
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.DoseyDatabase
import com.borealis.dosey.data.local.MedicationEntity
import com.borealis.dosey.notifications.DoseAlarmReceiver
import com.borealis.dosey.notifications.DoseAlarmScheduler
import com.borealis.dosey.ui.MedicationViewModel
import com.borealis.dosey.ui.theme.DoseyTheme
import android.Manifest
import android.content.Intent
import android.app.Activity
import android.widget.Toast
import org.json.JSONObject
import com.google.zxing.client.android.Intents
import com.google.zxing.integration.android.IntentIntegrator
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val medicationViewModel: MedicationViewModel by viewModels {
        MedicationViewModel.Factory(
            MedicationRepository(
                DoseyDatabase.getInstance(applicationContext).medicationDao(),
                DoseyDatabase.getInstance(applicationContext).doseScheduleDao(),
                DoseyDatabase.getInstance(applicationContext).doseLogDao(),
                DoseyDatabase.getInstance(applicationContext).oneOffDoseDao()
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
    val context = LocalContext.current
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val doseLogs by viewModel.doseLogs.collectAsStateWithLifecycle()
    val oneOffDoses by viewModel.oneOffDoses.collectAsStateWithLifecycle()
    val oneOffSuggestions by viewModel.oneOffSuggestions.collectAsStateWithLifecycle()
    var showMedicationDialog by remember { mutableStateOf(false) }
    var showOneOffDialog by remember { mutableStateOf(false) }
    var medicationBeingEdited by remember { mutableStateOf<MedicationEntity?>(null) }
    var medicationPendingDeletion by remember { mutableStateOf<MedicationEntity?>(null) }
    var medicationForSchedule by remember { mutableStateOf<MedicationEntity?>(null) }
    var medicationForRefill by remember { mutableStateOf<MedicationEntity?>(null) }
    var selectedScreen by remember { mutableStateOf("today") }
    var companionEnabled by remember { mutableStateOf(false) }
    var companionClients by remember { mutableStateOf(emptyList<CompanionCredentialStore.Client>()) }
    val pendingPairingRequest by CompanionPairingManager.pendingRequest.collectAsStateWithLifecycle()
    val companionFingerprint by CompanionNetworkServer.certificateFingerprint.collectAsStateWithLifecycle()
    val qrScanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val rawQr = result.data?.getStringExtra(Intents.Scan.RESULT)
            runCatching {
                val payload = JSONObject(rawQr ?: error("No QR data"))
                val request = pendingPairingRequest ?: error("There is no pending pairing request")
                val localFingerprint = companionFingerprint.orEmpty()
                    .replace(":", "").replace("-", "").filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)
                val qrFingerprint = payload.optString("fingerprint").replace(":", "").replace("-", "")
                    .filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)
                require(payload.optString("type") == "dosey-companion-pairing" &&
                    payload.optInt("protocolVersion") == 1) { "This QR code is not a Dosey pairing code." }
                require(localFingerprint.length == 64 && localFingerprint == qrFingerprint) {
                    "The QR certificate fingerprint does not match this phone. Do not approve this connection."
                }
                require(payload.optString("requestId") == request.id &&
                    payload.optString("verificationCode") == request.verificationCode &&
                    payload.optString("clientName") == request.clientName) {
                    "The QR code does not match the pending PC request. Start pairing again."
                }
                require(payload.optString("host").isNotBlank() &&
                    payload.optInt("port") in 1..65535) { "The QR code has invalid connection details." }
                check(CompanionPairingManager.approve(request.id)) { "The pairing request expired. Try again." }
                Toast.makeText(context, "Dosey Companion pairing approved", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "Could not verify QR code", Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(selectedScreen, companionEnabled) {
        if (selectedScreen == "companion" && companionEnabled) {
            companionClients = runCatching { CompanionCredentialStore.list(context) }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(companionEnabled) {
        while (companionEnabled) {
            CompanionPairingManager.clearExpired()
            delay(1000)
        }
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()
    val todayDate = LocalDate.now().toString()
    val today = LocalDate.now().format(
        DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern), Locale.getDefault())
    ).replaceFirstChar { it.uppercase(Locale.getDefault()) }

    val pendingDoses = schedules.mapNotNull { schedule ->
        val medication = medications.firstOrNull { it.id == schedule.medicationId } ?: return@mapNotNull null
        val existingLog = doseLogs.firstOrNull {
            it.medicationId == medication.id &&
                it.scheduledDate == todayDate &&
                it.minutesAfterMidnight == schedule.minutesAfterMidnight
        }
        if (existingLog == null) Pair(schedule, medication) else null
    }.sortedBy { it.first.minutesAfterMidnight }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                listOf(
                    "today" to R.string.nav_today,
                    "medications" to R.string.nav_medications,
                    "history" to R.string.nav_history,
                    "companion" to R.string.nav_companion
                ).forEach { (screen, label) ->
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text(stringResource(label)) },
                        selected = selectedScreen == screen,
                        onClick = {
                            selectedScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                if (selectedScreen == "medications") {
                    FloatingActionButton(
                        onClick = {
                            medicationBeingEdited = null
                            showMedicationDialog = true
                        },
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("+", fontSize = 28.sp) }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text("☰")
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (selectedScreen == "today") today else stringResource(
                                when (selectedScreen) {
                                    "medications" -> R.string.nav_medications
                                    "history" -> R.string.nav_history
                                    else -> R.string.nav_companion
                                }
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (selectedScreen) {
                    "companion" -> {
                        Text(
                            text = stringResource(R.string.companion_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        if (companionEnabled) R.string.companion_enabled_title
                                        else R.string.companion_disabled_title
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(
                                        if (companionEnabled) R.string.companion_enabled_detail
                                        else R.string.companion_disabled_detail
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (companionEnabled && companionFingerprint != null) {
                                    androidx.compose.material3.HorizontalDivider()
                                    Text(
                                        text = stringResource(R.string.companion_certificate_fingerprint_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = companionFingerprint.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = stringResource(R.string.companion_certificate_fingerprint_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        val action = if (companionEnabled) CompanionService.ACTION_STOP
                                            else CompanionService.ACTION_START
                                        val serviceIntent = Intent(context, CompanionService::class.java).setAction(action)
                                        if (companionEnabled) context.startService(serviceIntent)
                                        else androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                                        companionEnabled = !companionEnabled
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(
                                        if (companionEnabled) R.string.companion_disable
                                        else R.string.companion_enable
                                    ))
                                }
                                if (companionEnabled && pendingPairingRequest != null) {
                                    val request = pendingPairingRequest!!
                                    androidx.compose.material3.HorizontalDivider()
                                    Text(
                                        text = stringResource(R.string.companion_pair_request_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(stringResource(R.string.companion_pair_request_client, request.clientName))
                                    Text(
                                        text = request.verificationCode,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(stringResource(R.string.companion_pair_request_code_help))
                                    Button(
                                        onClick = {
                                            val activity = context as? Activity
                                            if (activity == null) {
                                                Toast.makeText(context, "Unable to open QR scanner", Toast.LENGTH_LONG).show()
                                            } else {
                                                val scanIntent = IntentIntegrator(activity).apply {
                                                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                                                    setPrompt("Scan the QR code shown by Dosey Companion on your PC")
                                                    setBeepEnabled(false)
                                                    setOrientationLocked(false)
                                                }.createScanIntent()
                                                qrScanner.launch(scanIntent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Scan PC pairing QR") }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TextButton(
                                            onClick = { CompanionPairingManager.reject(request.id) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.companion_pair_reject)) }
                                        Button(
                                            onClick = { CompanionPairingManager.approve(request.id) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.companion_pair_approve)) }
                                    }
                                }
                                if (companionEnabled) {
                                    androidx.compose.material3.HorizontalDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Paired computers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = {
                                            companionClients = runCatching {
                                                CompanionCredentialStore.list(context)
                                            }.getOrDefault(emptyList())
                                        }) { Text("Refresh") }
                                    }
                                    if (companionClients.isEmpty()) {
                                        Text(
                                            "No paired computers yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        companionClients.forEach { client ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(client.name, style = MaterialTheme.typography.bodyLarge)
                                                    Text(
                                                        if (client.revokedAt == null) "Paired" else "Access revoked",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (client.revokedAt == null) {
                                                    TextButton(onClick = {
                                                        if (CompanionCredentialStore.revoke(context, client.id)) {
                                                            companionClients = runCatching {
                                                                CompanionCredentialStore.list(context)
                                                            }.getOrDefault(emptyList())
                                                        }
                                                    }) { Text("Revoke") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.companion_description),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    "today" -> {
                        Text(
                            text = stringResource(R.string.today_doses_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (pendingDoses.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.all_done_today),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.all_done_today_detail),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Button(
                                        onClick = { showOneOffDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.add_one_off_dose))
                                    }
                                }
                            }
                        } else {
                            pendingDoses.forEach { (schedule, medication) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = false,
                                            onCheckedChange = {
                                                viewModel.markDoseTaken(
                                                    medication, todayDate, schedule.minutesAfterMidnight
                                                )
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = String.format(
                                                    Locale.ROOT, "%02d:%02d",
                                                    schedule.minutesAfterMidnight / 60,
                                                    schedule.minutesAfterMidnight % 60
                                                ),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(medication.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                medication.doseDescription.ifBlank { stringResource(R.string.dose_not_configured) },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            TextButton(
                                onClick = { showOneOffDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.add_one_off_dose))
                            }
                        }
                    }

                    "medications" -> {
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
                            EmptyMedicationCard(onAddClick = {
                                medicationBeingEdited = null
                                showMedicationDialog = true
                            })
                        } else {
                            medications.forEach { medication ->
                                MedicationCard(
                                    medication = medication,
                                    schedules = schedules.filter { it.medicationId == medication.id },
                                    onManageSchedule = { medicationForSchedule = medication },
                                    onRefill = { medicationForRefill = medication },
                                    onEdit = {
                                        medicationBeingEdited = medication
                                        showMedicationDialog = true
                                    },
                                    onDelete = { medicationPendingDeletion = medication }
                                )
                            }
                        }
                    }

                    "history" -> {
                        Text(
                            text = stringResource(R.string.dose_history),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (doseLogs.isEmpty() && oneOffDoses.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.no_dose_history),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    doseLogs.forEach { log -> DoseHistoryRow(log) }
                                    oneOffDoses.forEach { log -> OneOffDoseHistoryRow(log) }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showOneOffDialog) {
        OneOffDoseDialog(
            suggestions = oneOffSuggestions,
            onDismiss = { showOneOffDialog = false },
            onSave = { name, dose ->
                val now = java.time.LocalTime.now()
                viewModel.recordOneOffDose(name, dose, todayDate, now.hour * 60 + now.minute)
                showOneOffDialog = false
            }
        )
    }

    if (showMedicationDialog) {
        MedicationDialog(
            medication = medicationBeingEdited,
            onDismiss = {
                showMedicationDialog = false
                medicationBeingEdited = null
            },
            onSave = { name, dose, notes, stockQuantity, lowStockThreshold, unitsPerDose ->
                viewModel.saveMedication(
                    medicationBeingEdited, name, dose, notes,
                    stockQuantity, lowStockThreshold, unitsPerDose
                )
                medicationBeingEdited?.let { existing ->
                    val updated = existing.copy(
                        name = name.trim(), doseDescription = dose.trim(), notes = notes.trim(),
                        stockQuantity = stockQuantity, lowStockThreshold = lowStockThreshold,
                        unitsPerDose = unitsPerDose
                    )
                    schedules.filter { it.medicationId == existing.id }.forEach { schedule ->
                        DoseAlarmScheduler(context).schedule(updated, schedule)
                    }
                }
                showMedicationDialog = false
                medicationBeingEdited = null
            }
        )
    }

    medicationForRefill?.let { medication ->
        RefillStockDialog(
            medication = medication,
            onDismiss = { medicationForRefill = null },
            onConfirm = { quantity ->
                viewModel.refillMedication(medication.id, quantity)
                medicationForRefill = null
            }
        )
    }

    medicationForSchedule?.let { medication ->
        DoseScheduleDialog(
            context = context,
            medication = medication,
            schedules = schedules.filter { it.medicationId == medication.id },
            onAdd = { minutes ->
                viewModel.addSchedule(medication.id, minutes)
                DoseAlarmScheduler(context).schedule(
                    medication,
                    DoseScheduleEntity(medicationId = medication.id, minutesAfterMidnight = minutes)
                )
            },
            onDelete = { schedule ->
                DoseAlarmScheduler(context).cancel(medication.id, schedule.minutesAfterMidnight)
                viewModel.deleteSchedule(schedule)
            },
            onDismiss = { medicationForSchedule = null }
        )
    }

    medicationPendingDeletion?.let { medication ->
        AlertDialog(
            onDismissRequest = { medicationPendingDeletion = null },
            title = { Text(stringResource(R.string.delete_medication_title)) },
            text = { Text(stringResource(R.string.delete_medication_message, medication.name)) },
            confirmButton = {
                TextButton(onClick = {
                    schedules.filter { it.medicationId == medication.id }.forEach { schedule ->
                        DoseAlarmScheduler(context).cancel(medication.id, schedule.minutesAfterMidnight)
                    }
                    viewModel.deleteMedication(medication)
                    medicationPendingDeletion = null
                }) { Text(stringResource(R.string.delete)) }
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
private fun OneOffDoseDialog(
    suggestions: List<com.borealis.dosey.data.local.OneOffDoseEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.one_off_dose_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.one_off_recent_suggestions),
                        style = MaterialTheme.typography.titleSmall
                    )
                    suggestions.forEach { suggestion ->
                        TextButton(
                            onClick = {
                                name = suggestion.medicationName
                                dose = suggestion.doseDescription
                                validationError = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(suggestion.medicationName, fontWeight = FontWeight.SemiBold)
                                if (suggestion.doseDescription.isNotBlank()) {
                                    Text(
                                        suggestion.doseDescription,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.one_off_or_new),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = false },
                    label = { Text(stringResource(R.string.one_off_medication_name)) },
                    singleLine = true,
                    isError = validationError
                )
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text(stringResource(R.string.one_off_dose_label)) },
                    placeholder = { Text("e.g. 400 mg, 1 tablet") },
                    singleLine = true
                )
                if (validationError) {
                    Text(
                        text = stringResource(R.string.one_off_name_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) validationError = true
                else onSave(name.trim(), dose.trim())
            }) {
                Text(stringResource(R.string.record_dose))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun OneOffDoseHistoryRow(log: com.borealis.dosey.data.local.OneOffDoseEntity) {
    val time = String.format(Locale.ROOT, "%02d:%02d", log.minutesAfterMidnight / 60, log.minutesAfterMidnight % 60)
    val description = if (log.doseDescription.isBlank()) log.medicationName else "${log.medicationName} · ${log.doseDescription}"
    Text(
        text = stringResource(R.string.one_off_history_entry, log.scheduledDate, time, description),
        style = MaterialTheme.typography.bodyMedium
    )
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
    onRefill: () -> Unit,
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

                if (medication.stockQuantity >= 0) {
                    Text(
                        text = stringResource(R.string.stock_quantity, medication.stockQuantity),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (medication.stockQuantity <= medication.lowStockThreshold)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (medication.stockQuantity <= medication.lowStockThreshold) {
                        Text(
                            text = stringResource(R.string.low_stock_warning),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                    if (medication.stockQuantity >= 0) {
                        TextButton(onClick = onRefill) {
                            Text(stringResource(R.string.refill_stock))
                        }
                    }
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
private fun RefillStockDialog(
    medication: MedicationEntity,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantityText by remember(medication.id) { mutableStateOf("") }
    var invalidQuantity by remember(medication.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refill_stock_title, medication.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.refill_stock_current, medication.stockQuantity))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it.filter { character -> character.isDigit() }
                        invalidQuantity = false
                    },
                    label = { Text(stringResource(R.string.refill_amount_label)) },
                    placeholder = { Text("28") },
                    singleLine = true,
                    isError = invalidQuantity
                )
                if (invalidQuantity) {
                    Text(
                        text = stringResource(R.string.refill_amount_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val quantity = quantityText.toIntOrNull()
                if (quantity == null || quantity <= 0 || medication.stockQuantity + quantity < medication.stockQuantity) {
                    invalidQuantity = true
                } else {
                    onConfirm(quantity)
                }
            }) {
                Text(stringResource(R.string.refill_stock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun DoseHistoryRow(log: DoseLogEntity) {
    val time = String.format(Locale.ROOT, "%02d:%02d", log.minutesAfterMidnight / 60, log.minutesAfterMidnight % 60)
    val statusText = stringResource(if (log.status == DoseAlarmReceiver.STATUS_TAKEN) R.string.status_taken else R.string.status_missed)
    Text(
        text = stringResource(R.string.history_entry, log.scheduledDate, time, "${log.medicationName} · $statusText"),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun DoseScheduleDialog(
    context: android.content.Context,
    medication: MedicationEntity,
    schedules: List<DoseScheduleEntity>,
    onAdd: (Int) -> Unit,
    onDelete: (DoseScheduleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
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
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
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
    onSave: (
        name: String, dose: String, notes: String,
        stockQuantity: Int, lowStockThreshold: Int, unitsPerDose: Int
    ) -> Unit
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
    var stockText by remember(medication?.id) {
        mutableStateOf(medication?.stockQuantity?.takeIf { it >= 0 }?.toString().orEmpty())
    }
    var lowStockText by remember(medication?.id) {
        mutableStateOf((medication?.lowStockThreshold ?: 5).toString())
    }
    var unitsPerDoseText by remember(medication?.id) {
        mutableStateOf((medication?.unitsPerDose ?: 1).toString())
    }
    var inventoryError by remember(medication?.id) { mutableStateOf(false) }

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

                Text(
                    text = stringResource(R.string.inventory_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.inventory_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it.filter { character -> character.isDigit() }; inventoryError = false },
                    label = { Text(stringResource(R.string.stock_quantity_label)) },
                    placeholder = { Text(stringResource(R.string.stock_untracked_hint)) },
                    singleLine = true,
                    isError = inventoryError
                )
                OutlinedTextField(
                    value = lowStockText,
                    onValueChange = { lowStockText = it.filter { character -> character.isDigit() }; inventoryError = false },
                    label = { Text(stringResource(R.string.low_stock_threshold_label)) },
                    singleLine = true,
                    isError = inventoryError
                )
                OutlinedTextField(
                    value = unitsPerDoseText,
                    onValueChange = { unitsPerDoseText = it.filter { character -> character.isDigit() }; inventoryError = false },
                    label = { Text(stringResource(R.string.units_per_dose_label)) },
                    singleLine = true,
                    isError = inventoryError
                )
                if (inventoryError) {
                    Text(
                        text = stringResource(R.string.invalid_inventory_values),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val stock = if (stockText.isBlank()) -1 else stockText.toIntOrNull()
                    val threshold = lowStockText.toIntOrNull()
                    val units = unitsPerDoseText.toIntOrNull()
                    if (stock == null || stock < -1 || threshold == null || threshold < 0 || units == null || units < 1) {
                        inventoryError = true
                    } else {
                        onSave(name.trim(), dose.trim(), notes.trim(), stock, threshold, units)
                    }
                },
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
