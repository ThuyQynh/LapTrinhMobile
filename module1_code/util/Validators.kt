package com.team.smartnutrition.auth.util

import android.util.Patterns

/**
 * ═══════════════════════════════════════════
 * VALIDATION UTILS - Module 1
 * ═══════════════════════════════════════════
 *
 * Quy tắc:
 * - Email: Patterns.EMAIL_ADDRESS regex chuẩn Android
 * - Password: ≥6 ký tự + 1 chữ hoa + 1 chữ số
 * - Confirm: phải trùng khớp hoàn toàn
 */
object Validators {

    /**
     * Validate email theo format chuẩn Android.
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Validate password theo quy tắc bảo mật.
     * ≥6 ký tự, ít nhất 1 chữ hoa, ít nhất 1 chữ số.
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= 6 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isDigit() }
    }

    /**
     * Kiểm tra confirm password trùng khớp.
     */
    fun doPasswordsMatch(password: String, confirmPassword: String): Boolean {
        return password.isNotEmpty() && password == confirmPassword
    }

    /**
     * Lấy thông báo lỗi email (null = hợp lệ).
     */
    fun getEmailError(email: String): String? {
        return when {
            email.isBlank() -> null  // Chưa nhập, không hiện lỗi
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không hợp lệ"
            else -> null
        }
    }

    /**
     * Lấy thông báo lỗi password (null = hợp lệ).
     * Trả về lỗi cụ thể nhất cho UX tốt.
     */
    fun getPasswordError(password: String): String? {
        return when {
            password.isBlank() -> null  // Chưa nhập
            password.length < 6 -> "Mật khẩu phải có ít nhất 6 ký tự"
            !password.any { it.isUpperCase() } -> "Cần ít nhất 1 chữ cái viết hoa"
            !password.any { it.isDigit() } -> "Cần ít nhất 1 chữ số"
            else -> null
        }
    }

    /**
     * Lấy thông báo lỗi confirm password (null = hợp lệ).
     */
    fun getConfirmPasswordError(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isBlank() -> null  // Chưa nhập
            confirmPassword != password -> "Mật khẩu không khớp"
            else -> null
        }
    }
}
