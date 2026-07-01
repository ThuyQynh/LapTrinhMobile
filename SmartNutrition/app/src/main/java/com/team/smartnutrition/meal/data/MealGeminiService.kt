package com.team.smartnutrition.meal.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.team.smartnutrition.BuildConfig
import com.team.smartnutrition.auth.model.User
import com.team.smartnutrition.meal.model.DayPlan
import com.team.smartnutrition.meal.model.Ingredient
import com.team.smartnutrition.meal.model.Meal
import com.team.smartnutrition.meal.model.MealPlan
import com.team.smartnutrition.meal.util.WeekUtils
import com.team.smartnutrition.pantry.model.PantryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * MEAL GEMINI SERVICE - Gọi Gemini text API để lên thực đơn
 *
 * Pipeline:
 *   User profile + Pantry items → build prompt → Gemini API → JSON parse → MealPlan
 *
 * 4 tầng phòng thủ chống JSON sai:
 *   1. Prompt ép trả JSON thuần
 *   2. Strip markdown wrapper (```json...```)
 *   3. Gson parse với try-catch
 *   4. Validate structure: 7 ngày, 3 bữa, tên không blank
 */
class MealGeminiService {

    companion object {
        private const val TAG = "MealGeminiService"
        private const val MODEL_NAME = "gemini-2.5-flash"  // Dùng model hỗ trợ trên endpoint
        private const val TIMEOUT_MS = 90_000L  // 90s để AI kịp tạo thực đơn lớn

        /** System prompt tiếng Việt - ép AI trả JSON chuẩn (rút gọn để tạo nhanh) */
        private const val SYSTEM_PROMPT = """
Bạn là chuyên gia dinh dưỡng Việt Nam. Lên thực đơn 7 ngày (Thứ 2 → Chủ Nhật), mỗi ngày 3 bữa (breakfast, lunch, dinner).

PHONG CÁCH: Món ăn gia đình Việt Nam, dễ nấu, lành mạnh, đa dạng nguyên liệu giữa các ngày.

QUY TẮC:
- Ưu tiên sử dụng nguyên liệu từ danh sách "Kho thực phẩm" bên dưới (nếu có)
- Calo mỗi ngày bám sát mục tiêu được cung cấp (±10%)
- KHÔNG lặp lại cùng 1 món trong tuần
- Chỉ trả về tên món ăn, lượng calo, lượng protein (KHÔNG kèm nguyên liệu và công thức nấu)

TRẢ VỀ ĐÚNG JSON theo format sau, KHÔNG giải thích thêm, KHÔNG wrap trong markdown:
{
  "days": [
    {
      "dayLabel": "Thứ 2",
      "meals": {
        "breakfast": {
          "name": "tên món bằng tiếng Việt",
          "totalCalories": 450,
          "totalProtein": 25
        },
        "lunch": {},
        "dinner": {}
      }
    }
  ]
}

QUAN TRỌNG: Chỉ trả JSON thuần, KHÔNG wrap trong ```json, KHÔNG giải thích."""

        /** System prompt cho chi tiết nguyên liệu + cách nấu */
        private const val DETAIL_SYSTEM_PROMPT = """
Bạn là chuyên gia dinh dưỡng Việt Nam. Nhiệm vụ của bạn là cung cấp danh sách nguyên liệu chi tiết (kèm định lượng) và các bước thực hiện chi tiết cho món ăn được yêu cầu.

TRẢ VỀ ĐÚNG JSON theo format sau, KHÔNG giải thích thêm, KHÔNG wrap trong markdown:
{
  "ingredients": [
    {"name": "tên nguyên liệu", "amount": "định lượng (ví dụ: 100g, 2 quả, 1 thìa cà phê)"}
  ],
  "recipe": "1. Bước 1...\n2. Bước 2...\n3. Bước 3..."
}

QUAN TRỌNG: Chỉ trả JSON thuần, KHÔNG wrap trong ```json, KHÔNG giải thích."""

        /** System prompt tiếng Việt - ép AI trả JSON cho 1 ngày duy nhất */
        private const val DAILY_SYSTEM_PROMPT = """
Bạn là chuyên gia dinh dưỡng Việt Nam. Lên thực đơn cho 1 ngày duy nhất, gồm 3 bữa (breakfast, lunch, dinner).

PHONG CÁCH: Món ăn gia đình Việt Nam, dễ nấu, lành mạnh.

QUY TẮC:
- Ưu tiên sử dụng nguyên liệu từ danh sách "Kho thực phẩm" bên dưới (nếu có)
- Tổng calo cả ngày bám sát mục tiêu được cung cấp (±10%)
- Chỉ trả về tên món ăn, lượng calo, lượng protein (KHÔNG kèm nguyên liệu và công thức nấu)

TRẢ VỀ ĐÚNG JSON theo format sau, KHÔNG giải thích thêm, KHÔNG wrap trong markdown:
{
  "meals": {
    "breakfast": {
      "name": "tên món ăn sáng bằng tiếng Việt",
      "totalCalories": 450,
      "totalProtein": 25
    },
    "lunch": {
      "name": "tên món ăn trưa bằng tiếng Việt",
      "totalCalories": 700,
      "totalProtein": 35
    },
    "dinner": {
      "name": "tên món ăn tối bằng tiếng Việt",
      "totalCalories": 650,
      "totalProtein": 30
    }
  }
}

QUAN TRỌNG: Chỉ trả JSON thuần, KHÔNG wrap trong ```json, KHÔNG giải thích."""
    }

