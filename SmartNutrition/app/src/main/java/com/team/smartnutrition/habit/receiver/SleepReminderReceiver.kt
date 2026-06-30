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

class SleepReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("SleepReminderReceiver", "Triggered action: $action")

        // 1. Tạo Notification Channel
        createNotificationChannel(context)

        // 2. PendingIntent mở MainActivity khi click
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, action.hashCode(), openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build Notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationId = when (action) {
            ACTION_BEDTIME -> {
                notificationBuilder
                    .setContentTitle("🌙 Đến giờ đi ngủ rồi!")
                    .setContentText("Hãy chuẩn bị nghỉ ngơi để bảo vệ sức khỏe và phục hồi năng lượng nhé.")
                NOTIFICATION_ID_BEDTIME
            }
            ACTION_WAKEUP -> {
                notificationBuilder
                    .setContentTitle("⏰ Chào ngày mới!")
                    .setContentText("Đã đến giờ thức dậy rồi, chúc bạn một ngày mới đầy năng lượng và năng suất!")
                NOTIFICATION_ID_WAKEUP
            }
            else -> return
        }

        // 4. Hiển thị Notification
        if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
        }

        // 5. Tự động đặt lại lịch cho ngày mai
        val prefs = ReminderPrefs(context)
        if (prefs.sleepReminderEnabled) {
            when (action) {
                ACTION_BEDTIME -> {
                    AlarmScheduler.scheduleNextBedtimeAlarm(context, prefs.bedtimeHour, prefs.bedtimeMinute)
                }
                ACTION_WAKEUP -> {
                    AlarmScheduler.scheduleNextWakeupAlarm(context, prefs.wakeupHour, prefs.wakeupMinute)
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hẹn giờ ngủ & Thức dậy",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nhận thông báo nhắc nhở đi ngủ và báo thức dậy hàng ngày"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "sleep_reminder_channel"
        const val ACTION_BEDTIME = "com.team.smartnutrition.ACTION_BEDTIME"
        const val ACTION_WAKEUP = "com.team.smartnutrition.ACTION_WAKEUP"
        const val NOTIFICATION_ID_BEDTIME = 4000
        const val NOTIFICATION_ID_WAKEUP = 4001
    }
}
