package com.team.smartnutrition.meal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.smartnutrition.auth.data.UserRepository
import com.team.smartnutrition.meal.data.MealGeminiService
import com.team.smartnutrition.meal.data.MealRepository
import com.team.smartnutrition.meal.model.DayPlan
import com.team.smartnutrition.meal.model.Ingredient
import com.team.smartnutrition.meal.model.Meal
import com.team.smartnutrition.meal.model.MealPlan
import com.team.smartnutrition.meal.util.WeekUtils
import com.team.smartnutrition.pantry.data.PantryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.team.smartnutrition.R

/**
 * MEAL PLAN UI STATE
 */
data class MealPlanUiState(
    val mealPlan: MealPlan? = null,          // Plan hiện tại (null = chưa generate)
    val selectedDayIndex: Int = 0,            // Tab đang chọn (0-6)
    val isLoading: Boolean = true,            // Loading ban đầu (đọc Firestore)
    val isGenerating: Boolean = false,        // Đang gọi Gemini AI
    val loadingMessageResId: Int = R.string.loading, // ID câu chữ vui cho loading dialog
    val loadingMessageArgs: List<String> = emptyList(), // Tham số định dạng cho câu chữ loading
    val errorMessage: String? = null,         // Lỗi hiển thị cho user
    val isGeneratingDetail: Boolean = false,  // Đang gọi AI tải chi tiết nguyên liệu + cách nấu cho 1 món
    val detailErrorMessage: String? = null    // Lỗi khi tải chi tiết
)

/**
 * MEAL PLAN VIEW MODEL
 *
 * Shared ViewModel cho MealPlanWeekScreen + MealDetailScreen.
 *
 * Actions:
 *   init → loadCurrentPlan()
 *   generateMealPlan() → Gemini + Firestore save → update UI
 *   selectDay(index) → update selectedDayIndex
 *   clearError() → clear errorMessage
 *
 * Cross-module reads:
 *   UserRepository (Module 1) → calorieTarget, proteinTarget, goal
 *   PantryRepository (Module 2) → available pantry items
 */
class MealPlanViewModel : ViewModel() {

    private val mealGeminiService = MealGeminiService()
    private val mealRepository = MealRepository()
    private val userRepository = UserRepository()
    private val pantryRepository = PantryRepository()

    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    companion object {
        /** Danh sách ID câu loading vui vẻ, xoay vòng mỗi 2.5s */
        private val loadingMessageResIds = listOf(
            R.string.loading_msg_nutrition,
            R.string.loading_msg_ingredients,
            R.string.loading_msg_plan_7days,
            R.string.loading_msg_balance,
            R.string.loading_msg_almost_done
        )
    }

    init {
        loadCurrentPlan()
    }
    // PUBLIC ACTIONS
    /**
     * Khởi tạo hoặc tạo lại thực đơn cho ngày hiện tại đang chọn.
     * Nếu chưa có MealPlan (null) -> khởi tạo khung 7 ngày và tạo thực đơn cho ngày hôm nay.
     * Nếu đã có MealPlan -> tạo lại thực đơn cho ngày đang được chọn (selectedDayIndex).
     */
    fun generateMealPlan() {
        val currentPlan = _uiState.value.mealPlan
        if (currentPlan == null) {
            initWeeklyMealPlanSkeleton()
        } else {
            generateMealPlanForDay(_uiState.value.selectedDayIndex)
        }
    }

    /**
     * Khởi tạo khung thực đơn 7 ngày trống (Thứ 2 -> Chủ Nhật).
     * Tự động chọn ngày hôm nay trong tuần và gọi AI tạo thực đơn cho ngày đó.
     */
    private fun initWeeklyMealPlanSkeleton() {
        val uid = mealRepository.currentUid ?: return
        val weekId = WeekUtils.getCurrentWeekId()

        // Lấy ngày hiện tại trong tuần (1 = Thứ 2, 7 = Chủ Nhật)
        val today = java.time.LocalDate.now().dayOfWeek.value
        val todayIndex = (today - 1).coerceIn(0, 6)

        val emptyDays = WeekUtils.dayLabels.map { label ->
            DayPlan(dayLabel = label, meals = emptyMap())
        }

        val newPlan = MealPlan(
            weekId = weekId,
            days = emptyDays,
            calorieTarget = 2000,
            createdAt = com.google.firebase.Timestamp.now()
        )

        _uiState.update {
            it.copy(
                mealPlan = newPlan,
                selectedDayIndex = todayIndex,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Lưu skeleton vào local cache & Firestore
                mealRepository.saveMealPlan(uid, newPlan)
            } catch (e: Exception) {
                android.util.Log.e("MealPlanViewModel", "Lỗi lưu skeleton: ${e.message}", e)
            }
            // Gọi AI sinh thực đơn cho ngày hôm nay
            generateMealPlanForDay(todayIndex)
        }
    }

