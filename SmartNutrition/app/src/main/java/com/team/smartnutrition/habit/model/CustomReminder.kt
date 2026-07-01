package com.team.smartnutrition.habit.model

/**
 * Model cho nhắc nhở tùy chỉnh (ví dụ: uống thuốc, vitamin, v.v.)
 */
data class CustomReminder(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
)
