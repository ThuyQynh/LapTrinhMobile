package com.team.smartnutrition.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.smartnutrition.auth.data.UserRepository
import com.team.smartnutrition.auth.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════
 * REGISTER VIEW MODEL
 * ═══════════════════════════════════════════
 *
 * Xử lý đăng ký tài khoản mới.
 * Validation realtime + Firebase createUserWithEmailAndPassword.
 */

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistered: Boolean = false,  // true → navigate ProfileSetup
    // Validation errors (null = hợp lệ hoặc chưa nhập)
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
)

class RegisterViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = Validators.getEmailError(email),
                errorMessage = null
            )
        }
    }

    fun updatePassword(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = Validators.getPasswordError(password),
                // Re-validate confirm nếu đã nhập
                confirmPasswordError = if (it.confirmPassword.isNotBlank())
                    Validators.getConfirmPasswordError(password, it.confirmPassword)
                else null,
                errorMessage = null
            )
        }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                confirmPasswordError = Validators.getConfirmPasswordError(
                    it.password, confirmPassword
                ),
                errorMessage = null
            )
        }
    }

    /** Kiểm tra form hợp lệ hoàn toàn */
    fun isFormValid(): Boolean {
        val state = _uiState.value
        return Validators.isValidEmail(state.email) &&
                Validators.isValidPassword(state.password) &&
                Validators.doPasswordsMatch(state.password, state.confirmPassword)
    }

    /**
     * Đăng ký tài khoản mới.
     */
    fun register() {
        val state = _uiState.value

        // Validate lần cuối trước khi gửi
        if (!isFormValid()) {
            _uiState.update {
                it.copy(
                    emailError = Validators.getEmailError(state.email),
                    passwordError = Validators.getPasswordError(state.password),
                    confirmPasswordError = Validators.getConfirmPasswordError(
                        state.password, state.confirmPassword
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.registerWithEmail(state.email, state.password)
                _uiState.update { it.copy(isLoading = false, isRegistered = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapRegisterError(e.message)
                    )
                }
            }
        }
    }

    /** Reset navigation state */
    fun onNavigated() {
        _uiState.update { it.copy(isRegistered = false) }
    }

    /** Map Firebase register errors sang tiếng Việt */
    private fun mapRegisterError(message: String?): String {
        return when {
            message == null -> "Đã xảy ra lỗi"
            message.contains("timeout") || message.contains("timed out") || message.contains("Timed out") ->
                "Quá thời gian kết nối. Vui lòng kiểm tra mạng hoặc đảm bảo bạn đã cấu hình vân tay SHA-1/SHA-256 trong Firebase Console."
            message.contains("Configuration not found") || message.contains("CONFIGURATION_NOT_FOUND") || message.contains("configuration not found") ->
                "Lỗi cấu hình Firebase: Bạn chưa bật phương thức đăng nhập Email/Password trong Firebase Console (Authentication > Sign-in method)."
            message.contains("email-already-in-use") -> "Email đã được sử dụng"
            message.contains("weak-password") -> "Mật khẩu quá yếu"
            message.contains("invalid-email") -> "Email không hợp lệ"
            message.contains("network") -> "Lỗi mạng. Kiểm tra kết nối Internet"
            else -> "Đăng ký thất bại: $message"
        }
    }
}
