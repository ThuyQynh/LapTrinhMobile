package com.team.smartnutrition.pantry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.team.smartnutrition.pantry.model.FoodRecognitionResult
import com.team.smartnutrition.pantry.util.BarcodeDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════
 * BARCODE SCAN VIEW MODEL
 * ═══════════════════════════════════════════
 *
 * Xử lý:
 * - Trạng thái quyền camera
 * - Phát hiện barcode từ ML Kit
 * - Tra cứu sản phẩm trong BarcodeDatabase local
 * - Trả kết quả để navigate sang FoodResult
 */

data class BarcodeScanUiState(
    val hasCameraPermission: Boolean = false,
    val detectedBarcode: String? = null,
    val lookupResult: FoodRecognitionResult? = null,
    val isLookingUp: Boolean = false,
    val notFound: Boolean = false,
    val navigateToResult: Boolean = false,
    val resultJson: String? = null,
    val showResultSheet: Boolean = false,
    val showNotFoundDialog: Boolean = false,
    val errorMessage: String? = null
)

class BarcodeScanViewModel : ViewModel() {

    private val gson = Gson()
    private var processedBarcode: String? = null  // Tránh xử lý trùng

    private val _uiState = MutableStateFlow(BarcodeScanUiState())
    val uiState: StateFlow<BarcodeScanUiState> = _uiState.asStateFlow()

    /**
     * Cập nhật trạng thái quyền camera.
     */
    fun updatePermission(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    /**
     * Xử lý khi ML Kit phát hiện barcode.
     * Chỉ xử lý 1 lần cho mỗi mã (tránh trigger liên tục).
     */
    fun onBarcodeDetected(rawValue: String) {
        // Tránh xử lý trùng
        if (rawValue == processedBarcode) return
        processedBarcode = rawValue

        _uiState.update {
            it.copy(
                detectedBarcode = rawValue,
                isLookingUp = true,
                notFound = false,
                lookupResult = null
            )
        }

        viewModelScope.launch {
            val result = BarcodeDatabase.lookup(rawValue)

            if (result != null) {
                _uiState.update {
                    it.copy(
                        lookupResult = result,
                        isLookingUp = false,
                        showResultSheet = true,
                        resultJson = gson.toJson(result)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        notFound = true,
                        isLookingUp = false,
                        showNotFoundDialog = true
                    )
                }
            }
        }
    }

    /**
     * User xác nhận sản phẩm tìm thấy → navigate FoodResult.
     */
    fun confirmResult() {
        _uiState.update {
            it.copy(
                showResultSheet = false,
                navigateToResult = true
            )
        }
    }

    /**
     * Reset để quét barcode mới.
     */
    fun resetScan() {
        processedBarcode = null
        _uiState.update {
            it.copy(
                detectedBarcode = null,
                lookupResult = null,
                notFound = false,
                showResultSheet = false,
                showNotFoundDialog = false,
                navigateToResult = false,
                resultJson = null
            )
        }
    }

    /**
     * Navigate FoodResult cho manual entry (barcode không tìm thấy).
     */
    fun navigateManualEntry() {
        _uiState.update {
            it.copy(
                showNotFoundDialog = false,
                navigateToResult = true,
                resultJson = null  // null = empty form
            )
        }
    }

    /** Reset navigation state */
    fun onNavigated() {
        _uiState.update { it.copy(navigateToResult = false) }
    }

    /** Dismiss result sheet */
    fun dismissResultSheet() {
        _uiState.update { it.copy(showResultSheet = false) }
        resetScan()
    }

    /** Dismiss not found dialog */
    fun dismissNotFoundDialog() {
        _uiState.update { it.copy(showNotFoundDialog = false) }
        resetScan()
    }
}
