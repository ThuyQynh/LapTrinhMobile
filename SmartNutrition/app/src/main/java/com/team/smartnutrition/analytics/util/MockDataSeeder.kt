package com.team.smartnutrition.analytics.util

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.team.smartnutrition.meal.util.WeekUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

object MockDataSeeder {
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Hàm chính để nạp dữ liệu ảo - Chỉ nạp nếu database trống
     */
    fun seedMockData(uid: String) {
        // Kiểm tra và nạp lịch sử cân nặng nếu trống
        firestore.collection("users").document(uid)
            .collection("weightLog")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    seedWeightLog(uid)
                }
            }

        // Kiểm tra và nạp thực đơn nếu trống
        firestore.collection("users").document(uid)
            .collection("mealPlans")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    seedMealPlans(uid)
                }
            }
    }

    // 1. Tạo lịch sử cân nặng ảo trong 30 ngày qua
    private fun seedWeightLog(uid: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (i in 30 downTo 0) {
            val dateCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dateStr = sdf.format(dateCal.time)
            
            // Giả lập cân nặng giảm dần từ 75kg xuống 71kg
            val baseWeight = 75.0 - (30 - i) * 0.13
            val randomOffset = Random.nextDouble(-0.4, 0.4)
            val weight = Math.round((baseWeight + randomOffset) * 10.0) / 10.0
            
            // Tính BMI giả định (ví dụ chiều cao 1.72m)
            val heightM = 1.72
            val bmi = Math.round((weight / (heightM * heightM)) * 10.0) / 10.0

            val entry = hashMapOf(
                "weightKg" to weight,
                "bmi" to bmi,
                "loggedAt" to Timestamp(dateCal.time)
            )

            // Ghi đè hoặc tạo mới document ngày đó
            firestore.collection("users").document(uid)
                .collection("weightLog").document(dateStr)
                .set(entry)
        }
    }

    // 2. Tạo thực đơn & lượng Calories tiêu thụ ảo cho tuần hiện tại
    private fun seedMealPlans(uid: String) {
        val weekId = WeekUtils.getCurrentWeekId()
        val dayLabels = listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val days = dayLabels.mapIndexed { index, label ->
            val cal = Calendar.getInstance().apply {
                // Đặt ngày tương ứng trong tuần
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY + index)
            }
            val dateStr = sdf.format(cal.time)
            
            // Calo dao động quanh mục tiêu 2000 kcal
            val totalCalories = Random.nextInt(1750, 2250)
            val totalProtein = Random.nextInt(85, 125)

            hashMapOf(
                "dayLabel" to label,
                "date" to dateStr,
                "totalCalories" to totalCalories,
                "totalProtein" to totalProtein,
                "meals" to hashMapOf(
                    "breakfast" to hashMapOf(
                        "name" to "Bữa sáng giả lập",
                        "totalCalories" to (totalCalories * 0.25).toInt(),
                        "totalProtein" to (totalProtein * 0.25).toInt(),
                        "ingredients" to listOf(
                            hashMapOf("name" to "Bánh mì đen", "amount" to "2 lát"),
                            hashMapOf("name" to "Trứng luộc", "amount" to "2 quả")
                        ),
                        "recipe" to "Ăn sáng đơn giản."
                    ),
                    "lunch" to hashMapOf(
                        "name" to "Bữa trưa giả lập",
                        "totalCalories" to (totalCalories * 0.45).toInt(),
                        "totalProtein" to (totalProtein * 0.45).toInt(),
                        "ingredients" to listOf(
                            hashMapOf("name" to "Ức gà", "amount" to "200g"),
                            hashMapOf("name" to "Cơm gạo lứt", "amount" to "1 bát")
                        ),
                        "recipe" to "Ức gà áp chảo dùng kèm cơm gạo lứt."
                    ),
                    "dinner" to hashMapOf(
                        "name" to "Bữa tối giả lập",
                        "totalCalories" to (totalCalories * 0.3).toInt(),
                        "totalProtein" to (totalProtein * 0.3).toInt(),
                        "ingredients" to listOf(
                            hashMapOf("name" to "Cá hồi phi lê", "amount" to "150g"),
                            hashMapOf("name" to "Măng tây", "amount" to "100g")
                        ),
                        "recipe" to "Cá hồi nướng kèm măng tây."
                    )
                )
            )
        }

        val mealPlanData = hashMapOf(
            "weekId" to weekId,
            "totalCalories" to days.sumOf { it["totalCalories"] as Int },
            "calorieTarget" to 2000,
            "createdAt" to Timestamp.now(),
            "days" to days
        )

        firestore.collection("users").document(uid)
            .collection("mealPlans").document(weekId)
            .set(mealPlanData)
    }
}
