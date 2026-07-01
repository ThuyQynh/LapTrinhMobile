package com.team.smartnutrition.habit.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.team.smartnutrition.habit.model.CustomReminder

/**
 * REMINDER PREFS - SharedPreferences wrapper
 *
 * Lưu cài đặt nhắc nhở LOCAL (không lên Firestore).
 */
class ReminderPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("habit_reminder_prefs", Context.MODE_PRIVATE)
    // WATER REMINDER SETTINGS
    /** Bật/tắt nhắc nhở uống nước */
    var waterReminderEnabled: Boolean
        get() = prefs.getBoolean("water_reminder_enabled", false)
        set(v) = prefs.edit().putBoolean("water_reminder_enabled", v).apply()

    /** Chu kỳ nhắc: mỗi 1h, 2h, hoặc 3h */
    var waterIntervalHours: Int
        get() = prefs.getInt("water_interval_hours", 2)
        set(v) = prefs.edit().putInt("water_interval_hours", v).apply()

    /** Giờ bắt đầu nhắc (0-23, default 8:00) */
    var waterStartHour: Int
        get() = prefs.getInt("water_start_hour", 8)
        set(v) = prefs.edit().putInt("water_start_hour", v).apply()

    /** Giờ kết thúc nhắc (0-23, default 22:00) */
    var waterEndHour: Int
        get() = prefs.getInt("water_end_hour", 22)
        set(v) = prefs.edit().putInt("water_end_hour", v).apply()

    /** Mục tiêu số cốc nước / ngày (default 8) */
    var waterGoal: Int
        get() = prefs.getInt("water_goal", 8)
        set(v) = prefs.edit().putInt("water_goal", v).apply()
    // VITAMIN REMINDER SETTINGS (Backward compatibility)
    /** Bật/tắt nhắc nhở uống vitamin */
    var vitaminReminderEnabled: Boolean
        get() = prefs.getBoolean("vitamin_reminder_enabled", false)
        set(v) = prefs.edit().putBoolean("vitamin_reminder_enabled", v).apply()

    /** Giờ nhắc vitamin (0-23, default 7:00 sáng) */
    var vitaminHour: Int
        get() = prefs.getInt("vitamin_hour", 7)
        set(v) = prefs.edit().putInt("vitamin_hour", v).apply()

    /** Phút nhắc vitamin (0-59, default 0) */
    var vitaminMinute: Int
        get() = prefs.getInt("vitamin_minute", 0)
        set(v) = prefs.edit().putInt("vitamin_minute", v).apply()
    // CUSTOM REMINDERS SETTINGS
    /** Lấy danh sách nhắc nhở tùy chỉnh */
    var customReminders: List<CustomReminder>
        get() {
            val json = prefs.getString("custom_reminders_json", null) ?: return getDefaultReminders()
            val type = object : TypeToken<List<CustomReminder>>() {}.type
            return try {
                Gson().fromJson(json, type) ?: getDefaultReminders()
            } catch (e: Exception) {
                getDefaultReminders()
            }
        }
        set(value) {
            val json = Gson().toJson(value)
            prefs.edit().putString("custom_reminders_json", json).apply()
        }

    private fun getDefaultReminders(): List<CustomReminder> {
        // Tạo một nhắc nhở Vitamin mặc định để di chuyển người dùng cũ mượt mà
        return listOf(
            CustomReminder(
                id = "vitamin",
                name = "Uống vitamin",
                hour = vitaminHour,
                minute = vitaminMinute,
                enabled = vitaminReminderEnabled
            )
        )
    }
    // SLEEP REMINDER SETTINGS
    /** Bật/tắt nhắc nhở đi ngủ & thức dậy */
    var sleepReminderEnabled: Boolean
        get() = prefs.getBoolean("sleep_reminder_enabled", false)
        set(v) = prefs.edit().putBoolean("sleep_reminder_enabled", v).apply()

    /** Giờ đi ngủ (0-23, default 22:30) */
    var bedtimeHour: Int
        get() = prefs.getInt("bedtime_hour", 22)
        set(v) = prefs.edit().putInt("bedtime_hour", v).apply()

    /** Phút đi ngủ (0-59, default 30) */
    var bedtimeMinute: Int
        get() = prefs.getInt("bedtime_minute", 30)
        set(v) = prefs.edit().putInt("bedtime_minute", v).apply()
}
