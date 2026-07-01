package com.team.smartnutrition.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.team.smartnutrition.auth.data.UserRepository
import com.team.smartnutrition.auth.model.User
import com.team.smartnutrition.auth.util.HealthCalculator
import com.team.smartnutrition.auth.util.HealthMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PROFILE SETUP VIEW MODEL
 *
 * Quản lý multi-step wizard 3 bước:
 * Step 1: Giới tính + Năm sinh + Tên hiển thị
 * Step 2: Chiều cao + Cân nặng
 * Step 3: Mục tiêu + Mức độ vận động → tính toán → lưu
 */

data class ProfileSetupUiState(
    val currentStep: Int = 1,           // 1, 2, 3
    val totalSteps: Int = 3,
    // Step 1
    val displayName: String = "",
    val gender: String = "male",        // "male" | "female"
    val birthYear: Int = 2003,
    // Step 2
    val heightCm: Int = 170,
    val weightKg: Double = 65.0,
    // Step 3
    val goal: String = "maintain",      // "lose_weight" | "maintain" | "gain_muscle"
    val activityLevel: Double = 1.55,
    // Calculated metrics (tính khi chuyển sang step 3)
    val metrics: HealthMetrics? = null,
    // UI state
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,       // true → navigate Home
    val errorMessage: String? = null
)

/**
 * Mức độ vận động với label tiếng Việt.
 */
data class ActivityLevelOption(
    val value: Double,
    val label: String,
    val emoji: String
)

val activityLevelOptions = listOf(
    ActivityLevelOption(1.2, "Ít vận động", "🚶"),
    ActivityLevelOption(1.375, "Vận động nhẹ", "🏃"),
    ActivityLevelOption(1.55, "Vận động vừa", "🏋️"),
    ActivityLevelOption(1.725, "Vận động nhiều", "🏅"),
    ActivityLevelOption(1.9, "Rất nhiều", "🔥")
)

/**
 * Mục tiêu sức khỏe với label.
 */
data class GoalOption(
    val value: String,
    val label: String,
    val emoji: String,
    val description: String
)

val goalOptions = listOf(
    GoalOption("lose_weight", "Giảm mỡ", "🔥", "TDEE - 500 kcal/ngày"),
    GoalOption("gain_muscle", "Tăng cơ", "💪", "TDEE + 300 kcal/ngày"),
    GoalOption("maintain", "Duy trì", "⚖️", "Giữ nguyên TDEE")
)

class ProfileSetupViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    // Step 1 updates

    fun updateDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun updateGender(gender: String) {
        _uiState.update { it.copy(gender = gender) }
    }

    fun updateBirthYear(year: Int) {
        _uiState.update { it.copy(birthYear = year) }
    }

    // Step 2 updates

    fun updateHeightCm(height: Int) {
        _uiState.update { it.copy(heightCm = height) }
    }

    fun updateWeightKg(weight: Double) {
        _uiState.update { it.copy(weightKg = weight) }
    }

    // Step 3 updates

    fun updateGoal(goal: String) {
        _uiState.update { it.copy(goal = goal) }
        recalculateMetrics()
    }

    fun updateActivityLevel(level: Double) {
        _uiState.update { it.copy(activityLevel = level) }
        recalculateMetrics()
    }

    // Navigation

    fun nextStep() {
        val state = _uiState.value
        if (state.currentStep < state.totalSteps) {
            val nextStep = state.currentStep + 1
            _uiState.update { it.copy(currentStep = nextStep) }
            // Tính metrics khi chuyển sang step 3
            if (nextStep == 3) {
                recalculateMetrics()
            }
        }
    }

    fun previousStep() {
        val state = _uiState.value
        if (state.currentStep > 1) {
            _uiState.update { it.copy(currentStep = state.currentStep - 1) }
        }
    }

    /** Kiểm tra step hiện tại đã điền đủ chưa */
    fun isCurrentStepValid(): Boolean {
        val state = _uiState.value
        return when (state.currentStep) {
            1 -> state.displayName.isNotBlank() && state.birthYear in 1940..2015
            2 -> state.heightCm in 100..250 && state.weightKg in 30.0..300.0
            3 -> state.goal.isNotBlank() && state.activityLevel > 0
            else -> false
        }
    }

    // Calculate

    private fun recalculateMetrics() {
        val state = _uiState.value
        val metrics = HealthCalculator.calculateAllMetrics(
            weightKg = state.weightKg,
            heightCm = state.heightCm,
            birthYear = state.birthYear,
            gender = state.gender,
            activityLevel = state.activityLevel,
            goal = state.goal
        )
        _uiState.update { it.copy(metrics = metrics) }
    }

    // Save

    /**
     * Lưu profile lên Firestore.
     * Gọi khi user bấm "Hoàn tất" ở step 3.
     */
    fun saveProfile() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val state = _uiState.value
        val metrics = state.metrics ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val user = User(
                    uid = currentUser.uid,
                    email = currentUser.email ?: "",
                    displayName = state.displayName,
                    gender = state.gender,
                    birthYear = state.birthYear,
                    heightCm = state.heightCm,
                    weightKg = state.weightKg,
                    activityLevel = state.activityLevel,
                    goal = state.goal,
                    bmi = metrics.bmi,
                    bmr = metrics.bmr,
                    tdee = metrics.tdee,
                    proteinTarget = metrics.proteinTarget,
                    carbTarget = metrics.carbTarget,
                    fatTarget = metrics.fatTarget,
                    calorieTarget = metrics.calorieTarget
                )
                repository.saveUser(user)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
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

    fun onNavigated() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
