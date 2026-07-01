package com.team.smartnutrition.analytics.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.team.smartnutrition.analytics.model.DailyCalorieEntry
import com.team.smartnutrition.analytics.model.WeightChartEntry
import com.team.smartnutrition.auth.model.User
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class AnalyticsRepository {
    private val firestore = FirebaseFirestore.getInstance()

    val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // WEIGHT LOG (Module 1)
    
    /**
     * Đọc lịch sử cân nặng, sắp xếp theo ngày tăng dần (cũ → mới)
     * cho LineChart. Reverse order so với UserRepository.getWeightHistory().
     */
    suspend fun getWeightHistory(uid: String, limit: Long = 30): List<WeightChartEntry> {
        val snapshot = try {
            withTimeout(3000) {
                firestore.collection("users").document(uid)
                    .collection("weightLog")
                    .orderBy("loggedAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
            }
        } catch (e: Exception) {
            firestore.collection("users").document(uid)
                .collection("weightLog")
                .orderBy("loggedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get(Source.CACHE).await()
        }

        val entries = snapshot.documents.map { doc ->
            WeightChartEntry(
                date = doc.id,
                weightKg = (doc.getDouble("weightKg") ?: 0.0).toFloat(),
                bmi = (doc.getDouble("bmi") ?: 0.0).toFloat()
            )
        }
        return entries.reversed()
    }

    // MEAL PLANS (Module 3)
    
    /**
     * Đọc mealPlan theo weekId → extract daily calories.
     */
    suspend fun getDailyCalories(uid: String, weekId: String): List<DailyCalorieEntry> {
        val doc = try {
            withTimeout(3000) {
                firestore.collection("users").document(uid)
                    .collection("mealPlans").document(weekId)
                    .get().await()
            }
        } catch (e: Exception) {
            firestore.collection("users").document(uid)
                .collection("mealPlans").document(weekId)
                .get(Source.CACHE).await()
        }

        if (!doc.exists()) return emptyList()
        
        val data = doc.data ?: return emptyList()
        val calorieTarget = (data["calorieTarget"] as? Number)?.toInt() ?: 0
        val daysRaw = data["days"] as? List<Map<String, Any>> ?: emptyList()

        return daysRaw.map { dayMap ->
            DailyCalorieEntry(
                date = dayMap["date"] as? String ?: "",
                dayLabel = shortenDayLabel(dayMap["dayLabel"] as? String ?: ""),
                totalCalories = (dayMap["totalCalories"] as? Number)?.toInt() ?: 0,
                calorieTarget = calorieTarget
            )
        }
    }

    // USER PROFILE (Module 1)
    
    suspend fun getUserProfile(uid: String): User? {
        val doc = try {
            withTimeout(3000) {
                firestore.collection("users").document(uid).get().await()
            }
        } catch (e: Exception) {
            firestore.collection("users").document(uid)
                .get(Source.CACHE).await()
        }
        return doc.toObject(User::class.java)?.copy(uid = uid)
    }

    // HELPERS
    
    /** "Thứ Hai" → "T2", "Chủ Nhật" → "CN" */
    private fun shortenDayLabel(label: String): String {
        return when {
            label.contains("Hai") || label.contains("2") -> "T2"
            label.contains("Ba") || label.contains("3") -> "T3"
            label.contains("Tư") || label.contains("4") -> "T4"
            label.contains("Năm") || label.contains("5") -> "T5"
            label.contains("Sáu") || label.contains("6") -> "T6"
            label.contains("Bảy") || label.contains("7") -> "T7"
            label.contains("Nhật") || label.contains("CN") || label.contains("Chủ") -> "CN"
            else -> label.take(3)
        }
    }
}
