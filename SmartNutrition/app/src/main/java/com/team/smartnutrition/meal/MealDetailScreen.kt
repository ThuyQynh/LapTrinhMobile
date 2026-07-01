package com.team.smartnutrition.meal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.common.components.SmartTopBar
import com.team.smartnutrition.meal.model.Ingredient
import com.team.smartnutrition.meal.model.Meal
import com.team.smartnutrition.meal.viewmodel.MealPlanViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * MODULE 3 - Chi tiết bữa ăn
 *
 * Nhận dayIndex + mealType từ nav args.
 * Đọc Meal từ shared ViewModel (data load từ Firestore cache → gần instant).
 *
 * Layout:
 *   SmartTopBar ← back
 *   LazyColumn:
 *     - Tên món (Headline)
 *     - Chips: calo + protein
 *     - Section Nguyên liệu
 *     - Section Cách nấu (multi-line)
 */
@Composable
fun MealDetailScreen(
    navController: NavController,
    dayIndex: Int,
    mealType: String,
    viewModel: MealPlanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val meal = uiState.mealPlan?.days?.getOrNull(dayIndex)?.meals?.get(mealType)
    val dayLabelRaw = uiState.mealPlan?.days?.getOrNull(dayIndex)?.dayLabel ?: ""

    val isEnglish = stringResource(R.string.filter_all) == "All"
    val dayLabel = if (isEnglish) {
        when {
            dayLabelRaw.startsWith("Chủ") || dayLabelRaw.contains("Sunday", ignoreCase = true) -> "Sunday"
            dayLabelRaw.endsWith("2") || dayLabelRaw.contains("Monday", ignoreCase = true) -> "Monday"
            dayLabelRaw.endsWith("3") || dayLabelRaw.contains("Tuesday", ignoreCase = true) -> "Tuesday"
            dayLabelRaw.endsWith("4") || dayLabelRaw.contains("Wednesday", ignoreCase = true) -> "Wednesday"
            dayLabelRaw.endsWith("5") || dayLabelRaw.contains("Thursday", ignoreCase = true) -> "Thursday"
            dayLabelRaw.endsWith("6") || dayLabelRaw.contains("Friday", ignoreCase = true) -> "Friday"
            dayLabelRaw.endsWith("7") || dayLabelRaw.contains("Saturday", ignoreCase = true) -> "Saturday"
            else -> dayLabelRaw
        }
    } else {
        when {
            dayLabelRaw.startsWith("Chủ") || dayLabelRaw.contains("Sunday", ignoreCase = true) -> "Chủ Nhật"
            dayLabelRaw.endsWith("2") || dayLabelRaw.contains("Monday", ignoreCase = true) -> "Thứ 2"
            dayLabelRaw.endsWith("3") || dayLabelRaw.contains("Tuesday", ignoreCase = true) -> "Thứ 3"
            dayLabelRaw.endsWith("4") || dayLabelRaw.contains("Wednesday", ignoreCase = true) -> "Thứ 4"
            dayLabelRaw.endsWith("5") || dayLabelRaw.contains("Thursday", ignoreCase = true) -> "Thứ 5"
            dayLabelRaw.endsWith("6") || dayLabelRaw.contains("Friday", ignoreCase = true) -> "Thứ 6"
            dayLabelRaw.endsWith("7") || dayLabelRaw.contains("Saturday", ignoreCase = true) -> "Thứ 7"
            else -> dayLabelRaw
        }
    }

    val mealLabel = when (mealType.lowercase()) {
        "breakfast" -> stringResource(R.string.breakfast)
        "lunch" -> stringResource(R.string.lunch)
        "dinner" -> stringResource(R.string.dinner)
        else -> mealType
    }

    // Tự động gọi tải chi tiết món ăn khi màn hình được tạo
    LaunchedEffect(dayIndex, mealType) {
        viewModel.loadMealDetail(dayIndex, mealType)
    }

    var showEditDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                SmartTopBar(
                    title = "$mealLabel — $dayLabel",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        if (meal != null) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.edit)
                                )
                            }
                            IconButton(onClick = { viewModel.changeSpecificMeal(dayIndex, mealType) }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.swap_meal_desc)
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            when {
                uiState.isGeneratingDetail -> {
                    // Đang gọi AI sinh chi tiết công thức
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.ai_detail_loading),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                uiState.detailErrorMessage != null -> {
                    // Lỗi khi tải chi tiết
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.detailErrorMessage ?: stringResource(R.string.detail_error_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { viewModel.loadMealDetail(dayIndex, mealType) }
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                meal == null -> {
                    // Fallback khi không tìm thấy thông tin bữa ăn
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_meal_info),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    // Thành công -> hiển thị giao diện
                    MealDetailContent(
                        meal = meal,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }

        if (uiState.isGenerating) {
            GeneratingDialog(
                message = stringResource(
                    uiState.loadingMessageResId,
                    *uiState.loadingMessageArgs.toTypedArray()
                )
            )
        }

        if (showEditDialog && meal != null) {
            EditMealDialog(
                meal = meal,
                onDismiss = { showEditDialog = false },
                onSave = { name, calories, protein, ingredientsText, recipeText ->
                    val parsedIngredients = ingredientsText.lineSequence()
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val parts = line.split(":", limit = 2)
                            val ingName = parts.getOrNull(0)?.trim() ?: ""
                            val amount = parts.getOrNull(1)?.trim() ?: ""
                            Ingredient(ingName, amount)
                        }
                        .toList()
                    viewModel.updateMealManually(
                        dayIndex = dayIndex,
                        mealType = mealType,
                        name = name,
                        calories = calories,
                        protein = protein,
                        ingredients = parsedIngredients,
                        recipe = recipeText
                    )
                    showEditDialog = false
                }
            )
        }
    }
}
// DETAIL CONTENT
@Composable
private fun MealDetailContent(meal: Meal, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tên món ăn (Headline)
        item {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Nutrition chips: calo + protein
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NutritionChip(
                    label = "🔥 ${meal.totalCalories} kcal",
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
                NutritionChip(
                    label = "💪 ${meal.totalProtein}g protein",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }

        // Section: Nguyên liệu header
        item {
            Text(
                text = "📝 " + stringResource(R.string.ingredients_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Ingredient rows
        items(meal.ingredients) { ingredient ->
            IngredientRow(ingredient)
        }

        // Divider
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Section: Cách nấu header
        item {
            Text(
                text = "👨‍🍳 " + stringResource(R.string.recipe_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Recipe multi-line text
        item {
            Text(
                text = meal.recipe,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }

        // Bottom spacing
        item { Spacer(Modifier.height(24.dp)) }
    }
}
// SUB-COMPONENTS
@Composable
private fun NutritionChip(label: String, containerColor: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "• ${ingredient.name}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ingredient.amount,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMealDialog(
    meal: Meal,
    onDismiss: () -> Unit,
    onSave: (name: String, calories: Int, protein: Int, ingredientsText: String, recipe: String) -> Unit
) {
    var name by remember { mutableStateOf(meal.name) }
    var caloriesStr by remember { mutableStateOf(meal.totalCalories.toString()) }
    var proteinStr by remember { mutableStateOf(meal.totalProtein.toString()) }
    
    val initialIngredientsText = meal.ingredients.joinToString("\n") { 
        if (it.amount.isNotEmpty()) "${it.name}: ${it.amount}" else it.name 
    }
    var ingredientsText by remember { mutableStateOf(initialIngredientsText) }
    var recipe by remember { mutableStateOf(meal.recipe) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_meal_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.meal_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.calories_kcal)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proteinStr,
                        onValueChange = { proteinStr = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.protein_g)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = ingredientsText,
                    onValueChange = { ingredientsText = it },
                    label = { Text(stringResource(R.string.ingredients_edit_label)) },
                    placeholder = { Text(stringResource(R.string.ingredients_edit_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    minLines = 3
                )
                OutlinedTextField(
                    value = recipe,
                    onValueChange = { recipe = it },
                    label = { Text(stringResource(R.string.recipe_edit_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val calories = caloriesStr.toIntOrNull() ?: 0
                    val protein = proteinStr.toIntOrNull() ?: 0
                    onSave(name, calories, protein, ingredientsText, recipe)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

