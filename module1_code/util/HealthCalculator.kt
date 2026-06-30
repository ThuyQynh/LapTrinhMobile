package com.team.smartnutrition.auth.util

import java.time.Year
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ═══════════════════════════════════════════
 * HEALTH CALCULATOR - Tính chỉ số sức khỏe
 * ═══════════════════════════════════════════
 *
 * Pure functions, chạy LOCAL trên app.
 * Công thức Harris-Benedict cho BMR.
 */
object HealthCalculator {

    /**
     * Tính BMI = cân nặng (kg) / chiều cao (m)²
     */
    fun calculateBmi(weightKg: Double, heightCm: Int): Double {
        if (heightCm <= 0) return 0.0
        val heightM = heightCm / 100.0
        return (weightKg / heightM.pow(2)).roundTo(2)
    }

    /**
     * Phân loại BMI theo chuẩn WHO.
     */
    fun getBmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "Thiếu cân"
        bmi < 25.0 -> "Bình thường"
        bmi < 30.0 -> "Thừa cân"
        else -> "Béo phì"
    }

    /**
     * Tính BMR bằng công thức Harris-Benedict.
     * @param gender "male" hoặc "female"
     * @param age tuổi (năm)
     */
    fun calculateBmr(
        weightKg: Double,
        heightCm: Int,
        age: Int,
        gender: String
    ): Double {
        return if (gender == "male") {
            88.362 + (13.397 * weightKg) + (4.799 * heightCm) - (5.677 * age)
        } else {
            447.593 + (9.247 * weightKg) + (3.098 * heightCm) - (4.330 * age)
        }.roundTo(1)
    }

    /**
     * Tính TDEE = BMR × hệ số vận động.
     * @param activityLevel 1.2 (ít) → 1.9 (rất nhiều)
     */
    fun calculateTdee(bmr: Double, activityLevel: Double): Double {
        return (bmr * activityLevel).roundTo(1)
    }

    /**
     * Calorie mục tiêu, điều chỉnh theo goal.
     * - lose_weight: TDEE - 500
     * - gain_muscle: TDEE + 300
     * - maintain: TDEE giữ nguyên
     */
    fun calculateCalorieTarget(tdee: Double, goal: String): Int {
        return when (goal) {
            "lose_weight" -> (tdee - 500).roundToInt()
            "gain_muscle" -> (tdee + 300).roundToInt()
            else -> tdee.roundToInt()
        }
    }

    /**
     * Tính Macros mục tiêu (Protein, Carb, Fat) theo gram/ngày.
     * @return Triple(proteinGrams, carbGrams, fatGrams)
     */
    fun calculateMacros(calorieTarget: Int, goal: String): Triple<Int, Int, Int> {
        val cal = calorieTarget.toDouble()
        return when (goal) {
            "gain_muscle" -> Triple(
                (cal * 0.30 / 4).roundToInt(),  // 30% Protein (1g = 4 kcal)
                (cal * 0.45 / 4).roundToInt(),  // 45% Carb
                (cal * 0.25 / 9).roundToInt()   // 25% Fat (1g = 9 kcal)
            )
            "lose_weight" -> Triple(
                (cal * 0.35 / 4).roundToInt(),  // 35% Protein
                (cal * 0.35 / 4).roundToInt(),  // 35% Carb
                (cal * 0.30 / 9).roundToInt()   // 30% Fat
            )
            else -> Triple(
                (cal * 0.25 / 4).roundToInt(),  // 25% Protein
                (cal * 0.50 / 4).roundToInt(),  // 50% Carb
                (cal * 0.25 / 9).roundToInt()   // 25% Fat
            )
        }
    }

    /**
     * Tính tuổi từ năm sinh.
     */
    fun calculateAge(birthYear: Int): Int {
        return Year.now().value - birthYear
    }

    /**
     * Tính tất cả chỉ số cùng lúc.
     * Tiện cho ProfileSetup khi cần hiện toàn bộ kết quả.
     */
    fun calculateAllMetrics(
        weightKg: Double,
        heightCm: Int,
        birthYear: Int,
        gender: String,
        activityLevel: Double,
        goal: String
    ): HealthMetrics {
        val age = calculateAge(birthYear)
        val bmi = calculateBmi(weightKg, heightCm)
        val bmr = calculateBmr(weightKg, heightCm, age, gender)
        val tdee = calculateTdee(bmr, activityLevel)
        val calorieTarget = calculateCalorieTarget(tdee, goal)
        val (protein, carb, fat) = calculateMacros(calorieTarget, goal)

        return HealthMetrics(
            bmi = bmi,
            bmiCategory = getBmiCategory(bmi),
            bmr = bmr,
            tdee = tdee,
            calorieTarget = calorieTarget,
            proteinTarget = protein,
            carbTarget = carb,
            fatTarget = fat
        )
    }

    /** Round Double tới N chữ số thập phân */
    private fun Double.roundTo(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (this * factor).roundToInt() / factor
    }
}

/**
 * Kết quả tính toán tất cả chỉ số sức khỏe.
 */
data class HealthMetrics(
    val bmi: Double = 0.0,
    val bmiCategory: String = "",
    val bmr: Double = 0.0,
    val tdee: Double = 0.0,
    val calorieTarget: Int = 0,
    val proteinTarget: Int = 0,
    val carbTarget: Int = 0,
    val fatTarget: Int = 0
)
