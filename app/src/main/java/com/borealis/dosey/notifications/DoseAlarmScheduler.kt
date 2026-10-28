package com.borealis.dosey.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.borealis.dosey.data.local.DoseScheduleEntity
import com.borealis.dosey.data.local.MedicationEntity
import java.time.LocalDateTime
import java.time.ZoneId

class DoseAlarmScheduler(private val context: Context) {
    fun schedule(medication: MedicationEntity, schedule: DoseScheduleEntity) {
        scheduleNextOccurrence(
            medication.id,
            medication.name,
            schedule.minutesAfterMidnight,
            includeToday = true
        )
    }

    /**
     * Uses a one-shot alarm instead of setInexactRepeating. Android can defer
     * repeating inexact alarms substantially, which makes minute-level testing
     * and medication reminders unreliable. The receiver schedules the next day
     * after delivering each reminder.
     */
    fun scheduleNextOccurrence(
        medicationId: Long,
        medicationName: String,
        minutes: Int,
        includeToday: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DoseAlarmReceiver.notificationId(medicationId, minutes),
            reminderIntent(medicationId, medicationName, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDateTime.now()
        var next = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong())
        if (!includeToday || !next.isAfter(now)) next = next.plusDays(1)

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingIntent
        )
    }

    fun cancel(medicationId: Long, minutes: Int) {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            action = DoseAlarmReceiver.ACTION_REMIND
            putExtra(DoseAlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
            putExtra(DoseAlarmReceiver.EXTRA_MINUTES, minutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DoseAlarmReceiver.notificationId(medicationId, minutes),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun reminderIntent(medicationId: Long, medicationName: String, minutes: Int) =
        Intent(context, DoseAlarmReceiver::class.java).apply {
            action = DoseAlarmReceiver.ACTION_REMIND
            putExtra(DoseAlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
            putExtra(DoseAlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(DoseAlarmReceiver.EXTRA_MINUTES, minutes)
        }
}
