package com.team.smartnutrition.analytics.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.smartnutrition.analytics.data.AnalyticsRepository
import com.team.smartnutrition.analytics.model.*
import com.team.smartnutrition.auth.model.User
import com.team.smartnutrition.meal.util.WeekUtils
import com.team.smartnutrition.analytics.util.MockDataSeeder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel : ViewModel() {
    private val repository = AnalyticsRepository()

    private val _weightEntries = MutableStateFlow<List<WeightChartEntry>>(emptyList())
    val weightEntries: StateFlow<List<WeightChartEntry>> = _weightEntries

    private val _calorieEntries = MutableStateFlow<List<DailyCalorieEntry>>(emptyList())
    val calorieEntries: StateFlow<List<DailyCalorieEntry>> = _calorieEntries

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile

    private val _timeRange = MutableStateFlow(TimeRange.WEEK)
    val timeRange: StateFlow<TimeRange> = _timeRange

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadData()
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
        loadWeightData()
    }

    fun refreshData() = loadData()

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val uid = repository.currentUid ?: throw Exception("Chưa đăng nhập")
                
                // Nạp dữ liệu ảo (Mock Data) lên Firestore cho User hiện tại
                MockDataSeeder.seedMockData(uid)
                
                // Load user profile
                _userProfile.value = repository.getUserProfile(uid)
                
                // Load weight history
                loadWeightData()
                
                // Load calorie data (tuần hiện tại)
                val weekId = WeekUtils.getCurrentWeekId()
                _calorieEntries.value = repository.getDailyCalories(uid, weekId)
                
            } catch (e: Exception) {
                _error.value = e.message ?: "Lỗi tải dữ liệu"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadWeightData() {
        viewModelScope.launch {
            try {
                val uid = repository.currentUid ?: return@launch
                val limit = when (_timeRange.value) {
                    TimeRange.WEEK -> 7L
                    TimeRange.MONTH -> 30L
                }
                _weightEntries.value = repository.getWeightHistory(uid, limit)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
