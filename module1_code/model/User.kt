package com.team.smartnutrition.auth.model

import com.google.firebase.Timestamp

/**
 * ═══════════════════════════════════════════
 * DATA MODELS cho Module 1 - Auth & Profile
 * ═══════════════════════════════════════════
 *
 * Ánh xạ trực tiếp tới Firestore document:
 * - User → users/{uid}
 * - WeightEntry → users/{uid}/weightLog/{date}
 */

/**
 * User profile data, khớp 1:1 với Firestore schema.
 * Dùng default values để Firestore.toObject() hoạt động.
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val gender: String = "male",            // "male" | "female"
    val birthYear: Int = 2000,
    val heightCm: Int = 170,
    val weightKg: Double = 65.0,
    val activityLevel: Double = 1.55,       // 1.2 → 1.9
    val goal: String = "maintain",          // "lose_weight" | "maintain" | "gain_muscle"
    val bmi: Double = 0.0,
    val bmr: Double = 0.0,
    val tdee: Double = 0.0,
    val proteinTarget: Int = 0,             // gram/ngày
    val carbTarget: Int = 0,                // gram/ngày
    val fatTarget: Int = 0,                 // gram/ngày
    val calorieTarget: Int = 0,             // kcal/ngày
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Một entry cân nặng trong sub-collection weightLog.
 * Document ID = "yyyy-MM-dd" (mỗi ngày 1 record duy nhất).
 */
data class WeightEntry(
    val weightKg: Double = 0.0,
    val bmi: Double = 0.0,
    val loggedAt: Timestamp? = null,
    val date: String = ""                   // Document ID, format "yyyy-MM-dd"
)