    // Lazy init cho model tạo thực đơn tuần
    private val weeklyModel: GenerativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) throw apiKeyException()
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(SYSTEM_PROMPT) }
        )
    }

    // Lazy init cho model tạo chi tiết món ăn
    private val detailModel: GenerativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) throw apiKeyException()
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(DETAIL_SYSTEM_PROMPT) }
        )
    }

    // Lazy init cho model tạo thực đơn ngày
    private val dailyModel: GenerativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) throw apiKeyException()
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(DAILY_SYSTEM_PROMPT) }
        )
    }

    private fun apiKeyException() = IllegalStateException(
        "GEMINI_API_KEY chưa được cấu hình. " +
        "Thêm dòng GEMINI_API_KEY=your_key vào file local.properties"
    )
    // PUBLIC API
    /**
     * Gọi Gemini để generate thực đơn cả tuần.
     * @throws Exception nếu timeout, parse fail, hoặc validation fail
     */
    suspend fun generateMealPlan(
        user: User,
        pantryItems: List<PantryItem>
    ): MealPlan {
        val userPrompt = buildUserPrompt(user, pantryItems)

        val response = try {
            // Dùng withTimeout — khi timeout sẽ ném TimeoutCancellationException
            withTimeout(TIMEOUT_MS) {
                weeklyModel.generateContent(
                    content {
                        text(userPrompt)
                    }
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini timeout after ${TIMEOUT_MS}ms")
            throw Exception("AI mất quá nhiều thời gian. Kiểm tra mạng và thử lại.")
        } catch (e: CancellationException) {
            throw e // Không được nuốt CancellationException của coroutine
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API error", e)
            throw Exception("Lỗi kết nối AI: ${e.message ?: "Không xác định"}. Thử lại.")
        }

        val responseText = response.text
            ?: throw Exception("AI không trả về kết quả")

        Log.d(TAG, "Gemini response length: ${responseText.length}")
        Log.d(TAG, "Gemini response preview: ${responseText.take(300)}")

        return parseAndValidate(responseText, user.calorieTarget)
    }

    /**
     * Gọi Gemini để generate thực đơn cho 1 ngày duy nhất.
     * @throws Exception nếu timeout, parse fail, hoặc validation fail
     */
    suspend fun generateDailyMealPlan(
        user: User,
        pantryItems: List<PantryItem>,
        dayLabel: String
    ): DayPlan {
        val userPrompt = buildDailyUserPrompt(user, pantryItems, dayLabel)

        val response = try {
            withTimeout(30000L) { // 30s cho 1 ngày
                dailyModel.generateContent(
                    content {
                        text(userPrompt)
                    }
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini daily timeout after 30s")
            throw Exception("AI mất quá nhiều thời gian. Kiểm tra mạng và thử lại.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini daily API error", e)
            throw Exception("Lỗi kết nối AI: ${e.message ?: "Không xác định"}. Thử lại.")
        }

        val responseText = response.text
            ?: throw Exception("AI không trả về thực đơn ngày")

        Log.d(TAG, "Gemini daily response preview: ${responseText.take(300)}")
        return parseAndValidateDaily(responseText, dayLabel)
    }

    /**
     * Gọi Gemini để generate chi tiết nguyên liệu + công thức của 1 món ăn cụ thể.
     */
    suspend fun generateMealDetail(
        mealName: String,
        userGoal: String,
        calorieTarget: Int
    ): MealDetailResult {
        val prompt = """
Hãy sinh danh sách nguyên liệu và công thức nấu ăn cho:
- Món ăn: "$mealName"
- Lượng Calo mục tiêu: $calorieTarget kcal
- Mục tiêu sức khỏe của người dùng: $userGoal
"""

        val response = try {
            withTimeout(45000L) { // 45s cho 1 món là dư dả
                detailModel.generateContent(prompt)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini detail timeout after 45s")
            throw Exception("AI mất quá nhiều thời gian để tạo công thức. Thử lại.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini detail API error", e)
            throw Exception("Lỗi kết nối AI khi tạo công thức: ${e.message ?: "Không xác định"}")
        }

        val responseText = response.text ?: throw Exception("AI không trả về công thức")
        val jsonString = stripMarkdownWrapper(responseText)

        return try {
            Gson().fromJson(jsonString, MealDetailResult::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Detail JSON parse fail: $jsonString", e)
            throw Exception("Lỗi định dạng công thức từ AI. Thử lại.")
        }
    }

    data class MealDetailResult(
        val ingredients: List<IngredientResult> = emptyList(),
        val recipe: String = ""
    )
    data class IngredientResult(
        val name: String = "",
        val amount: String = ""
    )

    /**
     * Gọi Gemini để đổi món ăn cho 1 bữa cụ thể.
     */
    suspend fun generateSingleMealReplacement(
        user: User,
        pantryItems: List<PantryItem>,
        dayLabel: String,
        mealType: String,
        currentMealName: String
    ): Meal {
        val goalVi = when (user.goal) {
            "lose_weight" -> "Giảm cân"
            "gain_muscle" -> "Tăng cơ"
            else -> "Duy trì cân nặng"
        }

        val mealTypeVi = when (mealType.lowercase()) {
            "breakfast" -> "Bữa sáng"
            "lunch" -> "Bữa trưa"
            "dinner" -> "Bữa tối"
            else -> mealType
        }

        val pantrySection = if (pantryItems.isEmpty()) {
            "KHO THỰC PHẨM: Đang trống — hãy gợi ý thực đơn tự do bám sát chỉ số calo mục tiêu."
        } else {
            val itemsList = pantryItems.joinToString("\n") { item ->
                "- ${item.name}: ${item.caloriesPer100g} kcal/100g, " +
                "${item.proteinPer100g}g protein/100g"
            }
            "KHO THỰC PHẨM HIỆN CÓ (ưu tiên sử dụng):\n$itemsList"
        }

        val prompt = """
Bạn là chuyên gia dinh dưỡng Việt Nam.
Hãy gợi ý một món ăn THAY THẾ cho món ăn hiện tại mà người dùng không thích.
- Bữa cần thay thế: $mealTypeVi của ngày $dayLabel
- Món ăn hiện tại người dùng MUỐN THAY THẾ: "$currentMealName" (Tránh gợi ý lại món này hoặc món quá giống món này).
- Mục tiêu thể trạng người dùng: $goalVi
- Chỉ số calo ước tính cho bữa ăn thay thế này: Khoảng 350 - 750 kcal phù hợp với bữa ăn $mealTypeVi.

$pantrySection

HÃY TRẢ VỀ ĐÚNG JSON THEO FORMAT SAU, KHÔNG GIẢI THÍCH THÊM, KHÔNG WRAP TRONG MARKDOWN:
{
  "name": "tên món ăn thay thế bằng tiếng Việt",
  "totalCalories": 550,
  "totalProtein": 25,
  "ingredients": [
    {"name": "tên nguyên liệu 1", "amount": "100g"},
    {"name": "tên nguyên liệu 2", "amount": "2 quả"}
  ],
  "recipe": "1. Sơ chế...\n2. Chế biến..."
}

QUAN TRỌNG: Chỉ trả JSON thuần, KHÔNG giải thích, KHÔNG có ký tự markdown ```json.
"""

        val response = try {
            withTimeout(30000L) {
                dailyModel.generateContent(prompt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini swap meal API error", e)
            throw Exception("Lỗi kết nối AI khi đổi món: ${e.message ?: "Không xác định"}. Thử lại.")
        }

        val responseText = response.text
            ?: throw Exception("AI không trả về món ăn thay thế")

        val jsonString = stripMarkdownWrapper(responseText)
        return try {
            val geminiMeal = Gson().fromJson(jsonString, GeminiMealItem::class.java)
            if (geminiMeal.name.isBlank()) {
                throw Exception("Món ăn thay thế trống tên")
            }
            Meal(
                name = geminiMeal.name,
                totalCalories = geminiMeal.totalCalories,
                totalProtein = geminiMeal.totalProtein,
                ingredients = geminiMeal.ingredients.map {
                    Ingredient(name = it.name, amount = it.amount)
                },
                recipe = geminiMeal.recipe
            )
        } catch (e: Exception) {
            Log.e(TAG, "Swap meal JSON parse fail: $jsonString", e)
            throw Exception("Lỗi định dạng món ăn từ AI. Thử lại.")
        }
    }
    // PRIVATE: BUILD PROMPT
    /**
     * Tạo user-specific prompt từ profile + pantry items.
     */
    private fun buildUserPrompt(
        user: User,
        pantryItems: List<PantryItem>
    ): String {
        val goalVi = when (user.goal) {
            "lose_weight" -> "Giảm cân"
            "gain_muscle" -> "Tăng cơ"
            else -> "Duy trì cân nặng"
        }

        val pantrySection = if (pantryItems.isEmpty()) {
            "KHO THỰC PHẨM: Đang trống — hãy gợi ý thực đơn tự do bám sát chỉ số calo mục tiêu."
        } else {
            val itemsList = pantryItems.joinToString("\n") { item ->
                "- ${item.name}: ${item.caloriesPer100g} kcal/100g, " +
                "${item.proteinPer100g}g protein/100g, " +
                "còn ${item.quantityGrams}${item.unit}, " +
                "trạng thái: ${item.status}"
            }
            "KHO THỰC PHẨM HIỆN CÓ (ưu tiên sử dụng, đặc biệt items sắp hết hạn):\n$itemsList"
        }

        return """
THÔNG TIN NGƯỜI DÙNG:
- Mục tiêu: $goalVi
- Calo mục tiêu: ${user.calorieTarget} kcal/ngày
- Protein mục tiêu: ${user.proteinTarget}g/ngày
- Carb mục tiêu: ${user.carbTarget}g/ngày
- Fat mục tiêu: ${user.fatTarget}g/ngày

$pantrySection

Hãy lên thực đơn 7 ngày theo đúng format JSON đã hướng dẫn."""
    }

    /**
     * Tạo prompt cho thực đơn 1 ngày từ profile + pantry items.
     */
    private fun buildDailyUserPrompt(
        user: User,
        pantryItems: List<PantryItem>,
        dayLabel: String
    ): String {
        val goalVi = when (user.goal) {
            "lose_weight" -> "Giảm cân"
            "gain_muscle" -> "Tăng cơ"
            else -> "Duy trì cân nặng"
        }

        val pantrySection = if (pantryItems.isEmpty()) {
            "KHO THỰC PHẨM: Đang trống — hãy gợi ý thực đơn tự do bám sát chỉ số calo mục tiêu."
        } else {
            val itemsList = pantryItems.joinToString("\n") { item ->
                "- ${item.name}: ${item.caloriesPer100g} kcal/100g, " +
                "${item.proteinPer100g}g protein/100g, " +
                "còn ${item.quantityGrams}${item.unit}, " +
                "trạng thái: ${item.status}"
            }
            "KHO THỰC PHẨM HIỆN CÓ (ưu tiên sử dụng, đặc biệt items sắp hết hạn):\n$itemsList"
        }

        return """
THÔNG TIN NGƯỜI DÙNG:
- Mục tiêu: $goalVi
- Calo mục tiêu: ${user.calorieTarget} kcal/ngày
- Protein mục tiêu: ${user.proteinTarget}g/ngày
- Carb mục tiêu: ${user.carbTarget}g/ngày
- Fat mục tiêu: ${user.fatTarget}g/ngày

$pantrySection

Hãy lên thực đơn cho ngày: "$dayLabel" theo đúng format JSON đã hướng dẫn."""
    }
    // PRIVATE: JSON PARSE (4 tầng phòng thủ)
    // Tầng 1: Intermediate classes cho Gson parse
    private data class GeminiMealResponse(
        val days: List<GeminiDayResponse> = emptyList()
    )
    private data class GeminiDayResponse(
        val dayLabel: String = "",
        val meals: Map<String, GeminiMealItem> = emptyMap()
    )
    private data class GeminiMealItem(
        val name: String = "",
        val totalCalories: Int = 0,
        val totalProtein: Int = 0,
        val ingredients: List<GeminiIngredient> = emptyList(),
        val recipe: String = ""
    )
    private data class GeminiIngredient(
        val name: String = "",
        val amount: String = ""
    )

    private data class GeminiDailyResponse(
        val meals: Map<String, GeminiMealItem> = emptyMap()
    )

    /**
     * Parse + validate Gemini daily response → DayPlan.
     */
    private fun parseAndValidateDaily(responseText: String, dayLabel: String): DayPlan {
        val jsonString = stripMarkdownWrapper(responseText)
        val geminiResponse = try {
            Gson().fromJson(jsonString, GeminiDailyResponse::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Daily JSON parse fail: ${jsonString.take(300)}", e)
            throw Exception("AI trả kết quả không đúng format cho thực đơn ngày. Vui lòng thử lại.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected daily parse error", e)
            throw Exception("Lỗi xử lý kết quả từ AI. Vui lòng thử lại.")
        }

        if (geminiResponse.meals.isEmpty()) {
            throw Exception("AI không trả về thực đơn cho ngày. Vui lòng thử lại.")
        }
        if (geminiResponse.meals.size < 3) {
            throw Exception("Thực đơn ngày thiếu bữa ăn. Thử lại.")
        }
        geminiResponse.meals.values.forEach { meal ->
            if (meal.name.isBlank()) {
                throw Exception("Có bữa ăn thiếu tên món. Thử lại.")
            }
        }

        val meals = geminiResponse.meals.mapValues { (_, mealResp) ->
            Meal(
                name = mealResp.name,
                totalCalories = mealResp.totalCalories,
                totalProtein = mealResp.totalProtein,
                ingredients = emptyList(),
                recipe = ""
            )
        }
        val dayCalories = meals.values.sumOf { it.totalCalories }
        val dayProtein = meals.values.sumOf { it.totalProtein }

        return DayPlan(
            dayLabel = dayLabel,
            meals = meals,
            totalCalories = dayCalories,
            totalProtein = dayProtein
        )
    }

    /**
     * Parse + validate Gemini response → MealPlan.
     * Ném Exception rõ ràng nếu structure sai.
     */
    private fun parseAndValidate(responseText: String, calorieTarget: Int): MealPlan {
        // Tầng 2: Strip markdown wrapper
        val jsonString = stripMarkdownWrapper(responseText)

        // Tầng 3: Gson parse
        val geminiResponse = try {
            Gson().fromJson(jsonString, GeminiMealResponse::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parse fail: ${jsonString.take(300)}", e)
            throw Exception("AI trả kết quả không đúng format. Vui lòng thử lại.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected parse error", e)
            throw Exception("Lỗi xử lý kết quả từ AI. Vui lòng thử lại.")
        }

        // Tầng 4: Validate structure
        if (geminiResponse.days.isEmpty()) {
            throw Exception("AI không trả về thực đơn. Vui lòng thử lại.")
        }
        if (geminiResponse.days.size != 7) {
            Log.w(TAG, "AI returned ${geminiResponse.days.size} days instead of 7")
            throw Exception("AI chỉ trả về ${geminiResponse.days.size} ngày thay vì 7. Thử lại.")
        }
        geminiResponse.days.forEach { day ->
            if (day.meals.size < 3) {
                throw Exception("Ngày '${day.dayLabel}' thiếu bữa ăn. Thử lại.")
            }
            day.meals.values.forEach { meal ->
                if (meal.name.isBlank()) {
                    throw Exception("Có bữa ăn thiếu tên món. Thử lại.")
                }
            }
        }

        // Convert → domain model
        return toMealPlan(geminiResponse, calorieTarget)
    }

    /**
     * Convert Gemini intermediate model → domain MealPlan.
     */
    private fun toMealPlan(response: GeminiMealResponse, calorieTarget: Int): MealPlan {
        val days = response.days.map { dayResp ->
            val meals = dayResp.meals.mapValues { (_, mealResp) ->
                Meal(
                    name = mealResp.name,
                    totalCalories = mealResp.totalCalories,
                    totalProtein = mealResp.totalProtein,
                    ingredients = mealResp.ingredients.map {
                        Ingredient(name = it.name, amount = it.amount)
                    },
                    recipe = mealResp.recipe
                )
            }
            val dayCalories = meals.values.sumOf { it.totalCalories }
            val dayProtein = meals.values.sumOf { it.totalProtein }
            DayPlan(
                dayLabel = dayResp.dayLabel,
                meals = meals,
                totalCalories = dayCalories,
                totalProtein = dayProtein
            )
        }

        return MealPlan(
            weekId = WeekUtils.getCurrentWeekId(),
            days = days,
            totalCalories = days.sumOf { it.totalCalories },
            calorieTarget = calorieTarget,
            createdAt = Timestamp.now()
        )
    }

    /**
     * Strip markdown code block wrapper.
     * Gemini đôi khi trả: ```json\n{...}\n``` thay vì pure JSON.
     */
    private fun stripMarkdownWrapper(text: String): String {
        var result = text.trim()
        if (result.startsWith("```")) {
            result = result.removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
            if (result.endsWith("```")) {
                result = result.removeSuffix("```")
            }
        }
        return result.trim()
    }
}
