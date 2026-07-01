package com.team.smartnutrition.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.team.smartnutrition.auth.data.UserRepository
import com.team.smartnutrition.auth.model.User
import com.team.smartnutrition.auth.util.HealthCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PROFILE VIEW VIEW MODEL
 *
 * Xử lý:
 * - Load user profile từ Firestore
 * - Chế độ Edit (cập nhật + recalculate metrics)
 * - Sign Out
 */

data class ProfileViewUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSignedOut: Boolean = false,
    // Edit fields (chỉ dùng khi isEditing = true)
    val editDisplayName: String = "",
    val editHeightCm: Int = 170,
    val editWeightKg: Double = 65.0,
    val editGender: String = "male",
    val editBirthYear: Int = 2000,
    val editGoal: String = "maintain",
    val editActivityLevel: Double = 1.55
)

class ProfileViewViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _uiState = MutableStateFlow(ProfileViewUiState())
    val uiState: StateFlow<ProfileViewUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * Load user profile từ Firestore.
     */
    fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = repository.getUser(uid)
                _uiState.update {
                    it.copy(
                        user = user,
                        isLoading = false,
                        errorMessage = if (user == null) "Không tìm thấy hồ sơ" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Lỗi tải hồ sơ: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Bắt đầu chế độ chỉnh sửa.
     * Copy dữ liệu hiện tại vào edit fields.
     */
    fun startEditing() {
        val user = _uiState.value.user ?: return
        _uiState.update {
            it.copy(
                isEditing = true,
                editDisplayName = user.displayName,
                editHeightCm = user.heightCm,
                editWeightKg = user.weightKg,
                editGender = user.gender,
                editBirthYear = user.birthYear,
                editGoal = user.goal,
                editActivityLevel = user.activityLevel
            )
        }
    }

    /** Hủy chỉnh sửa */
    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    // Edit field updates

    fun updateEditDisplayName(name: String) {
        _uiState.update { it.copy(editDisplayName = name) }
    }

    fun updateEditHeightCm(height: Int) {
        _uiState.update { it.copy(editHeightCm = height) }
    }

    fun updateEditWeightKg(weight: Double) {
        _uiState.update { it.copy(editWeightKg = weight) }
    }

    fun updateEditGender(gender: String) {
        _uiState.update { it.copy(editGender = gender) }
    }

    fun updateEditBirthYear(year: Int) {
        _uiState.update { it.copy(editBirthYear = year) }
    }

    fun updateEditGoal(goal: String) {
        _uiState.update { it.copy(editGoal = goal) }
    }

    fun updateEditActivityLevel(level: Double) {
        _uiState.update { it.copy(editActivityLevel = level) }
    }

    /**
     * Lưu thay đổi: recalculate metrics + update Firestore.
     */
    fun saveChanges() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val state = _uiState.value
        val currentUser = state.user ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                // Tính lại tất cả metrics
                val metrics = HealthCalculator.calculateAllMetrics(
                    weightKg = state.editWeightKg,
                    heightCm = state.editHeightCm,
                    birthYear = state.editBirthYear,
                    gender = state.editGender,
                    activityLevel = state.editActivityLevel,
                    goal = state.editGoal
                )

                val updatedUser = currentUser.copy(
                    displayName = state.editDisplayName,
                    heightCm = state.editHeightCm,
                    weightKg = state.editWeightKg,
                    gender = state.editGender,
                    birthYear = state.editBirthYear,
                    goal = state.editGoal,
                    activityLevel = state.editActivityLevel,
                    bmi = metrics.bmi,
                    bmr = metrics.bmr,
                    tdee = metrics.tdee,
                    proteinTarget = metrics.proteinTarget,
                    carbTarget = metrics.carbTarget,
                    fatTarget = metrics.fatTarget,
                    calorieTarget = metrics.calorieTarget
                )

                repository.saveUser(updatedUser)
                _uiState.update {
                    it.copy(
                        user = updatedUser,
                        isEditing = false,
                        isSaving = false
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

    /**
     * Đăng xuất.
     */
    fun signOut() {
        repository.signOut()
        _uiState.update { it.copy(isSignedOut = true) }
    }

    fun onSignedOut() {
        _uiState.update { it.copy(isSignedOut = false) }
    }
}
