package com.borealis.dosey.companion

import androidx.room.withTransaction
import com.borealis.dosey.data.local.DoseLogEntity
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.DoseyDatabase
import com.borealis.dosey.data.local.MedicationEntity
import com.borealis.dosey.data.local.OneOffDoseEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a consistent, versioned-format snapshot from Room.
 *
 * This is an internal data preparation layer only. It is not connected to any HTTP route;
 * authenticated access control must be complete before medication data can leave the device.
 */
internal object CompanionSnapshotExporter {
    data class Snapshot(
        val generatedAt: Long,
        val revision: Long,
        val medications: List<MedicationEntity>,
        val schedules: List<DoseScheduleEntity>,
        val doseLogs: List<DoseLogEntity>,
        val oneOffDoses: List<OneOffDoseEntity>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("generatedAt", generatedAt)
            .put("revision", revision)
            .put("medications", JSONArray().apply {
                medications.forEach { item ->
                    put(JSONObject()
                        .put("id", item.id)
                        .put("name", item.name)
                        .put("doseDescription", item.doseDescription)
                        .put("notes", item.notes)
                        .put("stockQuantity", item.stockQuantity)
                        .put("lowStockThreshold", item.lowStockThreshold)
                        .put("unitsPerDose", item.unitsPerDose)
                        .put("createdAt", item.createdAt))
                }
            })
            .put("schedules", JSONArray().apply {
                schedules.forEach { item ->
                    put(JSONObject()
                        .put("id", item.id)
                        .put("medicationId", item.medicationId)
                        .put("minutesAfterMidnight", item.minutesAfterMidnight))
                }
            })
            .put("doseLogs", JSONArray().apply {
                doseLogs.forEach { item ->
                    put(JSONObject()
                        .put("id", item.id)
                        .put("medicationId", item.medicationId)
                        .put("medicationName", item.medicationName)
                        .put("scheduledDate", item.scheduledDate)
                        .put("minutesAfterMidnight", item.minutesAfterMidnight)
                        .put("status", item.status)
                        .put("recordedAt", item.recordedAt))
                }
            })
            .put("oneOffDoses", JSONArray().apply {
                oneOffDoses.forEach { item ->
                    put(JSONObject()
                        .put("id", item.id)
                        .put("medicationName", item.medicationName)
                        .put("doseDescription", item.doseDescription)
                        .put("scheduledDate", item.scheduledDate)
                        .put("minutesAfterMidnight", item.minutesAfterMidnight)
                        .put("recordedAt", item.recordedAt))
                }
            })

    }

    suspend fun create(database: DoseyDatabase): Snapshot = database.withTransaction {
        Snapshot(
            generatedAt = System.currentTimeMillis(),
            revision = database.companionSyncMetadataDao().getRevision()
                ?: error("Companion snapshot revision metadata is missing"),
            medications = database.medicationDao().getAllForSnapshot(),
            schedules = database.doseScheduleDao().getAllForSnapshot(),
            doseLogs = database.doseLogDao().getAllForSnapshot(),
            oneOffDoses = database.oneOffDoseDao().getAllForSnapshot()
        )
    }

    private const val PROTOCOL_VERSION = 1
}
