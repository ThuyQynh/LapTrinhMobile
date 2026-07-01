package com.team.smartnutrition.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.team.smartnutrition.auth.data.UserRepository
import com.team.smartnutrition.auth.model.WeightEntry
import com.team.smartnutrition.auth.util.HealthCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * WEIGHT LOG VIEW MODEL
 *
 * Xử lý:
 * - Nhập cân nặng hôm nay
 * - Tính lại BMI realtime
 * - Lưu vào weightLog/{date} + update user profile
 * - Load lịch sử cân nặng
 */

data class WeightLogUiState(
    val currentWeight: String = "",     // String để user nhập linh hoạt
    val heightCm: Int = 170,            // Lấy từ user profile để tính BMI
    val calculatedBmi: Double? = null,  // BMI mới (tính realtime khi nhập)
    val history: List<WeightEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

class WeightLogViewModel : ViewModel() {

    private val repository = UserRepository()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _uiState = MutableStateFlow(WeightLogUiState())
    val uiState: StateFlow<WeightLogUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    /**
     * Load user height (để tính BMI) + lịch sử cân nặng.
     */
    fun loadData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Load user profile để lấy heightCm
                val user = repository.getUser(uid)
                val heightCm = user?.heightCm ?: 170

                // Load lịch sử
                val history = repository.getWeightHistory(uid)

                _uiState.update {
                    it.copy(
                        heightCm = heightCm,
                        history = history,
                        isLoading = false,
                        // Pre-fill cân nặng hiện tại nếu có
                        currentWeight = user?.weightKg?.toString() ?: ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Lỗi tải dữ liệu: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Cập nhật cân nặng nhập vào.
     * Tính BMI mới realtime.
     */
    fun updateWeight(weight: String) {
        val weightKg = weight.toDoubleOrNull()
        val bmi = if (weightKg != null && weightKg > 0) {
            HealthCalculator.calculateBmi(weightKg, _uiState.value.heightCm)
        } else null

        _uiState.update {
            it.copy(
                currentWeight = weight,
                calculatedBmi = bmi,
                saveSuccess = false
            )
        }
    }

    /**
     * Lưu cân nặng hôm nay.
     * - Upsert vào weightLog/{today}
     * - Update user profile weightKg + bmi
     */
    fun saveWeight() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val state = _uiState.value
        val weightKg = state.currentWeight.toDoubleOrNull() ?: return
        val bmi = state.calculatedBmi ?: return

        if (weightKg < 30 || weightKg > 300) {
            _uiState.update { it.copy(errorMessage = "Cân nặng phải từ 30 đến 300 kg") }
            return
        }

        val today = LocalDate.now().format(dateFormatter)

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                repository.logWeight(uid, today, weightKg, bmi)
                // Reload lịch sử
                val history = repository.getWeightHistory(uid)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        history = history
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Lưu thất bại: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
