package com.team.smartnutrition

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class - khởi tạo Firebase và cấu hình chung.
 * File này KHÔNG CẦN SỬA trừ khi thêm thư viện mới cần init.
 */
class SmartNutritionApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Cấu hình lưu trữ ngoại tuyến Firestore (Offline Persistence)
        // Cho phép app hoạt động khi mất mạng
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Khôi phục chế độ sáng/tối từ SharedPreferences
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Khôi phục ngôn ngữ từ SharedPreferences
        val langCode = prefs.getString("language", "vi") ?: "vi"
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
