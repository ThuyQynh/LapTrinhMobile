package com.team.smartnutrition.habit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.team.smartnutrition.habit.data.HabitRepository
import com.team.smartnutrition.habit.data.ReminderPrefs
import com.team.smartnutrition.habit.model.HabitDay
import com.team.smartnutrition.habit.model.CustomReminder
import com.team.smartnutrition.habit.util.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══════════════════════════════════════════
 * HABIT UI STATE
 * ═══════════════════════════════════════════
 */
data class HabitUiState(
    // === Dashboard state (from Firestore) ===
    val habitDay: HabitDay? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // === Reminder settings (from SharedPreferences) ===
    val waterReminderEnabled: Boolean = false,
    val waterIntervalHours: Int = 2,
    val waterStartHour: Int = 8,
    val waterEndHour: Int = 22,
    val waterGoal: Int = 8,
    val vitaminReminderEnabled: Boolean = false,
    val vitaminHour: Int = 7,
    val vitaminMinute: Int = 0,

    // === Custom reminders ===
    val customReminders: List<CustomReminder> = emptyList(),

    // === Sleep alarm timer settings ===
    val sleepReminderEnabled: Boolean = false,
    val bedtimeHour: Int = 22,
    val bedtimeMinute: Int = 30,
    val wakeupHour: Int = 6,
    val wakeupMinute: Int = 0
)

/**
 * ═══════════════════════════════════════════
 * HABIT VIEW MODEL
 * ═══════════════════════════════════════════
 */
