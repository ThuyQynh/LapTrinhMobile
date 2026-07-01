package com.team.smartnutrition.analytics.model

/**
 * Entry cho biểu đồ cân nặng (LineChart).
 * Map từ Firestore: users/{uid}/weightLog/{date}
 */
data class WeightChartEntry(
    val date: String = "",        // "2026-06-15" (document ID)
    val weightKg: Float = 0f,
    val bmi: Float = 0f
)

/**
 * Entry cho biểu đồ calo hàng ngày (BarChart).
 * Tính từ Firestore: users/{uid}/mealPlans/{weekId}.days[].totalCalories
 */
data class DailyCalorieEntry(
    val date: String = "",        // "2026-06-15"
    val dayLabel: String = "",    // "T2", "T3"...
    val totalCalories: Int = 0,
    val calorieTarget: Int = 0
)

/**
 * Enum cho toggle tuần/tháng trên AnalyticsScreen.
 */
enum class TimeRange {
    WEEK, MONTH
}
