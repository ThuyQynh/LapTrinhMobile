package com.team.smartnutrition.habit.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.team.smartnutrition.MainActivity
import com.team.smartnutrition.R
import com.team.smartnutrition.habit.data.ReminderPrefs
import com.team.smartnutrition.habit.util.AlarmScheduler

class CustomReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminder_id") ?: return
        val reminderName = intent.getStringExtra("reminder_name") ?: context.getString(R.string.reminder_default_title)

        Log.d("CustomReminderReceiver", "Triggered: $reminderName (ID: $reminderId)")

        // 1. Tạo Notification Channel
        createNotificationChannel(context)

        // 2. PendingIntent mở MainActivity khi click
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, reminderId.hashCode(), openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.custom_reminder_title_pattern, reminderName))
            .setContentText(context.getString(R.string.custom_reminder_body_template, reminderName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        // 4. Hiển thị Notification
        if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
        }

        // 5. Tự động đặt lại lịch (reschedule) cho ngày mai
        val prefs = ReminderPrefs(context)
        val reminder = prefs.customReminders.find { it.id == reminderId }
        if (reminder != null && reminder.enabled) {
            AlarmScheduler.scheduleNextCustomReminderAlarm(context, reminder)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.custom_reminders_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.custom_reminders_channel_desc)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "custom_reminders_channel"
    }
}
