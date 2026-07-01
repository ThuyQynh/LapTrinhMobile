package com.team.smartnutrition.analytics.viewmodel

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _currentLanguage = MutableStateFlow(prefs.getString("language", "vi") ?: "vi")
    val currentLanguage: StateFlow<String> = _currentLanguage

    private val _isOfflineEnabled = MutableStateFlow(prefs.getBoolean("offline_sync", true))
    val isOfflineEnabled: StateFlow<Boolean> = _isOfflineEnabled

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (newValue) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun setLanguage(langCode: String) {
        _currentLanguage.value = langCode
        prefs.edit().putString("language", langCode).apply()
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
        )
    }

    fun toggleOfflineSync() {
        val newValue = !_isOfflineEnabled.value
        _isOfflineEnabled.value = newValue
        prefs.edit().putBoolean("offline_sync", newValue).apply()
        // Note: Firestore persistence setup is done once at App initialization
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}
