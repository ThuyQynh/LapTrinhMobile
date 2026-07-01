package com.team.smartnutrition.pantry.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.team.smartnutrition.pantry.data.GeminiService
import com.team.smartnutrition.pantry.model.FoodRecognitionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════
 * CAMERA CAPTURE VIEW MODEL
 * ═══════════════════════════════════════════
 *
 * Xử lý:
 * - Trạng thái quyền camera
 * - Capture ảnh từ CameraX
 * - Gửi ảnh đến Gemini Vision API nhận diện
 * - Trả kết quả dạng JSON string để truyền qua navigation
 */

data class CameraCaptureUiState(
    val hasCameraPermission: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val resultJson: String? = null,      // JSON string để truyền qua savedStateHandle
    val navigateToResult: Boolean = false
)

class CameraCaptureViewModel : ViewModel() {

    private val geminiService = GeminiService()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(CameraCaptureUiState())
    val uiState: StateFlow<CameraCaptureUiState> = _uiState.asStateFlow()

    /**
     * Cập nhật trạng thái quyền camera.
     */
    fun updatePermission(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    /**
     * Xử lý ảnh đã chụp: gửi đến Gemini Vision API.
     */
    fun onPhotoCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

            try {
                val result = geminiService.recognizeFood(bitmap)
                val json = gson.toJson(result)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        resultJson = json,
                        navigateToResult = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Lỗi nhận diện thực phẩm"
                    )
                }
            }
        }
    }

    /** Reset navigation state sau khi đã navigate */
    fun onNavigated() {
        _uiState.update { it.copy(navigateToResult = false, resultJson = null) }
    }

    /** Clear error */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
