package com.team.smartnutrition.habit.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.team.smartnutrition.habit.data.ReminderPrefs
import com.team.smartnutrition.habit.receiver.VitaminReminderReceiver
import com.team.smartnutrition.habit.receiver.WaterReminderReceiver
import java.util.Calendar

/**
 * ALARM SCHEDULER - Utility quản lý alarm
 *
 * Stateless object — nhận Context để schedule/cancel alarms.
 * Dùng setExactAndAllowWhileIdle() để hoạt động trong Doze mode.
 *
 * Được gọi bởi:
 * - HabitViewModel: khi user thay đổi settings
 * - BootReceiver: khi thiết bị khởi động lại
 * - WaterReminderReceiver: reschedule cho ngày mai
 * - VitaminReminderReceiver: reschedule cho ngày mai
 *
 * Request Code Convention:
 *   1000-1019 → Water reminder alarms (max 20 slots/day)
 *   2000      → Vitamin reminder alarm
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    private const val WATER_BASE_CODE = 1000
    private const val WATER_MAX_SLOTS = 20
    private const val VITAMIN_CODE = 2000
    // WATER REMINDERS (Interval-based)
    /**
     * Lên toàn bộ water alarm trong ngày.
     * Cancel cũ → tạo mới theo interval + start/end hour.
     *
     * VD: interval=2h, start=8, end=22
     *   → Alarms: 8:00, 10:00, 12:00, 14:00, 16:00, 18:00, 20:00, 22:00
     */
    fun scheduleWaterAlarms(
        context: Context,
        intervalHours: Int,
        startHour: Int,
        endHour: Int
    ) {
        // 1. Cancel tất cả alarm cũ
        cancelWaterAlarms(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 2. Kiểm tra quyền exact alarm (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — permission not granted")
            return
        }

        // 3. Tạo alarm cho mỗi slot
        var requestCode = WATER_BASE_CODE
        var hour = startHour

        while (hour <= endHour && requestCode < WATER_BASE_CODE + WATER_MAX_SLOTS) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Nếu giờ đã qua hôm nay → đặt cho ngày mai
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, WaterReminderReceiver::class.java).apply {
                putExtra("alarm_hour", hour)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Log.d(TAG, "Water alarm scheduled: $hour:00 (requestCode=$requestCode)")
            hour += intervalHours
            requestCode++
        }
    }

    /**
     * Hủy tất cả water alarm (cancel hết 20 request code slots).
     */
    fun cancelWaterAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (code in WATER_BASE_CODE until WATER_BASE_CODE + WATER_MAX_SLOTS) {
            val intent = Intent(context, WaterReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        Log.d(TAG, "All water alarms cancelled")
    }

    /**
     * Đặt lại alarm cho ngày mai cùng giờ.
     * Gọi trong WaterReminderReceiver.onReceive() vì setExactAndAllowWhileIdle() không repeat.
     */
    fun scheduleNextWaterAlarm(context: Context, alarmHour: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val prefs = ReminderPrefs(context)
        if (!prefs.waterReminderEnabled) return

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1) // Ngày mai
            set(Calendar.HOUR_OF_DAY, alarmHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Request code dựa trên offset: (hour - startHour) / interval
        val offset = if (prefs.waterIntervalHours > 0) {
            (alarmHour - prefs.waterStartHour) / prefs.waterIntervalHours
        } else 0
        val requestCode = WATER_BASE_CODE + offset.coerceIn(0, WATER_MAX_SLOTS - 1)

        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            putExtra("alarm_hour", alarmHour)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d(TAG, "Next water alarm scheduled: tomorrow $alarmHour:00")
    }
    // VITAMIN REMINDER (Fixed daily)
    /**
     * Đặt 1 alarm vitamin theo giờ cố định.
     * Cancel cũ trước → đặt mới.
     */
    fun scheduleVitaminAlarm(context: Context, hour: Int, minute: Int) {
        cancelVitaminAlarm(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms for vitamin — permission not granted")
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, VitaminReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, VITAMIN_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d(TAG, "Vitamin alarm scheduled: $hour:${String.format("%02d", minute)}")
    }

    /**
     * Hủy vitamin alarm.
     */
    fun cancelVitaminAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, VitaminReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, VITAMIN_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        Log.d(TAG, "Vitamin alarm cancelled")
    }

    /**
     * Đặt lại vitamin alarm cho ngày mai.
     * Gọi trong VitaminReminderReceiver.onReceive().
     */
    fun scheduleNextVitaminAlarm(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, VitaminReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, VITAMIN_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d(TAG, "Next vitamin alarm scheduled: tomorrow $hour:${String.format("%02d", minute)}")
    }
    // CUSTOM REMINDERS (Dynamic)
    fun scheduleCustomReminderAlarm(context: Context, reminder: com.team.smartnutrition.habit.model.CustomReminder) {
        cancelCustomReminderAlarm(context, reminder.id)

        if (!reminder.enabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms for custom reminder — permission not granted")
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, com.team.smartnutrition.habit.receiver.CustomReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_name", reminder.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminder.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d(TAG, "Custom alarm scheduled: ${reminder.name} at ${reminder.hour}:${String.format("%02d", reminder.minute)}")
    }

    fun cancelCustomReminderAlarm(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.team.smartnutrition.habit.receiver.CustomReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        Log.d(TAG, "Custom alarm cancelled: ID = $reminderId")
    }

    fun scheduleNextCustomReminderAlarm(context: Context, reminder: com.team.smartnutrition.habit.model.CustomReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, com.team.smartnutrition.habit.receiver.CustomReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_name", reminder.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminder.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d(TAG, "Next custom alarm scheduled: tomorrow ${reminder.name} at ${reminder.hour}:${String.format("%02d", reminder.minute)}")
    }
    // SLEEP REMINDER (Bedtime only)
    fun scheduleSleepAlarm(context: Context, bedtimeHour: Int, bedtimeMinute: Int) {
        cancelSleepAlarm(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms for bedtime — permission not granted")
            return
        }

        val bedtimeCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, bedtimeHour)
            set(Calendar.MINUTE, bedtimeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val bedtimeIntent = Intent(context, com.team.smartnutrition.habit.receiver.SleepReminderReceiver::class.java).apply {
            action = com.team.smartnutrition.habit.receiver.SleepReminderReceiver.ACTION_BEDTIME
        }
        val bedtimePending = PendingIntent.getBroadcast(
            context, com.team.smartnutrition.habit.receiver.SleepReminderReceiver.NOTIFICATION_ID_BEDTIME, bedtimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            bedtimeCalendar.timeInMillis,
            bedtimePending
        )

        Log.d(TAG, "Bedtime alarm scheduled at $bedtimeHour:$bedtimeMinute")
    }

    fun cancelSleepAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val bedtimeIntent = Intent(context, com.team.smartnutrition.habit.receiver.SleepReminderReceiver::class.java).apply {
            action = com.team.smartnutrition.habit.receiver.SleepReminderReceiver.ACTION_BEDTIME
        }
        val bedtimePending = PendingIntent.getBroadcast(
            context, com.team.smartnutrition.habit.receiver.SleepReminderReceiver.NOTIFICATION_ID_BEDTIME, bedtimeIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        bedtimePending?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        Log.d(TAG, "Bedtime alarm cancelled")
    }

    fun scheduleNextBedtimeAlarm(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, com.team.smartnutrition.habit.receiver.SleepReminderReceiver::class.java).apply {
            action = com.team.smartnutrition.habit.receiver.SleepReminderReceiver.ACTION_BEDTIME
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, com.team.smartnutrition.habit.receiver.SleepReminderReceiver.NOTIFICATION_ID_BEDTIME, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Next Bedtime alarm scheduled: tomorrow at $hour:$minute")
    }
}
