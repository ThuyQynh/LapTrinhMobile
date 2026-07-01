package com.team.smartnutrition.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.auth.util.HealthCalculator
import com.team.smartnutrition.auth.viewmodel.ProfileViewUiState
import com.team.smartnutrition.auth.viewmodel.ProfileViewViewModel
import com.team.smartnutrition.auth.viewmodel.activityLevelOptions
import com.team.smartnutrition.auth.viewmodel.goalOptions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.team.smartnutrition.common.components.ErrorCard
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileViewScreen(
    navController: NavController,
    viewModel: ProfileViewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
            viewModel.onSignedOut()
        }
    }

    when {
        uiState.isLoading -> LoadingScreen()
        uiState.errorMessage != null && uiState.user == null -> {
            ErrorCard(message = uiState.errorMessage ?: stringResource(R.string.error_generic), onRetry = { viewModel.loadProfile() })
        }
        uiState.isEditing -> EditProfileContent(uiState, viewModel)
        else -> ViewProfileContent(uiState, viewModel, navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewProfileContent(
    uiState: ProfileViewUiState,
    viewModel: ProfileViewViewModel,
    navController: NavController
) {
    val user = uiState.user ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = { viewModel.startEditing() }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.btn_edit))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(user.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(user.displayName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))

            // Info Card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(12.dp))
                    InfoRow(stringResource(R.string.gender_label), if (user.gender == "male") "🚹 " + stringResource(R.string.gender_male) else "🚺 " + stringResource(R.string.gender_female))
                    InfoRow(stringResource(R.string.age_label), "${HealthCalculator.calculateAge(user.birthYear)} (${user.birthYear})")
                    InfoRow(stringResource(R.string.height_label), "${user.heightCm} cm")
                    InfoRow(stringResource(R.string.weight_label), "${"%.1f".format(user.weightKg)} kg")
                    
                    val goalLabel = when(user.goal) {
                        "lose_weight" -> "🔥 " + stringResource(R.string.goal_lose)
                        "gain_muscle" -> "💪 " + stringResource(R.string.goal_gain)
                        else -> "⚖️ " + stringResource(R.string.goal_maintain)
                    }
                    InfoRow(stringResource(R.string.goal_label), goalLabel)
                    
                    val activityLabel = when(user.activityLevel) {
                        1.2 -> "🚶 " + stringResource(R.string.activity_sedentary)
                        1.375 -> "🏃 " + stringResource(R.string.activity_light)
                        1.55 -> "🏋️ " + stringResource(R.string.activity_moderate)
                        1.725 -> "🏅 " + stringResource(R.string.activity_very)
                        else -> "🔥 " + stringResource(R.string.activity_extreme)
                    }
                    InfoRow(stringResource(R.string.activity_label), activityLabel)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Health Metrics
            Text("📊 " + stringResource(R.string.health_indices), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("BMI", "${"%.1f".format(user.bmi)}", HealthCalculator.getBmiCategory(user.bmi), Modifier.weight(1f))
                MetricCard("BMR", "${"%.0f".format(user.bmr)}", "kcal", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("TDEE", "${"%.0f".format(user.tdee)}", "kcal/ngày", Modifier.weight(1f))
                MetricCard(stringResource(R.string.goal_label), "${user.calorieTarget}", "kcal/ngày", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            // Macros
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.daily_macros), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(12.dp))
                    MacroBar("Protein", user.proteinTarget, 200, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    MacroBar("Carbs", user.carbTarget, 400, MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    MacroBar("Fat", user.fatTarget, 120, MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Actions
            OutlinedButton(onClick = { navController.navigate(Screen.WeightLog.route) },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Filled.MonitorWeight, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.weight_log_btn))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { navController.navigate(Screen.Settings.route) },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Filled.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_title))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileContent(uiState: ProfileViewUiState, viewModel: ProfileViewViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) { uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) } }

    var birthYearInputText by remember { mutableStateOf(uiState.editBirthYear.toString()) }
    LaunchedEffect(uiState.editBirthYear) {
        if (birthYearInputText.toIntOrNull() != uiState.editBirthYear) {
            birthYearInputText = uiState.editBirthYear.toString()
        }
    }

    var heightInputText by remember { mutableStateOf(uiState.editHeightCm.toString()) }
    LaunchedEffect(uiState.editHeightCm) {
        if (heightInputText.toIntOrNull() != uiState.editHeightCm) {
            heightInputText = uiState.editHeightCm.toString()
        }
    }

    var weightInputText by remember { mutableStateOf("%.1f".format(uiState.editWeightKg)) }
    LaunchedEffect(uiState.editWeightKg) {
        val currentFloat = weightInputText.toDoubleOrNull() ?: 0.0
        if (Math.abs(currentFloat - uiState.editWeightKg) > 0.05) {
            weightInputText = "%.1f".format(uiState.editWeightKg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.edit_profile_title)) },
                navigationIcon = { IconButton(onClick = { viewModel.cancelEditing() }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) } },
                actions = {
                    TextButton(onClick = { viewModel.saveChanges() }, enabled = !uiState.isSaving) {
                        if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = uiState.editDisplayName, onValueChange = { viewModel.updateEditDisplayName(it) },
                label = { Text(stringResource(R.string.display_name_label)) }, leadingIcon = { Icon(Icons.Filled.Person, null) },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text(stringResource(R.string.gender_label), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = uiState.editGender == "male", onClick = { viewModel.updateEditGender("male") }, label = { Text("🚹 " + stringResource(R.string.gender_male)) })
                FilterChip(selected = uiState.editGender == "female", onClick = { viewModel.updateEditGender("female") }, label = { Text("🚺 " + stringResource(R.string.gender_female)) })
            }

            Text(stringResource(R.string.birth_year_label, uiState.editBirthYear), style = MaterialTheme.typography.titleMedium)
            Slider(value = uiState.editBirthYear.toFloat(), onValueChange = { viewModel.updateEditBirthYear(it.toInt()) }, valueRange = 1940f..2015f)
            OutlinedTextField(
                value = birthYearInputText,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        birthYearInputText = newValue
                        newValue.toIntOrNull()?.let { year ->
                            if (year in 1940..2015) {
                                viewModel.updateEditBirthYear(year)
                            }
                        }
                    }
                },
                label = { Text(stringResource(R.string.birth_year_label, uiState.editBirthYear).substringBefore(":") + " (1940 - 2015)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(stringResource(R.string.height_slider_label, uiState.editHeightCm), style = MaterialTheme.typography.titleMedium)
            Slider(value = uiState.editHeightCm.toFloat(), onValueChange = { viewModel.updateEditHeightCm(it.toInt()) }, valueRange = 100f..250f)
            OutlinedTextField(
                value = heightInputText,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        heightInputText = newValue
                        newValue.toIntOrNull()?.let { height ->
                            if (height in 100..250) {
                                viewModel.updateEditHeightCm(height)
                            }
                        }
                    }
                },
                label = { Text(stringResource(R.string.enter_height_manual)) },
                suffix = { Text("cm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(stringResource(R.string.weight_slider_label, uiState.editWeightKg), style = MaterialTheme.typography.titleMedium)
            Slider(value = uiState.editWeightKg.toFloat(), onValueChange = { viewModel.updateEditWeightKg((it * 10).toInt() / 10.0) }, valueRange = 30f..200f)
            OutlinedTextField(
                value = weightInputText,
                onValueChange = { newValue ->
                    val normalized = newValue.replace(',', '.')
                    if (normalized.isEmpty() || normalized.toDoubleOrNull() != null || normalized == "." || normalized.endsWith(".")) {
                        weightInputText = newValue
                        normalized.toDoubleOrNull()?.let { weight ->
                            if (weight >= 30.0 && weight <= 200.0) {
                                viewModel.updateEditWeightKg(weight)
                            }
                        }
                    }
                },
                label = { Text(stringResource(R.string.enter_weight_manual)) },
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(stringResource(R.string.goal_label), style = MaterialTheme.typography.titleMedium)
            goalOptions.forEach { option ->
                FilterChip(selected = uiState.editGoal == option.value, onClick = { viewModel.updateEditGoal(option.value) },
                    label = {
                        val labelText = when (option.value) {
                            "lose_weight" -> "🔥 " + stringResource(R.string.goal_lose)
                            "gain_muscle" -> "💪 " + stringResource(R.string.goal_gain)
                            else -> "⚖️ " + stringResource(R.string.goal_maintain)
                        }
                        Text(labelText)
                    })
            }

            Text(stringResource(R.string.activity_label), style = MaterialTheme.typography.titleMedium)
            activityLevelOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = uiState.editActivityLevel == option.value, onClick = { viewModel.updateEditActivityLevel(option.value) })
                    val labelText = when (option.value) {
                        1.2 -> "🚶 " + stringResource(R.string.activity_sedentary)
                        1.375 -> "🏃 " + stringResource(R.string.activity_light)
                        1.55 -> "🏋️ " + stringResource(R.string.activity_moderate)
                        1.725 -> "🏅 " + stringResource(R.string.activity_very)
                        else -> "🔥 " + stringResource(R.string.activity_extreme)
                    }
                    Text(labelText)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Int, maxValue: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value}g", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (maxValue > 0) (value.toFloat() / maxValue).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color, trackColor = color.copy(alpha = 0.2f))
    }
}
