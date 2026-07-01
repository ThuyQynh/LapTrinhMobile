package com.team.smartnutrition.habit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.team.smartnutrition.habit.data.ReminderPrefs
import com.team.smartnutrition.habit.util.AlarmScheduler

/**
 * BOOT RECEIVER
 *
 * Được kích hoạt khi thiết bị khởi động lại (BOOT_COMPLETED).
 * Mọi alarm bị hủy khi restart → BootReceiver đọc SharedPreferences
 * và đặt lại tất cả alarm đang bật.
 *
 * Yêu cầu AndroidManifest:
 *   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 *   <receiver android:name=".habit.receiver.BootReceiver" android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BOOT_COMPLETED" />
 *       </intent-filter>
 *   </receiver>
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("BootReceiver", "Device rebooted — restoring alarms")

        val prefs = ReminderPrefs(context)

        // Khôi phục water alarms nếu đang bật
        if (prefs.waterReminderEnabled) {
            AlarmScheduler.scheduleWaterAlarms(
                context,
                prefs.waterIntervalHours,
                prefs.waterStartHour,
                prefs.waterEndHour
            )
            Log.d("BootReceiver", "Water alarms restored: every ${prefs.waterIntervalHours}h, ${prefs.waterStartHour}:00-${prefs.waterEndHour}:00")
        }

        // Khôi phục vitamin alarm nếu đang bật
        if (prefs.vitaminReminderEnabled) {
            AlarmScheduler.scheduleVitaminAlarm(
                context,
                prefs.vitaminHour,
                prefs.vitaminMinute
            )
            Log.d("BootReceiver", "Vitamin alarm restored: ${prefs.vitaminHour}:${String.format("%02d", prefs.vitaminMinute)}")
        }

        // Khôi phục các custom reminders tùy chỉnh
        prefs.customReminders.forEach { reminder ->
            if (reminder.enabled) {
                AlarmScheduler.scheduleCustomReminderAlarm(context, reminder)
                Log.d("BootReceiver", "Custom reminder restored: ${reminder.name} at ${reminder.hour}:${String.format("%02d", reminder.minute)}")
            }
        }

        // Khôi phục báo thức đi ngủ
        if (prefs.sleepReminderEnabled) {
            AlarmScheduler.scheduleSleepAlarm(
                context,
                prefs.bedtimeHour,
                prefs.bedtimeMinute
            )
            Log.d("BootReceiver", "Bedtime alarm restored: ${prefs.bedtimeHour}:${prefs.bedtimeMinute}")
        }
    }
}
