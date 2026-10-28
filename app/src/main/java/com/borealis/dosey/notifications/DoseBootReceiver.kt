package com.borealis.dosey.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.borealis.dosey.data.local.DoseyDatabase

class DoseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = DoseyDatabase.getInstance(context)
                val medications = database.medicationDao().observeAll().first()
                val schedules = database.doseScheduleDao().observeAll().first()
                val medicationById = medications.associateBy { it.id }
                val scheduler = DoseAlarmScheduler(context)
                schedules.forEach { schedule ->
                    medicationById[schedule.medicationId]?.let { medication ->
                        scheduler.schedule(medication, schedule)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
