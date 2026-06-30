package com.team.smartnutrition.auth.viewmodel

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.team.smartnutrition.auth.data.UserRepository
import com.team.smartnutrition.auth.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════
 * LOGIN VIEW MODEL
 * ═══════════════════════════════════════════
 *
 * Xử lý:
 * - Đăng nhập Email/Password
 * - Đăng nhập Google (Credential Manager)
 * - Kiểm tra user đã có profile chưa → navigate phù hợp
 */

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val navigateTo: LoginDestination? = null
)

enum class LoginDestination {
    HOME,           // Đã có profile → vào Home
    PROFILE_SETUP   // Chưa có profile → setup lần đầu
}

class LoginViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    /** Kiểm tra form hợp lệ (dùng cho enable/disable button) */
    fun isFormValid(): Boolean {
        val state = _uiState.value
        return Validators.isValidEmail(state.email) && state.password.isNotBlank()
    }

    /**
     * Đăng nhập bằng Email/Password.
     */
    fun signInWithEmail() {
        val state = _uiState.value
        if (!Validators.isValidEmail(state.email)) {
            _uiState.update { it.copy(errorMessage = "Email không hợp lệ") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = repository.signInWithEmail(state.email, state.password)
                checkProfileAndNavigate(user.uid)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseError(e.message)
                    )
                }
            }
        }
    }

    /**
     * Đăng nhập bằng Google Credential Manager.
     * Cần Activity context để hiện dialog chọn tài khoản.
     */
    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // 1. Tạo Google ID option
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getWebClientId(activity))
                    .build()

                // 2. Tạo credential request
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // 3. Gọi Credential Manager
                val credentialManager = CredentialManager.create(activity)
                val result: GetCredentialResponse =
                    credentialManager.getCredential(activity, request)

                // 4. Lấy Google ID Token
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(result.credential.data)
                val idToken = googleIdTokenCredential.idToken

                // 5. Đăng nhập Firebase bằng credential
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val user = repository.signInWithCredential(firebaseCredential)

                checkProfileAndNavigate(user.uid)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Đăng nhập Google thất bại: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Kiểm tra user đã có profile trên Firestore chưa.
     * - Có → navigate HOME
     * - Chưa → navigate PROFILE_SETUP
     */
    private suspend fun checkProfileAndNavigate(uid: String) {
        try {
            val hasProfile = repository.hasProfile(uid)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    navigateTo = if (hasProfile) LoginDestination.HOME
                    else LoginDestination.PROFILE_SETUP
                )
            }
        } catch (e: Exception) {
            // Firestore offline → default navigate to HOME
            _uiState.update {
                it.copy(isLoading = false, navigateTo = LoginDestination.HOME)
            }
        }
    }

    /** Reset navigation state sau khi đã navigate */
    fun onNavigated() {
        _uiState.update { it.copy(navigateTo = null) }
    }

    /** Clear error message */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Lấy Web Client ID từ google-services.json (qua strings.xml).
     * Firebase tự generate string resource "default_web_client_id".
     */
    private fun getWebClientId(activity: Activity): String {
        // 1. Thử lấy trực tiếp thông qua R class của package chính (com.team.smartnutrition)
        // Cách này an toàn nhất vì tránh được lỗi khi chạy debug build có applicationIdSuffix
        try {
            val resId = com.team.smartnutrition.R.string.default_web_client_id
            val clientId = activity.getString(resId)
            if (clientId.isNotEmpty()) {
                return clientId
            }
        } catch (e: Throwable) {
            // Bỏ qua nếu có lỗi biên dịch hoặc runtime
        }

        // 2. Thử tìm qua Identifier với package name cứng của ứng dụng
        val hardcodedPackageResId = activity.resources.getIdentifier(
            "default_web_client_id", "string", "com.team.smartnutrition"
        )
        if (hardcodedPackageResId != 0) {
            return activity.getString(hardcodedPackageResId)
        }

        // 3. Thử tìm qua Identifier với activity.packageName (cách cũ)
        val activityPackageResId = activity.resources.getIdentifier(
            "default_web_client_id", "string", activity.packageName
        )
        if (activityPackageResId != 0) {
            return activity.getString(activityPackageResId)
        }

        // 4. Nếu tất cả đều thất bại, fallback về Web Client ID lấy từ google-services.json hiện tại
        return "403911937731-p004v6umhf2fgertf1mscq325cdprsg9.apps.googleusercontent.com"
    }

    /** Map Firebase error codes sang tiếng Việt */
    private fun mapFirebaseError(message: String?): String {
        return when {
            message == null -> "Đã xảy ra lỗi"
            message.contains("timeout") || message.contains("timed out") || message.contains("Timed out") ->
                "Quá thời gian kết nối. Vui lòng kiểm tra mạng hoặc đảm bảo bạn đã cấu hình vân tay SHA-1/SHA-256 trong Firebase Console."
            message.contains("Configuration not found") || message.contains("CONFIGURATION_NOT_FOUND") || message.contains("configuration not found") ->
                "Lỗi cấu hình Firebase: Bạn chưa bật phương thức đăng nhập Email/Password trong Firebase Console (Authentication > Sign-in method)."
            message.contains("INVALID_LOGIN_CREDENTIALS") ||
            message.contains("invalid-credential") -> "Email hoặc mật khẩu không đúng"
            message.contains("user-not-found") -> "Tài khoản không tồn tại"
            message.contains("wrong-password") -> "Mật khẩu không đúng"
            message.contains("too-many-requests") -> "Quá nhiều lần thử. Vui lòng thử lại sau"
            message.contains("network") -> "Lỗi mạng. Kiểm tra kết nối Internet"
            message.contains("user-disabled") -> "Tài khoản đã bị vô hiệu hóa"
            else -> "Đăng nhập thất bại: $message"
        }
    }
}
