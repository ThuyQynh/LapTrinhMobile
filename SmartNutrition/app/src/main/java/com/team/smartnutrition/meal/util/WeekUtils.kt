package com.team.smartnutrition.meal.util

import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * WEEK UTILS - Helper tính ISO week và labels
 */
object WeekUtils {

    /**
     * Tính ISO week ID cho ngày hiện tại.
     * Format: "yyyy-Www" (VD: "2026-W25")
     */
    fun getCurrentWeekId(): String {
        val today = LocalDate.now()
        val weekFields = WeekFields.ISO
        val weekNumber = today.get(weekFields.weekOfWeekBasedYear())
        val year = today.get(weekFields.weekBasedYear())
        return "$year-W${weekNumber.toString().padStart(2, '0')}"
    }

    /**
     * Danh sách label 7 ngày trong tuần (tiếng Việt).
     */
    val dayLabels = listOf(
        "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật"
    )

    /**
     * Map mealType → label tiếng Việt + emoji.
     */
    val mealTypeLabels = mapOf(
        "breakfast" to "🌅 Sáng",
        "lunch" to "☀️ Trưa",
        "dinner" to "🌙 Tối"
    )

    /** Thứ tự hiển thị meal types */
    val mealTypeOrder = listOf("breakfast", "lunch", "dinner")
}
