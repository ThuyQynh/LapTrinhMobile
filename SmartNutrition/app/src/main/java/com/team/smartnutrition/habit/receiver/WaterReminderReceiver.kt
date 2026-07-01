package com.team.smartnutrition.habit.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.team.smartnutrition.MainActivity
import com.team.smartnutrition.R
import com.team.smartnutrition.habit.util.AlarmScheduler

/**
 * WATER REMINDER RECEIVER
 *
 * Được kích hoạt bởi AlarmManager khi đến giờ uống nước.
 * Hiển thị notification với action button "💧 Đã uống ✓".
 * Tự reschedule cho ngày mai (vì setExactAndAllowWhileIdle không repeat).
 */
class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmHour = intent.getIntExtra("alarm_hour", -1)

        // 1. Tạo NotificationChannel (Android 8+)
        createWaterChannel(context)

        // 2. PendingIntent cho action button "Đã uống" → WaterActionReceiver
        val actionIntent = Intent(context, WaterActionReceiver::class.java).apply {
            putExtra("notification_id", WATER_NOTIFICATION_ID)
        }
        val actionPendingIntent = PendingIntent.getBroadcast(
            context, ACTION_REQUEST_CODE, actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. PendingIntent khi click notification → mở app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, OPEN_APP_REQUEST_CODE, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Build notification
        val notification = NotificationCompat.Builder(context, WATER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.water_reminder_title))
            .setContentText(context.getString(R.string.water_reminder_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, context.getString(R.string.water_action_done), actionPendingIntent)
            .build()

        // 5. Hiển thị notification (check permission Android 13+)
        if (Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(WATER_NOTIFICATION_ID, notification)
        }

        // 6. Reschedule cho ngày mai
        if (alarmHour >= 0) {
            AlarmScheduler.scheduleNextWaterAlarm(context, alarmHour)
        }
    }

    private fun createWaterChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WATER_CHANNEL_ID,
                context.getString(R.string.water_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.water_channel_desc)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val WATER_CHANNEL_ID = "water_reminder_channel"
        const val WATER_NOTIFICATION_ID = 1001
        private const val ACTION_REQUEST_CODE = 3000
        private const val OPEN_APP_REQUEST_CODE = 3001
    }
}
