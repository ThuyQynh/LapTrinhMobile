package com.team.smartnutrition.auth

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import com.team.smartnutrition.R
import com.team.smartnutrition.auth.viewmodel.*
import com.team.smartnutrition.navigation.Screen
import java.time.Year

/**
 * MODULE 1 - TV1: THIẾT LẬP THỂ TRẠNG
 *
 * Multi-step wizard 3 bước:
 * Step 1: Giới tính + Năm sinh + Tên hiển thị
 * Step 2: Chiều cao + Cân nặng (Slider + Input)
 * Step 3: Mục tiêu + Mức vận động + Kết quả tính toán
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    navController: NavController,
    viewModel: ProfileSetupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle navigation after save
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
            viewModel.onNavigated()
        }
    }

    // Handle error
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.profile_setup_step_title, uiState.currentStep, uiState.totalSteps))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Progress Bar
            LinearProgressIndicator(
                progress = { uiState.currentStep.toFloat() / uiState.totalSteps },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Step Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> width } + fadeOut()
                        }
                    },
                    label = "stepTransition"
                ) { step ->
                    when (step) {
                        1 -> Step1Content(uiState, viewModel)
                        2 -> Step2Content(uiState, viewModel)
                        3 -> Step3Content(uiState, viewModel)
                    }
                }
            }

            // Bottom Buttons
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Back button
                    if (uiState.currentStep > 1) {
                        OutlinedButton(
                            onClick = { viewModel.previousStep() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.back))
                        }
                    }

                    // Next / Save button
                    Button(
                        onClick = {
                            if (uiState.currentStep < uiState.totalSteps) {
                                viewModel.nextStep()
                            } else {
                                viewModel.saveProfile()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = viewModel.isCurrentStepValid() && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else if (uiState.currentStep < uiState.totalSteps) {
                            Text(stringResource(R.string.next))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.finish))
                        }
                    }
                }
            }
        }
    }
}
// STEP 1: Giới tính + Năm sinh + Tên
@Composable
private fun Step1Content(
    uiState: ProfileSetupUiState,
    viewModel: ProfileSetupViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Title
        Text(
            text = stringResource(R.string.basic_info_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = stringResource(R.string.basic_info_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Tên hiển thị
        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = { viewModel.updateDisplayName(it) },
            label = { Text(stringResource(R.string.display_name_label)) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Giới tính
        Text(
            text = stringResource(R.string.gender_label),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GenderCard(
                emoji = "🚹",
                label = stringResource(R.string.gender_male),
                isSelected = uiState.gender == "male",
                onClick = { viewModel.updateGender("male") },
                modifier = Modifier.weight(1f)
            )
            GenderCard(
                emoji = "🚺",
                label = stringResource(R.string.gender_female),
                isSelected = uiState.gender == "female",
                onClick = { viewModel.updateGender("female") },
                modifier = Modifier.weight(1f)
            )
        }

        // Năm sinh
        Text(
            text = stringResource(R.string.birth_year_display, uiState.birthYear, Year.now().value - uiState.birthYear),
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = uiState.birthYear.toFloat(),
            onValueChange = { viewModel.updateBirthYear(it.toInt()) },
            valueRange = 1940f..2015f,
            steps = 74,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1940", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2015", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        var birthYearInputText by remember { mutableStateOf(uiState.birthYear.toString()) }
        LaunchedEffect(uiState.birthYear) {
            if (birthYearInputText.toIntOrNull() != uiState.birthYear) {
                birthYearInputText = uiState.birthYear.toString()
            }
        }
        
        OutlinedTextField(
            value = birthYearInputText,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                    birthYearInputText = newValue
                    newValue.toIntOrNull()?.let { year ->
                        if (year in 1940..2015) {
                            viewModel.updateBirthYear(year)
                        }
                    }
                }
            },
            label = { Text(stringResource(R.string.birth_year_label, uiState.birthYear).substringBefore(":") + " (1940 - 2015)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun GenderCard(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
// STEP 2: Chiều cao + Cân nặng
@Composable
private fun Step2Content(
    uiState: ProfileSetupUiState,
    viewModel: ProfileSetupViewModel
) {
    var heightInputText by remember { mutableStateOf(uiState.heightCm.toString()) }
    LaunchedEffect(uiState.heightCm) {
        if (heightInputText.toIntOrNull() != uiState.heightCm) {
            heightInputText = uiState.heightCm.toString()
        }
    }

    var weightInputText by remember { mutableStateOf("%.1f".format(uiState.weightKg)) }
    LaunchedEffect(uiState.weightKg) {
        val currentFloat = weightInputText.toDoubleOrNull() ?: 0.0
        if (Math.abs(currentFloat - uiState.weightKg) > 0.05) {
            weightInputText = "%.1f".format(uiState.weightKg)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = stringResource(R.string.body_metrics_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = stringResource(R.string.body_metrics_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Chiều cao
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📏 " + stringResource(R.string.height_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${uiState.heightCm} cm",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = uiState.heightCm.toFloat(),
                    onValueChange = { viewModel.updateHeightCm(it.toInt()) },
                    valueRange = 100f..250f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100 cm", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("250 cm", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = heightInputText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            heightInputText = newValue
                            newValue.toIntOrNull()?.let { height ->
                                if (height in 100..250) {
                                    viewModel.updateHeightCm(height)
                                }
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.enter_height_manual)) },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        // Cân nặng
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚖️ " + stringResource(R.string.weight_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${"%.1f".format(uiState.weightKg)} kg",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = uiState.weightKg.toFloat(),
                    onValueChange = {
                        viewModel.updateWeightKg(
                            (it * 10).toInt() / 10.0  // Round to 0.1
                        )
                    },
                    valueRange = 30f..200f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("30 kg", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("200 kg", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightInputText,
                    onValueChange = { newValue ->
                        val normalized = newValue.replace(',', '.')
                        if (normalized.isEmpty() || normalized.toDoubleOrNull() != null || normalized == "." || normalized.endsWith(".")) {
                            weightInputText = newValue
                            normalized.toDoubleOrNull()?.let { weight ->
                                if (weight >= 30.0 && weight <= 200.0) {
                                    viewModel.updateWeightKg(weight)
                                }
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.enter_weight_manual)) },
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        // Preview BMI
        val bmi = com.team.smartnutrition.auth.util.HealthCalculator.calculateBmi(
            uiState.weightKg, uiState.heightCm
        )
        val bmiCategory = com.team.smartnutrition.auth.util.HealthCalculator.getBmiCategory(bmi)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.current_bmi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        bmiCategory,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "${"%.1f".format(bmi)}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
// STEP 3: Mục tiêu + Vận động + Kết quả
@Composable
private fun Step3Content(
    uiState: ProfileSetupUiState,
    viewModel: ProfileSetupViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(R.string.goal_activity_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )

        // Mục tiêu sức khỏe
        Text(stringResource(R.string.goal_label), style = MaterialTheme.typography.titleMedium)
        goalOptions.forEach { option ->
            val labelText = when (option.value) {
                "lose_weight" -> stringResource(R.string.goal_lose)
                "gain_muscle" -> stringResource(R.string.goal_gain)
                else -> stringResource(R.string.goal_maintain)
            }
            val descText = when (option.value) {
                "lose_weight" -> stringResource(R.string.goal_lose_desc)
                "gain_muscle" -> stringResource(R.string.goal_gain_desc)
                else -> stringResource(R.string.goal_maintain_desc)
            }
            Card(
                onClick = { viewModel.updateGoal(option.value) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.goal == option.value)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (uiState.goal == option.value)
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            labelText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            descText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.goal == option.value) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Mức độ vận động
        Text(stringResource(R.string.activity_label), style = MaterialTheme.typography.titleMedium)
        activityLevelOptions.forEach { option ->
            val labelText = when (option.value) {
                1.2 -> stringResource(R.string.activity_sedentary)
                1.375 -> stringResource(R.string.activity_light)
                1.55 -> stringResource(R.string.activity_moderate)
                1.725 -> stringResource(R.string.activity_very)
                else -> stringResource(R.string.activity_extreme)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = uiState.activityLevel == option.value,
                    onClick = { viewModel.updateActivityLevel(option.value) }
                )
                Text(
                    text = "${option.emoji} $labelText",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Kết quả tính toán
        uiState.metrics?.let { metrics ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.your_metrics_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // BMI & BMR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem("BMI", "${"%.1f".format(metrics.bmi)}", metrics.bmiCategory)
                        MetricItem("BMR", "${"%.0f".format(metrics.bmr)}", stringResource(R.string.kcal))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // TDEE & Calorie Target
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem("TDEE", "${"%.0f".format(metrics.tdee)}", stringResource(R.string.kcal_per_day))
                        MetricItem(stringResource(R.string.goal_label), "${metrics.calorieTarget}", stringResource(R.string.kcal_per_day))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Macros
                    Text(
                        stringResource(R.string.daily_macros),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem("Protein", "${metrics.proteinTarget}g", "")
                        MetricItem("Carbs", "${metrics.carbTarget}g", "")
                        MetricItem("Fat", "${metrics.fatTarget}g", "")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        if (unit.isNotBlank()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}
