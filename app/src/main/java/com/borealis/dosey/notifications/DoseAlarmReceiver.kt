package com.borealis.dosey.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.borealis.dosey.MainActivity
import com.borealis.dosey.R
import com.borealis.dosey.data.local.DoseyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1L)
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME).orEmpty()
        val minutes = intent.getIntExtra(EXTRA_MINUTES, -1)
        val action = intent.action ?: ACTION_REMIND
        if (medicationId <= 0L || medicationName.isBlank() || minutes !in 0..1439) return

        if (action == ACTION_TAKEN || action == ACTION_MISSED) {
            val status = if (action == ACTION_TAKEN) STATUS_TAKEN else STATUS_MISSED
            CoroutineScope(Dispatchers.IO).launch {
                val db = DoseyDatabase.getInstance(context)
                db.doseLogDao().upsert(
                    com.borealis.dosey.data.local.DoseLogEntity(
                        medicationId = medicationId,
                        medicationName = medicationName,
                        scheduledDate = LocalDate.now().toString(),
                        minutesAfterMidnight = minutes,
                        status = status
                    )
                )
            }
            NotificationManagerCompat.from(context).cancel(notificationId(medicationId, minutes))
            return
        }

        if (action == ACTION_SNOOZE) {
            DoseAlarmScheduler(context).scheduleSnooze(
                medicationId = medicationId,
                medicationName = medicationName,
                minutes = minutes,
                snoozeMinutes = SNOOZE_MINUTES
            )
            NotificationManagerCompat.from(context).cancel(notificationId(medicationId, minutes))
            return
        }

        val isSnoozedReminder = intent.getBooleanExtra(EXTRA_IS_SNOOZED_REMINDER, false)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, notificationId(medicationId, minutes),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.dose_reminder_title))
            .setContentText(context.getString(R.string.dose_reminder_message, medicationName, formatTime(minutes)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.mark_taken), actionIntent(context, ACTION_TAKEN, medicationId, medicationName, minutes))
            .addAction(0, context.getString(R.string.mark_missed), actionIntent(context, ACTION_MISSED, medicationId, medicationName, minutes))
            .addAction(0, context.getString(R.string.snooze_reminder), actionIntent(context, ACTION_SNOOZE, medicationId, medicationName, minutes))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(medicationId, minutes), notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission may not have been granted.
        }

        // Only the regular daily alarm should schedule tomorrow's occurrence.
        // A snoozed reminder is a one-off and must not alter the daily schedule.
        if (!isSnoozedReminder) {
            DoseAlarmScheduler(context).scheduleNextOccurrence(
                medicationId = medicationId,
                medicationName = medicationName,
                minutes = minutes,
                includeToday = false
            )
        }
    }

    private fun actionIntent(context: Context, action: String, id: Long, name: String, minutes: Int): PendingIntent {
        val requestCode = when (action) {
            ACTION_TAKEN -> notificationId(id, minutes) + 1
            ACTION_MISSED -> notificationId(id, minutes) + 2
            ACTION_SNOOZE -> notificationId(id, minutes) + 3
            else -> notificationId(id, minutes)
        }
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MEDICATION_ID, id)
            putExtra(EXTRA_MEDICATION_NAME, name)
            putExtra(EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_REMIND = "com.borealis.dosey.ACTION_REMIND"
        const val ACTION_TAKEN = "com.borealis.dosey.ACTION_TAKEN"
        const val ACTION_MISSED = "com.borealis.dosey.ACTION_MISSED"
        const val ACTION_SNOOZE = "com.borealis.dosey.ACTION_SNOOZE"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_IS_SNOOZED_REMINDER = "is_snoozed_reminder"
        const val CHANNEL_ID = "dose_reminders"
        const val STATUS_TAKEN = "TAKEN"
        const val STATUS_MISSED = "MISSED"
        const val SNOOZE_MINUTES = 10

        fun notificationId(id: Long, minutes: Int) = ((id * 31 + minutes) % Int.MAX_VALUE).toInt()
        fun formatTime(minutes: Int) = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    }
}
