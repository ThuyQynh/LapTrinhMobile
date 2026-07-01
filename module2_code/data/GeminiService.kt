package com.team.smartnutrition.pantry.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.team.smartnutrition.BuildConfig
import com.team.smartnutrition.pantry.model.FoodRecognitionResult
import java.io.ByteArrayOutputStream

/**
 * ═══════════════════════════════════════════
 * GEMINI SERVICE - Gọi Gemini Vision API
 * ═══════════════════════════════════════════
 *
 * Nhận diện thực phẩm từ ảnh chụp bằng Gemini Vision API.
 * Pipeline: Bitmap → Resize → Gemini API → JSON → FoodRecognitionResult
 *
 * Lưu ý:
 * - API Key lấy từ BuildConfig.GEMINI_API_KEY (local.properties)
 * - Ảnh resize xuống max 1024×1024 để tránh request quá 4MB
 * - Gemini đôi khi trả markdown wrapper → cần strip trước khi parse JSON
 */
class GeminiService {

    companion object {
        private const val TAG = "GeminiService"
        private const val MAX_IMAGE_SIZE = 1024
        private const val MODEL_NAME = "gemini-2.5-flash"

        /** Prompt nhận diện thực phẩm (tiếng Việt) */
        private const val FOOD_RECOGNITION_PROMPT = """
Bạn là chuyên gia dinh dưỡng. Nhận diện thực phẩm trong ảnh này.
Trả về ĐÚNG 1 JSON object với format sau:
{"name": "tên tiếng Việt", "calories": số kcal trên 100g, "protein": số gram protein trên 100g}

Quy tắc:
- "name": tên thực phẩm bằng tiếng Việt, viết thường, ngắn gọn
- "calories": số nguyên, ước tính kcal trên 100g
- "protein": số nguyên, ước tính gram protein trên 100g
- Chỉ trả JSON thuần, KHÔNG giải thích thêm, KHÔNG wrap trong markdown
"""
    }

    private val model: GenerativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "GEMINI_API_KEY chưa được cấu hình. " +
                "Thêm dòng GEMINI_API_KEY=your_key vào file local.properties"
            )
        }
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey
        )
    }

    /**
     * Nhận diện thực phẩm từ ảnh Bitmap.
     *
     * @param bitmap ảnh chụp thực phẩm (sẽ tự resize nếu quá lớn)
     * @return FoodRecognitionResult chứa tên, calo, protein
     * @throws Exception nếu API call hoặc JSON parse thất bại
     */
    suspend fun recognizeFood(bitmap: Bitmap): FoodRecognitionResult {
        // 1. Resize bitmap nếu cần
        val resizedBitmap = resizeBitmap(bitmap)

        // 2. Gọi Gemini Vision API
        val response = kotlinx.coroutines.withTimeoutOrNull(30000) {
            model.generateContent(
                content {
                    image(resizedBitmap)
                    text(FOOD_RECOGNITION_PROMPT)
                }
            )
        } ?: throw Exception("Timeout khi gọi AI nhận diện thực phẩm")

        val responseText = response.text
            ?: throw Exception("AI không trả về kết quả")

        Log.d(TAG, "Gemini response: $responseText")

        // 3. Parse JSON (strip markdown wrapper nếu có)
        return parseResponse(responseText)
    }

    /**
     * Resize bitmap xuống max 1024×1024, giữ tỷ lệ.
     * Nếu bitmap đã nhỏ hơn → trả nguyên.
     */
    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }

        val ratio = minOf(
            MAX_IMAGE_SIZE.toFloat() / width,
            MAX_IMAGE_SIZE.toFloat() / height
        )
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Parse response text từ Gemini thành FoodRecognitionResult.
     * Xử lý các trường hợp Gemini wrap JSON trong markdown code block.
     */
    private fun parseResponse(responseText: String): FoodRecognitionResult {
        // Strip markdown wrapper nếu có
        val jsonString = stripMarkdownWrapper(responseText)

        return try {
            val result = Gson().fromJson(jsonString, FoodRecognitionResult::class.java)
            // Validate
            if (result.name.isBlank()) {
                throw Exception("AI không nhận diện được tên thực phẩm")
            }
            result
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parse error: $jsonString", e)
            throw Exception("Không thể đọc kết quả từ AI. Vui lòng chụp lại ảnh rõ hơn.")
        }
    }

    /**
     * Strip markdown code block wrapper.
     * Gemini đôi khi trả: ```json\n{...}\n``` thay vì pure JSON.
     */
    private fun stripMarkdownWrapper(text: String): String {
        var result = text.trim()

        // Remove ```json ... ``` wrapper
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