    /**
     * Gọi Gemini AI để sinh thực đơn cho một ngày cụ thể (dayIndex từ 0 đến 6).
     */
    fun generateMealPlanForDay(dayIndex: Int) {
        val uid = mealRepository.currentUid ?: return
        val currentPlan = _uiState.value.mealPlan ?: return
        val dayLabel = WeekUtils.dayLabels.getOrNull(dayIndex) ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    loadingMessageResId = R.string.loading_msg_setup_day,
                    loadingMessageArgs = listOf(dayLabel),
                    errorMessage = null
                )
            }

            // Xoay vòng loading messages mỗi 2.5s để UX sinh động
            val messageJob = launch {
                val dailyLoadingMessageResIds = listOf(
                    R.string.loading_msg_profile,
                    R.string.loading_msg_day_food,
                    R.string.loading_msg_balance,
                    R.string.loading_msg_almost_done
                )
                var msgIdx = 1
                while (true) {
                    delay(2500)
                    val resId = dailyLoadingMessageResIds[msgIdx % dailyLoadingMessageResIds.size]
                    val args = if (resId == R.string.loading_msg_day_food) listOf(dayLabel) else emptyList()
                    _uiState.update {
                        it.copy(
                            loadingMessageResId = resId,
                            loadingMessageArgs = args
                        )
                    }
                    msgIdx++
                }
            }

            try {
                // 1. Đọc user profile
                val user = userRepository.getUser(uid)
                    ?: throw Exception("Chưa có thông tin cá nhân. Vui lòng cập nhật profile trước.")

                // Fallback nếu chưa setup profile đầy đủ
                val safeUser = if (user.calorieTarget == 0) {
                    user.copy(
                        calorieTarget = 2000,
                        proteinTarget = 75,
                        carbTarget = 250,
                        fatTarget = 65
                    )
                } else user

                // 2. Đọc pantry items (có thể trống — không crash)
                val pantryItems = try {
                    pantryRepository.getAvailableItems(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }

                // 3. Gọi Gemini AI sinh thực đơn 1 ngày
                val generatedDayPlan = mealGeminiService.generateDailyMealPlan(safeUser, pantryItems, dayLabel)

                // 4. Cập nhật vào MealPlan hiện tại
                val updatedDays = currentPlan.days.toMutableList()
                if (dayIndex in updatedDays.indices) {
                    updatedDays[dayIndex] = generatedDayPlan
                } else {
                    while (updatedDays.size <= dayIndex) {
                        updatedDays.add(DayPlan(dayLabel = WeekUtils.dayLabels[updatedDays.size]))
                    }
                    updatedDays[dayIndex] = generatedDayPlan
                }

                // Cập nhật calorieTarget thực tế từ profile của user
                val updatedPlan = currentPlan.copy(
                    days = updatedDays,
                    calorieTarget = safeUser.calorieTarget,
                    totalCalories = updatedDays.sumOf { it.totalCalories }
                )

                // 5. Lưu plan mới
                mealRepository.saveMealPlan(uid, updatedPlan)

                _uiState.update {
                    it.copy(
                        mealPlan = updatedPlan,
                        isGenerating = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "Lỗi không xác định"
                    )
                }
            } finally {
                messageJob.cancel()
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    /**
     * Gọi Gemini AI đổi món ăn cho một bữa cụ thể (ví dụ: đổi bữa sáng của Thứ 2).
     */
    fun changeSpecificMeal(dayIndex: Int, mealType: String) {
        val uid = mealRepository.currentUid ?: return
        val currentPlan = _uiState.value.mealPlan ?: return
        val day = currentPlan.days.getOrNull(dayIndex) ?: return
        val meal = day.meals[mealType] ?: return
        val dayLabel = day.dayLabel

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    loadingMessageResId = R.string.loading_msg_swap,
                    loadingMessageArgs = emptyList(),
                    errorMessage = null
                )
            }

            try {
                // 1. Đọc user profile
                val user = userRepository.getUser(uid)
                    ?: throw Exception("Chưa có thông tin cá nhân. Vui lòng cập nhật profile trước.")

                val safeUser = if (user.calorieTarget == 0) {
                    user.copy(
                        calorieTarget = 2000,
                        proteinTarget = 75,
                        carbTarget = 250,
                        fatTarget = 65
                    )
                } else user

                // 2. Đọc pantry
                val pantryItems = try {
                    pantryRepository.getAvailableItems(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }

                // 3. Gọi Gemini thay thế món
                val newMeal = mealGeminiService.generateSingleMealReplacement(
                    user = safeUser,
                    pantryItems = pantryItems,
                    dayLabel = dayLabel,
                    mealType = mealType,
                    currentMealName = meal.name
                )

                // 4. Cập nhật vào MealPlan
                val updatedMeals = day.meals.toMutableMap().apply {
                    put(mealType, newMeal)
                }
                val updatedDay = day.copy(
                    meals = updatedMeals,
                    totalCalories = updatedMeals.values.sumOf { it.totalCalories },
                    totalProtein = updatedMeals.values.sumOf { it.totalProtein }
                )
                
                val updatedDays = currentPlan.days.toMutableList()
                if (dayIndex in updatedDays.indices) {
                    updatedDays[dayIndex] = updatedDay
                }

                val updatedPlan = currentPlan.copy(
                    days = updatedDays,
                    totalCalories = updatedDays.sumOf { it.totalCalories }
                )

                // 5. Lưu Firestore
                mealRepository.saveMealPlan(uid, updatedPlan)

                _uiState.update {
                    it.copy(
                        mealPlan = updatedPlan,
                        isGenerating = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "Lỗi đổi món ăn"
                    )
                }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    /**
     * Chọn ngày trong TabRow (index 0-6).
     */
    fun selectDay(index: Int) {
        _uiState.update { it.copy(selectedDayIndex = index) }
    }

    /**
     * Xóa error message sau khi user đã đọc.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Tải chi tiết nguyên liệu + cách nấu cho một bữa ăn cụ thể của một ngày.
     * Nếu món ăn đã có nguyên liệu -> bỏ qua (đã được lưu cache).
     * Nếu chưa có -> gọi Gemini, sau đó cập nhật đối tượng MealPlan và lưu đè lên Firestore.
     */
    fun loadMealDetail(dayIndex: Int, mealType: String) {
        val uid = mealRepository.currentUid ?: return

        viewModelScope.launch {
            // Đợi mealPlan được tải xong nếu đang trong trạng thái loading
            var elapsed = 0L
            while (_uiState.value.mealPlan == null && _uiState.value.isLoading && elapsed < 6000L) {
                delay(100)
                elapsed += 100
            }

            val currentPlan = _uiState.value.mealPlan ?: return@launch
            val day = currentPlan.days.getOrNull(dayIndex) ?: return@launch
            val meal = day.meals[mealType] ?: return@launch

            // Đã có chi tiết -> không gọi AI nữa
            if (meal.ingredients.isNotEmpty() && meal.recipe.isNotEmpty()) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    isGeneratingDetail = true,
                    detailErrorMessage = null
                )
            }

            try {
                // 1. Đọc user profile để lấy mục tiêu sức khỏe
                val user = userRepository.getUser(uid)
                val userGoal = user?.goal ?: "maintain"
                val goalVi = when (userGoal) {
                    "lose_weight" -> "Giảm cân"
                    "gain_muscle" -> "Tăng cơ"
                    else -> "Duy trì cân nặng"
                }

                // 2. Gọi Gemini để lấy công thức chi tiết
                val detailResult = mealGeminiService.generateMealDetail(
                    mealName = meal.name,
                    userGoal = goalVi,
                    calorieTarget = meal.totalCalories
                )

                // 3. Cập nhật đối tượng MealPlan mới
                val updatedIngredients = detailResult.ingredients.map {
                    Ingredient(name = it.name, amount = it.amount)
                }

                val updatedMeal = meal.copy(
                    ingredients = updatedIngredients,
                    recipe = detailResult.recipe
                )

                val updatedMeals = day.meals.toMutableMap().apply {
                    put(mealType, updatedMeal)
                }

                val updatedDay = day.copy(meals = updatedMeals)

                val updatedDays = currentPlan.days.toMutableList().apply {
                    set(dayIndex, updatedDay)
                }

                val updatedPlan = currentPlan.copy(days = updatedDays)

                // 4. Lưu đè lên local cache & Firestore (offline-first)
                mealRepository.saveMealPlan(uid, updatedPlan)

                // 5. Cập nhật UI State
                _uiState.update {
                    it.copy(
                        mealPlan = updatedPlan,
                        isGeneratingDetail = false,
                        detailErrorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingDetail = false,
                        detailErrorMessage = "Không thể tải công thức: ${e.message ?: "Lỗi kết nối"}"
                    )
                }
            }
        }
    }

    /**
     * Cập nhật món ăn bằng tay (không qua AI) và tự tính lại Calo + Protein toàn bộ thực đơn.
     */
    fun updateMealManually(
        dayIndex: Int,
        mealType: String,
        name: String,
        calories: Int,
        protein: Int,
        ingredients: List<Ingredient>,
        recipe: String
    ) {
        val uid = mealRepository.currentUid ?: return
        val currentPlan = _uiState.value.mealPlan ?: return
        val day = currentPlan.days.getOrNull(dayIndex) ?: return
        val meal = day.meals[mealType] ?: return

        viewModelScope.launch {
            try {
                val updatedMeal = meal.copy(
                    name = name,
                    totalCalories = calories,
                    totalProtein = protein,
                    ingredients = ingredients,
                    recipe = recipe
                )

                val updatedMeals = day.meals.toMutableMap().apply {
                    put(mealType, updatedMeal)
                }

                val updatedDay = day.copy(
                    meals = updatedMeals,
                    totalCalories = updatedMeals.values.sumOf { it.totalCalories },
                    totalProtein = updatedMeals.values.sumOf { it.totalProtein }
                )

                val updatedDays = currentPlan.days.toMutableList().apply {
                    set(dayIndex, updatedDay)
                }

                val updatedPlan = currentPlan.copy(
                    days = updatedDays,
                    totalCalories = updatedDays.sumOf { it.totalCalories }
                )

                // Lưu vào database
                mealRepository.saveMealPlan(uid, updatedPlan)

                // Cập nhật UI State
                _uiState.update {
                    it.copy(
                        mealPlan = updatedPlan,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Lỗi khi lưu món ăn: ${e.message}")
                }
            }
        }
    }
    // Hàm hỗ trợ PROPERTIES
    /** DayPlan đang được chọn (cho MealPlanWeekScreen) */
    val selectedDay: DayPlan?
        get() {
            val state = _uiState.value
            return state.mealPlan?.days?.getOrNull(state.selectedDayIndex)
        }

    /**
     * Lấy Meal cụ thể theo dayIndex + mealType.
     * Dùng cho MealDetailScreen.
     */
    fun getMeal(dayIndex: Int, mealType: String): Meal? {
        return _uiState.value.mealPlan?.days?.getOrNull(dayIndex)?.meals?.get(mealType)
    }
    // PRIVATE
    /**
     * Load meal plan tuần hiện tại từ Firestore.
     * Nếu không có plan tuần này → thử load plan gần nhất.
     * Nếu không có plan nào → hiện empty state.
     */
    private fun loadCurrentPlan() {
        val uid = mealRepository.currentUid ?: run {
            // User chưa login → bỏ loading, hiện empty state
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val weekId = WeekUtils.getCurrentWeekId()

                // Thử load plan tuần hiện tại
                var plan = mealRepository.getMealPlan(uid, weekId)

                // Fallback: load plan gần nhất nếu tuần này chưa có
                if (plan == null) {
                    plan = mealRepository.getLatestMealPlan(uid)
                }

                _uiState.update {
                    it.copy(
                        mealPlan = plan,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không thể tải thực đơn: ${e.message}"
                    )
                }
            }
        }
    }
}
