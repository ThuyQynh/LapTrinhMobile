package com.team.smartnutrition.pantry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.team.smartnutrition.pantry.data.PantryRepository
import com.team.smartnutrition.pantry.model.FoodRecognitionResult
import com.team.smartnutrition.pantry.model.PantryItem
import com.team.smartnutrition.pantry.util.calculateExpiryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * ═══════════════════════════════════════════
 * FOOD RESULT VIEW MODEL
 * ═══════════════════════════════════════════
 *
 * Xử lý:
 * - Hiển thị kết quả AI nhận diện (pre-filled)
 * - Cho user chỉnh sửa tên, calo, protein
 * - Chọn số lượng, đơn vị, ngày hết hạn
 * - Lưu vào Firestore pantry collection
 */

data class FoodResultUiState(
    val name: String = "",
    val caloriesPer100g: String = "0",
    val proteinPer100g: String = "0",
    val quantityGrams: String = "100",
    val unit: String = "gram",
    val expiryDateMillis: Long? = null,    // Epoch millis cho DatePicker
    val source: String = "camera",
    val barcode: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isInitialized: Boolean = false
)

class FoodResultViewModel : ViewModel() {

    private val repository = PantryRepository()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(FoodResultUiState())
    val uiState: StateFlow<FoodResultUiState> = _uiState.asStateFlow()

    /**
     * Khởi tạo từ kết quả AI/barcode (JSON string) hoặc empty cho manual.
     */
    fun initFromResult(
        resultJson: String?,
        source: String?,
        barcode: String?
    ) {
        if (_uiState.value.isInitialized) return // Tránh init lại khi recompose

        val defaultExpiry = LocalDate.now().plusDays(7)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        if (resultJson != null) {
            try {
                val result = gson.fromJson(resultJson, FoodRecognitionResult::class.java)
                _uiState.update {
                    it.copy(
                        name = result.name,
                        caloriesPer100g = result.calories.toString(),
                        proteinPer100g = result.protein.toString(),
                        source = source ?: "camera",
                        barcode = barcode ?: "",
                        expiryDateMillis = defaultExpiry,
                        isInitialized = true
                    )
                }
            } catch (e: Exception) {
                // JSON parse failed → empty form
                _uiState.update {
                    it.copy(
                        source = source ?: "manual",
                        barcode = barcode ?: "",
                        expiryDateMillis = defaultExpiry,
                        isInitialized = true
                    )
                }
            }
        } else {
            // Manual entry
            _uiState.update {
                it.copy(
                    source = source ?: "manual",
                    barcode = barcode ?: "",
                    expiryDateMillis = defaultExpiry,
                    isInitialized = true
                )
            }
        }
    }

    // ═══ FIELD UPDATES ═══

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun updateCalories(value: String) {
        _uiState.update { it.copy(caloriesPer100g = value, errorMessage = null) }
    }

    fun updateProtein(value: String) {
        _uiState.update { it.copy(proteinPer100g = value, errorMessage = null) }
    }

    fun updateQuantity(value: String) {
        _uiState.update { it.copy(quantityGrams = value, errorMessage = null) }
    }

    fun updateUnit(value: String) {
        _uiState.update { it.copy(unit = value, errorMessage = null) }
    }

    fun setExpiryDate(millis: Long) {
        _uiState.update { it.copy(expiryDateMillis = millis, errorMessage = null) }
    }

    /**
     * Kiểm tra form hợp lệ.
     */
    fun isFormValid(): Boolean {
        val state = _uiState.value
        return state.name.isNotBlank()
                && (state.caloriesPer100g.toIntOrNull() ?: 0) >= 0
                && (state.proteinPer100g.toIntOrNull() ?: 0) >= 0
                && (state.quantityGrams.toIntOrNull() ?: 0) > 0
                && state.expiryDateMillis != null
    }

    /**
     * Lưu thực phẩm vào Firestore pantry collection.
     */
    fun saveToKho() {
        val uid = repository.currentUid
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "Chưa đăng nhập") }
            return
        }

        val state = _uiState.value

        // Validate
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập tên thực phẩm") }
            return
        }
        val calories = state.caloriesPer100g.toIntOrNull()
        if (calories == null || calories < 0) {
            _uiState.update { it.copy(errorMessage = "Calo không hợp lệ") }
            return
        }
        val protein = state.proteinPer100g.toIntOrNull()
        if (protein == null || protein < 0) {
            _uiState.update { it.copy(errorMessage = "Protein không hợp lệ") }
            return
        }
        val quantity = state.quantityGrams.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            _uiState.update { it.copy(errorMessage = "Số lượng phải lớn hơn 0") }
            return
        }
        if (state.expiryDateMillis == null) {
            _uiState.update { it.copy(errorMessage = "Vui lòng chọn ngày hết hạn") }
            return
        }

        // Tính expiry Timestamp
        val expiryTimestamp = Timestamp(
            java.util.Date(state.expiryDateMillis)
        )

        // Tính status từ expiry date
        val status = calculateExpiryStatus(expiryTimestamp)

        val item = PantryItem(
            name = state.name.trim(),
            caloriesPer100g = calories,
            proteinPer100g = protein,
            quantityGrams = quantity,
            unit = state.unit,
            source = state.source,
            expiryDate = expiryTimestamp,
            status = status.firestoreValue,
            barcode = state.barcode
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                repository.addItem(uid, item)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Lưu thất bại: ${e.message}"
                    )
                }
            }
        }
    }

    /** Clear error */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