class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HabitRepository()
    private val prefs = ReminderPrefs(application)

    private val _uiState = MutableStateFlow(HabitUiState())
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()

    init {
        loadReminderSettings()
        loadTodayHabit()
    }

    // ═══════════════════════════════════════════════════
    // DASHBOARD ACTIONS
    // ═══════════════════════════════════════════════════

    /** Thêm 1 cốc nước. Optimistic update UI → fire-and-forget Firestore. */
    fun addWaterCup() {
        val uid = repository.currentUid ?: return
        val current = _uiState.value.habitDay ?: return
        val updated = current.copy(waterCups = current.waterCups + 1)

        _uiState.update { it.copy(habitDay = updated) }
        repository.saveHabitDay(uid, updated)
    }

    /** Giảm 1 cốc nước (min 0). */
    fun removeWaterCup() {
        val uid = repository.currentUid ?: return
        val current = _uiState.value.habitDay ?: return
        if (current.waterCups <= 0) return

        val updated = current.copy(waterCups = current.waterCups - 1)
        _uiState.update { it.copy(habitDay = updated) }
        repository.saveHabitDay(uid, updated)
    }

    /** Cập nhật giờ ngủ (0.0 - 12.0, step 0.5). */
    fun updateSleepHours(hours: Float) {
        val uid = repository.currentUid ?: return
        val current = _uiState.value.habitDay ?: return
        // Snap to nearest 0.5
        val snapped = (Math.round(hours * 2) / 2f)
        val updated = current.copy(sleepHours = snapped)

        _uiState.update { it.copy(habitDay = updated) }
        repository.saveHabitDay(uid, updated)
    }

    /** Toggle trạng thái uống vitamin. */
    fun toggleVitamin() {
        val uid = repository.currentUid ?: return
        val current = _uiState.value.habitDay ?: return
        val updated = current.copy(vitaminTaken = !current.vitaminTaken)

        _uiState.update { it.copy(habitDay = updated) }
        repository.saveHabitDay(uid, updated)
    }

    /** Xóa error message sau khi user đã đọc. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ═══════════════════════════════════════════════════
    // CUSTOM REMINDERS ACTIONS (SharedPreferences + AlarmScheduler)
    // ═══════════════════════════════════════════════════

    /** Thêm một nhắc nhở tùy chỉnh mới */
    fun addCustomReminder(name: String, hour: Int, minute: Int) {
        val newReminder = CustomReminder(
            id = "reminder_${System.currentTimeMillis()}",
            name = name,
            hour = hour,
            minute = minute,
            enabled = true
        )
        val list = prefs.customReminders + newReminder
        prefs.customReminders = list
        _uiState.update { it.copy(customReminders = list) }

        AlarmScheduler.scheduleCustomReminderAlarm(getApplication(), newReminder)
    }

    /** Cập nhật nhắc nhở tùy chỉnh */
    fun updateCustomReminder(reminder: CustomReminder) {
        val list = prefs.customReminders.map {
            if (it.id == reminder.id) reminder else it
        }
        prefs.customReminders = list
        _uiState.update { it.copy(customReminders = list) }

        if (reminder.enabled) {
            AlarmScheduler.scheduleCustomReminderAlarm(getApplication(), reminder)
        } else {
            AlarmScheduler.cancelCustomReminderAlarm(getApplication(), reminder.id)
        }
    }

    /** Xóa nhắc nhở tùy chỉnh */
    fun deleteCustomReminder(reminderId: String) {
        val list = prefs.customReminders.filter { it.id != reminderId }
        prefs.customReminders = list
        _uiState.update { it.copy(customReminders = list) }

        AlarmScheduler.cancelCustomReminderAlarm(getApplication(), reminderId)
    }

    /** Bật/Tắt nhắc nhở tùy chỉnh */
    fun toggleCustomReminderEnabled(reminderId: String, enabled: Boolean) {
        val list = prefs.customReminders.map {
            if (it.id == reminderId) it.copy(enabled = enabled) else it
        }
        prefs.customReminders = list
        _uiState.update { it.copy(customReminders = list) }

        val updated = list.find { it.id == reminderId } ?: return
        if (enabled) {
            AlarmScheduler.scheduleCustomReminderAlarm(getApplication(), updated)
        } else {
            AlarmScheduler.cancelCustomReminderAlarm(getApplication(), reminderId)
        }
    }

    /** Check/Uncheck hoàn thành thói quen hôm nay trên Dashboard */
    fun toggleReminderCompleted(reminderId: String) {
        val uid = repository.currentUid ?: return
        val current = _uiState.value.habitDay ?: return
        val completed = current.completedReminders.toMutableList()
        if (completed.contains(reminderId)) {
            completed.remove(reminderId)
        } else {
            completed.add(reminderId)
        }
        
        // Cập nhật vitaminTaken cho tương thích ngược nếu ID là "vitamin"
        val isVitamin = reminderId == "vitamin"
        val updated = current.copy(
            completedReminders = completed,
            vitaminTaken = if (isVitamin) completed.contains("vitamin") else current.vitaminTaken
        )

        _uiState.update { it.copy(habitDay = updated) }
        repository.saveHabitDay(uid, updated)
    }

    // ═══════════════════════════════════════════════════
    // SLEEP REMINDER SETTINGS ACTIONS
    // ═══════════════════════════════════════════════════

    /** Bật/tắt nhắc nhở đi ngủ & thức dậy */
    fun setSleepReminderEnabled(enabled: Boolean) {
        prefs.sleepReminderEnabled = enabled
        _uiState.update { it.copy(sleepReminderEnabled = enabled) }

        val context = getApplication<Application>()
        if (enabled) {
            AlarmScheduler.scheduleSleepAlarms(
                context,
                prefs.bedtimeHour,
                prefs.bedtimeMinute,
                prefs.wakeupHour,
                prefs.wakeupMinute
            )
        } else {
            AlarmScheduler.cancelSleepAlarms(context)
        }
    }

    /** Cập nhật giờ ngủ & thức dậy */
    fun updateSleepSettings(bedtimeHour: Int, bedtimeMinute: Int, wakeupHour: Int, wakeupMinute: Int) {
        prefs.bedtimeHour = bedtimeHour
        prefs.bedtimeMinute = bedtimeMinute
        prefs.wakeupHour = wakeupHour
        prefs.wakeupMinute = wakeupMinute
        _uiState.update {
            it.copy(
                bedtimeHour = bedtimeHour,
                bedtimeMinute = bedtimeMinute,
                wakeupHour = wakeupHour,
                wakeupMinute = wakeupMinute
            )
        }

        if (prefs.sleepReminderEnabled) {
            AlarmScheduler.scheduleSleepAlarms(
                getApplication(),
                bedtimeHour,
                bedtimeMinute,
                wakeupHour,
                wakeupMinute
            )
        }
    }

    // ═══════════════════════════════════════════════════
    // SETTINGS ACTIONS (SharedPreferences + AlarmScheduler)
    // ═══════════════════════════════════════════════════

    /** Bật/tắt nhắc nhở uống nước. */
    fun setWaterReminderEnabled(enabled: Boolean) {
        prefs.waterReminderEnabled = enabled
        _uiState.update { it.copy(waterReminderEnabled = enabled) }

        val context = getApplication<Application>()
        if (enabled) {
            AlarmScheduler.scheduleWaterAlarms(
                context, prefs.waterIntervalHours,
                prefs.waterStartHour, prefs.waterEndHour
            )
        } else {
            AlarmScheduler.cancelWaterAlarms(context)
        }
    }

    /** Đổi interval nhắc nước: 1h, 2h, 3h. */
    fun setWaterInterval(hours: Int) {
        prefs.waterIntervalHours = hours
        _uiState.update { it.copy(waterIntervalHours = hours) }
        rescheduleWaterIfEnabled()
    }

    /** Đổi giờ bắt đầu nhắc nước. */
    fun setWaterStartHour(hour: Int) {
        prefs.waterStartHour = hour
        _uiState.update { it.copy(waterStartHour = hour) }
        rescheduleWaterIfEnabled()
    }

    /** Đổi giờ kết thúc nhắc nước. */
    fun setWaterEndHour(hour: Int) {
        prefs.waterEndHour = hour
        _uiState.update { it.copy(waterEndHour = hour) }
        rescheduleWaterIfEnabled()
    }

    /** Đổi mục tiêu cốc nước/ngày. */
    fun setWaterGoal(cups: Int) {
        prefs.waterGoal = cups
        _uiState.update { it.copy(waterGoal = cups) }

        // Update Firestore habit day nếu đang có
        val uid = repository.currentUid ?: return
        val current = _uiState.value.habitDay ?: return
        val updated = current.copy(waterGoal = cups)
        _uiState.update { it.copy(habitDay = updated) }
        repository.saveHabitDay(uid, updated)
    }

    /** Bật/tắt nhắc nhở uống vitamin (Tương thích ngược). */
    fun setVitaminReminderEnabled(enabled: Boolean) {
        prefs.vitaminReminderEnabled = enabled
        _uiState.update { it.copy(vitaminReminderEnabled = enabled) }

        val context = getApplication<Application>()
        if (enabled) {
            AlarmScheduler.scheduleVitaminAlarm(context, prefs.vitaminHour, prefs.vitaminMinute)
        } else {
            AlarmScheduler.cancelVitaminAlarm(context)
        }

        // Đồng bộ với customReminders nếu có
        val list = prefs.customReminders.map {
            if (it.id == "vitamin") it.copy(enabled = enabled) else it
        }
        prefs.customReminders = list
        _uiState.update { it.copy(customReminders = list) }
    }

    /** Đổi giờ nhắc vitamin (Tương thích ngược). */
    fun setVitaminTime(hour: Int, minute: Int) {
        prefs.vitaminHour = hour
        prefs.vitaminMinute = minute
        _uiState.update { it.copy(vitaminHour = hour, vitaminMinute = minute) }

        if (prefs.vitaminReminderEnabled) {
            AlarmScheduler.scheduleVitaminAlarm(getApplication(), hour, minute)
        }

        // Đồng bộ với customReminders nếu có
        val list = prefs.customReminders.map {
            if (it.id == "vitamin") it.copy(hour = hour, minute = minute) else it
        }
        prefs.customReminders = list
        _uiState.update { it.copy(customReminders = list) }
    }

    // ═══════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════

    /**
     * Load habit data của ngày hôm nay từ Firestore.
     * Nếu chưa có → tạo HabitDay mặc định với waterGoal từ prefs.
     */
    private fun loadTodayHabit() {
        val uid = repository.currentUid ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val habit = repository.getHabitDay(uid, today)

                _uiState.update {
                    it.copy(
                        habitDay = habit ?: HabitDay(
                            date = today,
                            waterGoal = prefs.waterGoal
                        ),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không thể tải dữ liệu: ${e.message}"
                    )
                }
            }
        }
    }

    /** Load tất cả reminder settings từ SharedPreferences vào UI state. */
    private fun loadReminderSettings() {
        _uiState.update {
            it.copy(
                waterReminderEnabled = prefs.waterReminderEnabled,
                waterIntervalHours = prefs.waterIntervalHours,
                waterStartHour = prefs.waterStartHour,
                waterEndHour = prefs.waterEndHour,
                waterGoal = prefs.waterGoal,
                vitaminReminderEnabled = prefs.vitaminReminderEnabled,
                vitaminHour = prefs.vitaminHour,
                vitaminMinute = prefs.vitaminMinute,
                customReminders = prefs.customReminders,
                sleepReminderEnabled = prefs.sleepReminderEnabled,
                bedtimeHour = prefs.bedtimeHour,
                bedtimeMinute = prefs.bedtimeMinute,
                wakeupHour = prefs.wakeupHour,
                wakeupMinute = prefs.wakeupMinute
            )
        }
    }

    /** Reschedule water alarms nếu đang bật. */
    private fun rescheduleWaterIfEnabled() {
        if (prefs.waterReminderEnabled) {
            AlarmScheduler.scheduleWaterAlarms(
                getApplication(),
                prefs.waterIntervalHours,
                prefs.waterStartHour,
                prefs.waterEndHour
            )
        }
    }
}
