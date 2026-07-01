package com.team.smartnutrition.pantry.model

import com.google.firebase.Timestamp

/**
 * DATA MODELS cho Module 2 - Pantry Scanner
 *
 * Ánh xạ trực tiếp tới Firestore document:
 * - PantryItem → users/{uid}/pantry/{autoId}
 * - FoodRecognitionResult → kết quả từ Gemini Vision API
 */

/**
 * Thực phẩm trong kho, khớp 1:1 với Firestore schema.
 * Dùng default values để Firestore.toObject() hoạt động.
 */
data class PantryItem(
    val id: String = "",                        // Firestore document ID
    val name: String = "",                      // Tên thực phẩm (VD: "Ức gà")
    val caloriesPer100g: Int = 0,               // kcal trên 100g
    val proteinPer100g: Int = 0,                // gram protein trên 100g
    val quantityGrams: Int = 0,                 // Tổng gram đang có
    val unit: String = "gram",                  // "gram" | "piece" | "ml"
    val source: String = "camera",              // "camera" | "barcode" | "manual"
    val imageUrl: String = "",                  // (optional) URL ảnh đã chụp
    val expiryDate: Timestamp? = null,          // Ngày hết hạn
    val addedAt: Timestamp? = null,             // Ngày thêm vào kho
    val status: String = "fresh",               // "fresh" | "expiring" | "expired"
    val barcode: String = ""                    // Mã vạch nếu có
)

/**
 * Kết quả nhận diện thực phẩm từ Gemini Vision API.
 * Dùng để truyền giữa CameraCapture/BarcodeScan → FoodResult.
 */
data class FoodRecognitionResult(
    val name: String = "",
    val calories: Int = 0,                      // kcal trên 100g
    val protein: Int = 0                        // gram protein trên 100g
)
