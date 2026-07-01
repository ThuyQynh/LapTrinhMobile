package com.team.smartnutrition.habit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.habit.viewmodel.HabitUiState
import com.team.smartnutrition.habit.viewmodel.HabitViewModel
import com.team.smartnutrition.navigation.Screen
import com.team.smartnutrition.habit.model.CustomReminder

/**
 * MODULE 4 - HABIT DASHBOARD SCREEN
 *
 * Màn hình chính theo dõi thói quen hàng ngày:
 * - CircularProgress: số cốc nước / mục tiêu
 * - Nút +1/-1 cốc nước
 * - Giờ đi ngủ & Thức dậy (Hẹn giờ ngủ)
 * - Checkbox danh sách nhắc nhở tùy chỉnh
 * - Nút navigate sang Settings
 */
@Composable
fun HabitDashboardScreen(
    navController: NavController,
    viewModel: HabitViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshReminderSettings()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> LoadingScreen(stringResource(R.string.loading_habits))
            else -> HabitDashboardContent(
                uiState = uiState,
                onAddWater = { viewModel.addWaterCup() },
                onRemoveWater = { viewModel.removeWaterCup() },
                onSleepEnabledChanged = { viewModel.setSleepReminderEnabled(it) },
                onSleepSettingsChanged = { bHour, bMin ->
                    viewModel.updateSleepSettings(bHour, bMin)
                },
                onToggleReminder = { viewModel.toggleReminderCompleted(it) },
                onSettingsClick = { navController.navigate(Screen.ReminderSettings.route) }
            )
        }

        // Màu báo lỗi (Error) card
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
// Nội dung chính
@Composable
private fun HabitDashboardContent(
    uiState: HabitUiState,
    onAddWater: () -> Unit,
    onRemoveWater: () -> Unit,
    onSleepEnabledChanged: (Boolean) -> Unit,
    onSleepSettingsChanged: (Int, Int) -> Unit,
    onToggleReminder: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val habit = uiState.habitDay ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "💧 " + stringResource(R.string.habit_dashboard_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            fontWeight = FontWeight.Bold
        )

        // Water Progress Section
        WaterProgressSection(
            waterCups = habit.waterCups,
            waterGoal = uiState.waterGoal,
            onAdd = onAddWater,
            onRemove = onRemoveWater
        )

        // Nhắc nhở đi ngủ (Thay thế thanh trượt giờ ngủ cũ)
        SleepAlarmSection(
            enabled = uiState.sleepReminderEnabled,
            bedtimeHour = uiState.bedtimeHour,
            bedtimeMinute = uiState.bedtimeMinute,
            onEnabledChanged = onSleepEnabledChanged,
            onSettingsChanged = onSleepSettingsChanged
        )

        // Nhắc nhở thói quen tùy chỉnh (Thay thế vitamin section)
        CustomRemindersDashboardSection(
            customReminders = uiState.customReminders,
            completedReminders = habit.completedReminders,
            onToggleCompletion = onToggleReminder,
            onSettingsClick = onSettingsClick
        )

        // Navigate to Settings
        OutlinedButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reminder_settings_btn))
        }

        // Bottom spacing
        Spacer(Modifier.height(24.dp))
    }
}
// Tiến trình uống nước - Vòng tròn tiến trình
@Composable
private fun WaterProgressSection(
    waterCups: Int,
    waterGoal: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val progress = if (waterGoal > 0) waterCups.toFloat() / waterGoal else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "water_progress"
    )
    val isGoalReached = waterCups >= waterGoal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💧 " + stringResource(R.string.water_today_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(20.dp))

            // Circular Progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surface,
                    color = if (isGoalReached) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.primary
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$waterCups",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGoalReached) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.water_goal_cups, waterGoal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${waterCups * 250}ml",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Badge khi đạt mục tiêu
            if (isGoalReached) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "🎉 " + stringResource(R.string.target_achieved),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            // Nút +/- cốc
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onRemove,
                    enabled = waterCups > 0,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, stringResource(R.string.decrease_cup))
                }

                Text(
                    text = stringResource(R.string.cups_unit, waterCups),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                FilledIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.increase_cup))
                }
            }
        }
    }
}
// Nhắc nhở giấc ngủ - Hẹn giờ ngủ & Báo thức dậy
@Composable
private fun SleepAlarmSection(
    enabled: Boolean,
    bedtimeHour: Int,
    bedtimeMinute: Int,
    onEnabledChanged: (Boolean) -> Unit,
    onSettingsChanged: (Int, Int) -> Unit
) {
    var showSetupDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌙 ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.sleep_reminder_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { onEnabledChanged(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Time Display row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.daily_sleep_reminder_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%02d:%02d", bedtimeHour, bedtimeMinute),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action button
            OutlinedButton(
                onClick = { showSetupDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.setup_sleep_reminder_btn))
            }
        }
    }

    if (showSetupDialog) {
        SleepSetupDialog(
            currentBedtimeHour = bedtimeHour,
            currentBedtimeMinute = bedtimeMinute,
            onConfirm = { bHour, bMin ->
                onSettingsChanged(bHour, bMin)
                showSetupDialog = false
            },
            onDismiss = { showSetupDialog = false }
        )
    }
}
// Hộp thoại: Hỗ trợ cài đặt giờ ngủ
@Composable
private fun SleepSetupDialog(
    currentBedtimeHour: Int,
    currentBedtimeMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var bedtimeHour by remember { mutableStateOf(currentBedtimeHour) }
    var bedtimeMinute by remember { mutableStateOf(currentBedtimeMinute) }

    var showBedtimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_sleep_reminder_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bedtime picker trigger
                Column {
                    Text(stringResource(R.string.bedtime_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showBedtimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(String.format("%02d:%02d", bedtimeHour, bedtimeMinute), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(bedtimeHour, bedtimeMinute) }
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

    if (showBedtimePicker) {
        TimePickerDialogHelper(
            initialHour = bedtimeHour,
            initialMinute = bedtimeMinute,
            onConfirm = { h, m ->
                bedtimeHour = h
                bedtimeMinute = m
                showBedtimePicker = false
            },
            onDismiss = { showBedtimePicker = false }
        )
    }
}
// Hộp thoại chọn giờ
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogHelper(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_time_title)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text(stringResource(R.string.ok_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
// Danh sách thói quen hôm nay
@Composable
private fun CustomRemindersDashboardSection(
    customReminders: List<CustomReminder>,
    completedReminders: List<String>,
    onToggleCompletion: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val activeReminders = customReminders.filter { it.enabled }
    val totalCount = activeReminders.size
    val completedCount = activeReminders.count { completedReminders.contains(it.id) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.today_habits),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (totalCount > 0) {
                    Text(
                        text = stringResource(R.string.completed_habits_count, completedCount, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (activeReminders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.no_habits_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSettingsClick) {
                        Text(stringResource(R.string.manage_reminders_btn))
                    }
                }
            } else {
                activeReminders.forEachIndexed { index, reminder ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        )
                    }

                    val isCompleted = completedReminders.contains(reminder.id)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (reminder.id == "vitamin" || reminder.name.lowercase().contains("vitamin")) "💊" 
                                       else if (reminder.name.lowercase().contains("thuốc") || reminder.name.lowercase().contains("medicine")) "💊"
                                       else if (reminder.name.lowercase().contains("nước") || reminder.name.lowercase().contains("water")) "💧"
                                       else if (reminder.name.lowercase().contains("sữa") || reminder.name.lowercase().contains("milk")) "🥛"
                                       else "📝",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = reminder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.reminder_time_format, reminder.hour, reminder.minute),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Checkbox(
                            checked = isCompleted,
                            onCheckedChange = { onToggleCompletion(reminder.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}
