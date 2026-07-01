package com.team.smartnutrition.habit.model

import com.google.firebase.Timestamp

/**
 * DATA MODEL cho Module 4 - Habit Tracker
 *
 * Ánh xạ trực tiếp tới Firestore document:
 *   users/{uid}/habits/{date}
 *
 * Document ID = date string format "yyyy-MM-dd"
 * (giống convention weightLog ở Module 1)
 */
data class HabitDay(
    val date: String = "",              // "2026-06-19" (dùng làm document ID)
    val waterCups: Int = 0,             // Số cốc nước đã uống hôm nay
    val waterGoal: Int = 8,             // Mục tiêu cốc nước/ngày (snapshot từ SharedPreferences)
    val sleepHours: Float = 0f,         // Giờ ngủ đêm qua (0.0 - 12.0, step 0.5)
    val vitaminTaken: Boolean = false,  // Đã uống vitamin hôm nay chưa
    val completedReminders: List<String> = emptyList(), // Danh sách ID nhắc nhở đã hoàn thành hôm nay
    val updatedAt: Timestamp? = null
)
