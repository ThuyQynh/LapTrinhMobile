package com.team.smartnutrition.habit.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.team.smartnutrition.habit.model.HabitDay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * HABIT REPOSITORY - Lớp dữ liệu cho thói quen
 *
 * Đóng gói toàn bộ Firestore operations cho collection:
 *   users/{uid}/habits/{date}
 *
 * Convention: giống MealRepository (Module 3)
 * - Offline-first: ghi không .await()
 * - Timeout 3s cho reads + fallback cache
 * - Manual mapping (docToHabitDay / habitDayToMap)
 */
class HabitRepository {

    private val firestore = FirebaseFirestore.getInstance()

    /** UID của user hiện tại */
    val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    /** Reference tới habits sub-collection */
    private fun habitsRef(uid: String) =
        firestore.collection("users").document(uid).collection("habits")

    // Đọc dữ liệu

    /**
     * Đọc habit data của 1 ngày.
     * Timeout 3s → fallback đọc từ Firestore cache local.
     * @param date format "yyyy-MM-dd"
     * @return HabitDay? hoặc null nếu chưa có data ngày đó
     */
    suspend fun getHabitDay(uid: String, date: String): HabitDay? {
        val doc = try {
            withTimeout(3000) {
                habitsRef(uid).document(date).get().await()
            }
        } catch (e: Exception) {
            // Fallback đọc từ cache local (offline)
            habitsRef(uid).document(date)
                .get(Source.CACHE).await()
        }

        if (!doc.exists()) return null
        return docToHabitDay(doc)
    }

    // Thêm mới / Cập nhật

    /**
     * Lưu/cập nhật toàn bộ habit day.
     * Fire-and-forget: ghi vào cache local trước, sync sau (offline-first).
     * Document ID = date string.
     */
    fun saveHabitDay(uid: String, habitDay: HabitDay) {
        habitsRef(uid).document(habitDay.date).set(habitDayToMap(habitDay))
    }

    /**
     * Tăng waterCups thêm 1 (atomic).
     * Dùng bởi WaterActionReceiver khi user nhấn "Đã uống" trên notification.
     *
     * Dùng FieldValue.increment(1) + SetOptions.merge() để:
     * - Atomic increment (thread-safe)
     * - Merge = không xóa các fields khác (sleepHours, vitaminTaken, ...)
     * - Tạo document mới nếu chưa tồn tại
     */
    fun incrementWaterCups(uid: String, date: String) {
        habitsRef(uid).document(date).set(
            mapOf(
                "waterCups" to FieldValue.increment(1),
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        )
    }

    // HELPER: Convert HabitDay ↔ Map

    private fun habitDayToMap(h: HabitDay): Map<String, Any?> = mapOf(
        "waterCups" to h.waterCups,
        "waterGoal" to h.waterGoal,
        "sleepHours" to h.sleepHours,
        "vitaminTaken" to h.vitaminTaken,
        "completedReminders" to h.completedReminders,
        "updatedAt" to Timestamp.now()
    )

    private fun docToHabitDay(doc: DocumentSnapshot): HabitDay {
        val data = doc.data ?: return HabitDay(date = doc.id)
        val completedRaw = data["completedReminders"] as? List<*>
        val completedReminders = completedRaw?.mapNotNull { it?.toString() } ?: emptyList()
        return HabitDay(
            date = doc.id,
            waterCups = (data["waterCups"] as? Number)?.toInt() ?: 0,
            waterGoal = (data["waterGoal"] as? Number)?.toInt() ?: 8,
            sleepHours = (data["sleepHours"] as? Number)?.toFloat() ?: 0f,
            vitaminTaken = data["vitaminTaken"] as? Boolean ?: false,
            completedReminders = completedReminders,
            updatedAt = data["updatedAt"] as? Timestamp
        )
    }

    // SETTINGS PERSISTENCE

    /**
     * Lưu cài đặt nhắc nhở thói quen lên Firestore.
     */
    fun saveReminderSettings(uid: String, settings: Map<String, Any?>) {
        firestore.collection("users").document(uid).collection("habitSettings").document("reminders").set(settings)
    }

    /**
     * Tải cài đặt nhắc nhở thói quen từ Firestore.
     */
    suspend fun getReminderSettings(uid: String): Map<String, Any?>? {
        val ref = firestore.collection("users").document(uid).collection("habitSettings").document("reminders")
        return try {
            val doc = withTimeout(3000) {
                ref.get().await()
            }
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            try {
                val doc = ref.get(Source.CACHE).await()
                if (doc.exists()) doc.data else null
            } catch (e2: Exception) {
                null
            }
        }
    }
}
