package com.team.smartnutrition.meal.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.team.smartnutrition.meal.model.DayPlan
import com.team.smartnutrition.meal.model.Ingredient
import com.team.smartnutrition.meal.model.Meal
import com.team.smartnutrition.meal.model.MealPlan
import kotlinx.coroutines.tasks.await

/**
 * MEAL REPOSITORY - Lớp dữ liệu cho thực đơn AI
 *
 * Đóng gói toàn bộ Firestore operations cho collection:
 *   users/{uid}/mealPlans/{weekId}
 *
 * Convention: giống PantryRepository (Module 2)
 * - Offline-first: ghi không .await()
 * - Timeout 3s cho reads + fallback cache
 * - Manual mapping thay vì toObject() (do nested generics)
 */
class MealRepository {

    private val firestore = FirebaseFirestore.getInstance()

    /** UID của user hiện tại */
    val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    /** Reference tới mealPlans collection của user */
    private fun mealPlansRef(uid: String) =
        firestore.collection("users").document(uid).collection("mealPlans")

    // Thêm mới / Cập nhật

    suspend fun saveMealPlan(uid: String, mealPlan: MealPlan) {
        val data = mealPlanToMap(mealPlan)
        mealPlansRef(uid).document(mealPlan.weekId).set(data).await()
    }

    // Đọc dữ liệu

    /**
     * Đọc meal plan theo weekId.
     * Timeout 3s → fallback đọc từ Firestore cache local.
     * @return MealPlan? hoặc null nếu không tồn tại
     */
    suspend fun getMealPlan(uid: String, weekId: String): MealPlan? {
        val doc = try {
            kotlinx.coroutines.withTimeout(3000) {
                mealPlansRef(uid).document(weekId).get().await()
            }
        } catch (e: Exception) {
            try {
                // Fallback đọc từ cache local (offline)
                mealPlansRef(uid).document(weekId)
                    .get(com.google.firebase.firestore.Source.CACHE).await()
            } catch (cacheEx: Exception) {
                null // Nếu cache cũng lỗi (không có trong cache), trả về null thay vì ném lỗi
            }
        }

        if (doc == null || !doc.exists()) return null
        return docToMealPlan(doc)
    }

    /**
     * Đọc meal plan gần nhất (sắp xếp theo createdAt DESC).
     * Dùng khi mở app: hiện plan gần nhất thay vì màn trống.
     * @return MealPlan? hoặc null nếu chưa có plan nào
     */
    suspend fun getLatestMealPlan(uid: String): MealPlan? {
        val snapshot = try {
            kotlinx.coroutines.withTimeout(3000) {
                mealPlansRef(uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
            }
        } catch (e: Exception) {
            try {
                // Fallback đọc từ cache local
                mealPlansRef(uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get(com.google.firebase.firestore.Source.CACHE)
                    .await()
            } catch (cacheEx: Exception) {
                null
            }
        }

        if (snapshot == null) return null
        val doc = snapshot.documents.firstOrNull() ?: return null
        return docToMealPlan(doc)
    }

    // Xóa dữ liệu

    /**
     * Xóa meal plan cũ trước khi regenerate.
     * Fire-and-forget.
     */
    fun deleteMealPlan(uid: String, weekId: String) {
        mealPlansRef(uid).document(weekId).delete()
    }

    // HELPER: Convert MealPlan → Map

    /**
     * Convert MealPlan → Map cho Firestore.
     * Firestore serialize maps/lists tự nhiên.
     */
    private fun mealPlanToMap(plan: MealPlan): Map<String, Any?> {
        return mapOf(
            "weekId" to plan.weekId,
            "totalCalories" to plan.totalCalories,
            "calorieTarget" to plan.calorieTarget,
            "createdAt" to (plan.createdAt ?: Timestamp.now()),
            "days" to plan.days.map { day ->
                mapOf(
                    "dayLabel" to day.dayLabel,
                    "totalCalories" to day.totalCalories,
                    "totalProtein" to day.totalProtein,
                    "meals" to day.meals.mapValues { (_, meal) ->
                        mapOf(
                            "name" to meal.name,
                            "totalCalories" to meal.totalCalories,
                            "totalProtein" to meal.totalProtein,
                            "ingredients" to meal.ingredients.map { ing ->
                                mapOf("name" to ing.name, "amount" to ing.amount)
                            },
                            "recipe" to meal.recipe
                        )
                    }
                )
            }
        )
    }

    // HELPER: Convert Firestore Document → MealPlan

    /**
     * Convert Firestore DocumentSnapshot → MealPlan.
     * Dùng manual mapping thay vì toObject() vì nested generic types
     * (Map<String, Meal> chứa List<Ingredient>) gây ClassCastException.
     */
    @Suppress("UNCHECKED_CAST")
    private fun docToMealPlan(doc: com.google.firebase.firestore.DocumentSnapshot): MealPlan {
        val data = doc.data ?: return MealPlan()
        val daysRaw = data["days"] as? List<Map<String, Any>> ?: emptyList()

        val days = daysRaw.map { dayMap ->
            val mealsRaw = dayMap["meals"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val meals = mealsRaw.mapValues { (_, mealMap) ->
                val ingredientsRaw = mealMap["ingredients"] as? List<Map<String, Any>> ?: emptyList()
                Meal(
                    name = mealMap["name"] as? String ?: "",
                    totalCalories = (mealMap["totalCalories"] as? Number)?.toInt() ?: 0,
                    totalProtein = (mealMap["totalProtein"] as? Number)?.toInt() ?: 0,
                    ingredients = ingredientsRaw.map { ingMap ->
                        Ingredient(
                            name = ingMap["name"] as? String ?: "",
                            amount = ingMap["amount"] as? String ?: ""
                        )
                    },
                    recipe = mealMap["recipe"] as? String ?: ""
                )
            }
            DayPlan(
                dayLabel = dayMap["dayLabel"] as? String ?: "",
                totalCalories = (dayMap["totalCalories"] as? Number)?.toInt() ?: 0,
                totalProtein = (dayMap["totalProtein"] as? Number)?.toInt() ?: 0,
                meals = meals
            )
        }

        return MealPlan(
            weekId = data["weekId"] as? String ?: doc.id,
            days = days,
            totalCalories = (data["totalCalories"] as? Number)?.toInt() ?: 0,
            calorieTarget = (data["calorieTarget"] as? Number)?.toInt() ?: 0,
            createdAt = data["createdAt"] as? Timestamp
        )
    }
}
