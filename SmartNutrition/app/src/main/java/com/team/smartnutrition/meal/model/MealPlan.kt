package com.team.smartnutrition.meal.model

import com.google.firebase.Timestamp

/**
 * DATA MODELS cho Module 3 - AI Meal Planner
 *
 * Ánh xạ trực tiếp tới Firestore document:
 *   users/{uid}/mealPlans/{weekId}
 *
 * Tất cả fields có default values → Firestore toObject() hoạt động.
 */

/**
 * Toàn bộ thực đơn 1 tuần — map 1:1 với Firestore document.
 * Path: users/{uid}/mealPlans/{weekId}
 */
data class MealPlan(
    val weekId: String = "",                    // "2026-W25" (ISO week)
    val days: List<DayPlan> = emptyList(),      // 7 ngày (Thứ 2 → CN)
    val totalCalories: Int = 0,                 // Tổng calo cả tuần
    val createdAt: Timestamp? = null,
    val calorieTarget: Int = 0                  // Snapshot mục tiêu calo/ngày từ User
)

/**
 * Thực đơn 1 ngày trong tuần.
 */
data class DayPlan(
    val dayLabel: String = "",                  // "Thứ 2", "Thứ 3"... "Chủ Nhật"
    val meals: Map<String, Meal> = emptyMap(),  // keys: "breakfast" | "lunch" | "dinner"
    val totalCalories: Int = 0,                 // Tổng calo ngày
    val totalProtein: Int = 0                   // Tổng protein ngày
)

/**
 * 1 bữa ăn cụ thể (breakfast/lunch/dinner).
 */
data class Meal(
    val name: String = "",                      // "Cơm chiên dương châu"
    val totalCalories: Int = 0,                 // kcal
    val totalProtein: Int = 0,                  // gram protein
    val ingredients: List<Ingredient> = emptyList(),
    val recipe: String = ""                     // "1. Nấu cơm...\n2. Xào..."
)

/**
 * 1 nguyên liệu trong bữa ăn.
 */
data class Ingredient(
    val name: String = "",                      // "Ức gà"
    val amount: String = ""                     // "150g", "2 quả"
)
