package com.team.smartnutrition.meal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.common.components.ErrorCard
import com.team.smartnutrition.common.components.GradientButton
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.meal.model.DayPlan
import com.team.smartnutrition.meal.model.Meal
import com.team.smartnutrition.meal.util.WeekUtils
import com.team.smartnutrition.meal.viewmodel.MealPlanUiState
import com.team.smartnutrition.meal.viewmodel.MealPlanViewModel
import com.team.smartnutrition.navigation.Screen

/**
 * Module 3 - Thực đơn AI - Màn hình tổng quan tuần
 *
 * States:
 *   isLoading → LoadingScreen
 *   mealPlan == null → EmptyMealPlanContent (CTA generate)
 *   mealPlan != null → MealPlanContent (TabRow + Cards)
 *
 * Overlays:
 *   isGenerating → GeneratingDialog (animated AI loading)
 *   errorMessage != null → ErrorCard (bottom)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanWeekScreen(
    navController: NavController,
    viewModel: MealPlanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> LoadingScreen(stringResource(R.string.loading_meal_plan))
            uiState.mealPlan == null -> EmptyMealPlanContent(
                onGenerateClick = { viewModel.generateMealPlan() }
            )
            else -> MealPlanContent(
                uiState = uiState,
                onDaySelected = { viewModel.selectDay(it) },
                onMealClick = { dayIndex, mealType ->
                    navController.navigate(Screen.MealDetail.createRoute(dayIndex, mealType))
                },
                onRegenerateClick = { viewModel.generateMealPlan() },
                onGenerateDayClick = { dayIndex -> viewModel.generateMealPlanForDay(dayIndex) },
                onSwapMealClick = { dayIndex, mealType -> viewModel.changeSpecificMeal(dayIndex, mealType) }
            )
        }

        // Màu báo lỗi (Error) snackbar / card (bottom)
        uiState.errorMessage?.let { error ->
            ErrorCard(
                message = error,
                onRetry = {
                    viewModel.clearError()
                    viewModel.generateMealPlan()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Full-screen loading dialog overlay khi đang gọi Gemini AI
        if (uiState.isGenerating) {
            GeneratingDialog(
                message = stringResource(
                    uiState.loadingMessageResId,
                    *uiState.loadingMessageArgs.toTypedArray()
                )
            )
        }
    }
}
// Trạng thái trống - Chưa có thực đơn
@Composable
private fun EmptyMealPlanContent(onGenerateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.RestaurantMenu,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_meal_plan),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.meal_plan_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        GradientButton(
            text = stringResource(R.string.generate_meal_plan_btn),
            onClick = onGenerateClick,
            icon = Icons.Filled.AutoAwesome
        )
    }
}
// Nội dung chính - Đã có thực đơn
@Composable
private fun MealPlanContent(
    uiState: MealPlanUiState,
    onDaySelected: (Int) -> Unit,
    onMealClick: (dayIndex: Int, mealType: String) -> Unit,
    onRegenerateClick: () -> Unit,
    onGenerateDayClick: (Int) -> Unit,
    onSwapMealClick: (Int, String) -> Unit
) {
    val plan = uiState.mealPlan ?: return
    val selectedDay = plan.days.getOrNull(uiState.selectedDayIndex) ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        // Chỉ hiện nút regenerate khi ngày hiện tại đã có thực đơn
        if (selectedDay.meals.isNotEmpty()) {
            GradientButton(
                text = stringResource(R.string.regenerate_meal_plan_btn),
                onClick = onRegenerateClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // Tab row chọn ngày (7 tab rút gọn)
        DayTabRow(
            days = plan.days,
            selectedIndex = uiState.selectedDayIndex,
            onDaySelected = onDaySelected
        )

        // Calorie + protein summary bar
        CalorieSummaryBar(
            consumed = selectedDay.totalCalories,
            target = plan.calorieTarget,
            protein = selectedDay.totalProtein
        )

        if (selectedDay.meals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.daily_plan_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.daily_plan_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    GradientButton(
                        text = stringResource(R.string.generate_daily_plan_btn),
                        onClick = { onGenerateDayClick(uiState.selectedDayIndex) },
                        icon = Icons.Filled.AutoAwesome,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // 3 meal cards (Sáng / Trưa / Tối)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(WeekUtils.mealTypeOrder) { mealType ->
                    val meal = selectedDay.meals[mealType]
                    if (meal != null) {
                        MealCard(
                            mealType = mealType,
                            meal = meal,
                            onClick = { onMealClick(uiState.selectedDayIndex, mealType) },
                            onSwapClick = { onSwapMealClick(uiState.selectedDayIndex, mealType) }
                        )
                    }
                }
                // Bottom spacing
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
// Thanh chọn ngày trong tuần
@Composable
private fun DayTabRow(
    days: List<DayPlan>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp
    ) {
        days.forEachIndexed { index, day ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onDaySelected(index) },
                text = {
                    val isEnglish = stringResource(R.string.filter_all) == "All"
                    val shortLabel = if (isEnglish) {
                        when {
                            day.dayLabel.startsWith("Chủ") || day.dayLabel.contains("Sunday", ignoreCase = true) -> "Sun"
                            day.dayLabel.endsWith("2") || day.dayLabel.contains("Monday", ignoreCase = true) -> "Mon"
                            day.dayLabel.endsWith("3") || day.dayLabel.contains("Tuesday", ignoreCase = true) -> "Tue"
                            day.dayLabel.endsWith("4") || day.dayLabel.contains("Wednesday", ignoreCase = true) -> "Wed"
                            day.dayLabel.endsWith("5") || day.dayLabel.contains("Thursday", ignoreCase = true) -> "Thu"
                            day.dayLabel.endsWith("6") || day.dayLabel.contains("Friday", ignoreCase = true) -> "Fri"
                            day.dayLabel.endsWith("7") || day.dayLabel.contains("Saturday", ignoreCase = true) -> "Sat"
                            else -> day.dayLabel.take(3)
                        }
                    } else {
                        when {
                            day.dayLabel.startsWith("Chủ") || day.dayLabel.contains("Sunday", ignoreCase = true) || day.dayLabel.contains("CN", ignoreCase = true) -> "CN"
                            day.dayLabel.endsWith("2") || day.dayLabel.contains("Hai", ignoreCase = true) || day.dayLabel.contains("Monday", ignoreCase = true) -> "T2"
                            day.dayLabel.endsWith("3") || day.dayLabel.contains("Ba", ignoreCase = true) || day.dayLabel.contains("Tuesday", ignoreCase = true) -> "T3"
                            day.dayLabel.endsWith("4") || day.dayLabel.contains("Tư", ignoreCase = true) || day.dayLabel.contains("Wednesday", ignoreCase = true) -> "T4"
                            day.dayLabel.endsWith("5") || day.dayLabel.contains("Năm", ignoreCase = true) || day.dayLabel.contains("Thursday", ignoreCase = true) || day.dayLabel.contains("T5") -> "T5"
                            day.dayLabel.endsWith("6") || day.dayLabel.contains("Sáu", ignoreCase = true) || day.dayLabel.contains("Friday", ignoreCase = true) -> "T6"
                            day.dayLabel.endsWith("7") || day.dayLabel.contains("Bảy", ignoreCase = true) || day.dayLabel.contains("Saturday", ignoreCase = true) -> "T7"
                            else -> "T${day.dayLabel.filter { it.isDigit() }.ifEmpty { day.dayLabel.lastOrNull()?.toString() ?: "" }}"
                        }
                    }
                    Text(shortLabel)
                }
            )
        }
    }
}
// Thanh tóm tắt lượng Calo
@Composable
private fun CalorieSummaryBar(consumed: Int, target: Int, protein: Int) {
    val rawProgress = if (target > 0) consumed.toFloat() / target else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1.2f),
        animationSpec = tween(600),
        label = "calorie_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔥 $consumed / $target kcal",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "💪 ${protein}g protein",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (rawProgress > 1f) MaterialTheme.colorScheme.error
                         else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
// Thẻ thông tin bữa ăn
@Composable
private fun MealCard(
    mealType: String,
    meal: Meal,
    onClick: () -> Unit,
    onSwapClick: () -> Unit
) {
    val label = when (mealType.lowercase()) {
        "breakfast" -> stringResource(R.string.breakfast)
        "lunch" -> stringResource(R.string.lunch)
        "dinner" -> stringResource(R.string.dinner)
        else -> mealType
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "🔥 ${meal.totalCalories} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "💪 ${meal.totalProtein}g protein",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onSwapClick
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.swap_meal_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
// Hộp thoại đang tạo thực đơn bằng AI
@Composable
private fun GeneratingDialog(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ai_generating_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
