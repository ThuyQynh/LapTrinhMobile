package com.team.smartnutrition.habit

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.common.components.SmartTopBar
import com.team.smartnutrition.habit.viewmodel.HabitUiState
import com.team.smartnutrition.habit.viewmodel.HabitViewModel
import com.team.smartnutrition.habit.model.CustomReminder

/**
 * MODULE 4 - REMINDER SETTINGS SCREEN
 *
 * Cài đặt:
 * - Mục tiêu cốc nước/ngày
 * - Nhắc uống nước: switch + interval (1h/2h/3h) + start/end hour
 * - Nhắc nhở thói quen tùy chỉnh (uống thuốc, uống vitamin, uống sữa, v.v.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    navController: NavController,
    viewModel: HabitViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission launcher cho POST_NOTIFICATIONS (Android 13+)
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }

    /** Check và xin quyền trước khi bật nhắc nhở */
    fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val permissionState = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionState == PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pendingAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            action()
        }
    }

    Scaffold(
        topBar = {
            SmartTopBar(
                title = stringResource(R.string.reminder_settings_title),
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Exact alarm permission warning (Android 12+)
            ExactAlarmWarning(context)

            // Water goal selector
            WaterGoalSelector(
                currentGoal = uiState.waterGoal,
                onGoalSelected = { viewModel.setWaterGoal(it) }
            )

            // Water reminder section
            WaterReminderSection(
                uiState = uiState,
                onEnabledChanged = { enabled ->
                    if (enabled) {
                        withNotificationPermission {
                            viewModel.setWaterReminderEnabled(true)
                        }
                    } else {
                        viewModel.setWaterReminderEnabled(false)
                    }
                },
                onIntervalChanged = { viewModel.setWaterInterval(it) },
                onStartHourChanged = { viewModel.setWaterStartHour(it) },
                onEndHourChanged = { viewModel.setWaterEndHour(it) }
            )

            // Custom reminders section (Thay thế cho Vitamin Reminder Section cũ)
            CustomRemindersSettingsSection(
                uiState = uiState,
                onAddReminder = { name, hour, minute -> viewModel.addCustomReminder(name, hour, minute) },
                onUpdateReminder = { viewModel.updateCustomReminder(it) },
                onDeleteReminder = { viewModel.deleteCustomReminder(it) },
                onToggleReminder = { id, enabled -> viewModel.toggleCustomReminderEnabled(id, enabled) },
                withPermission = { action -> withNotificationPermission(action) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
// EXACT ALARM WARNING (Android 12+)
@Composable
private fun ExactAlarmWarning(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ " + stringResource(R.string.alarm_permission_warning),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.alarm_permission_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.open_settings_btn))
                    }
                }
            }
        }
    }
}
// WATER GOAL SELECTOR
@Composable
private fun WaterGoalSelector(
    currentGoal: Int,
    onGoalSelected: (Int) -> Unit
) {
    val options = listOf(5, 6, 7, 8, 10)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🎯 " + stringResource(R.string.water_goal_cups_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { goal ->
                    val isSelected = goal == currentGoal
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGoalSelected(goal) },
                        label = {
                            Text(
                                text = "$goal",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.water_ml_per_day, currentGoal * 250),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
// WATER REMINDER SECTION
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterReminderSection(
    uiState: HabitUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onStartHourChanged: (Int) -> Unit,
    onEndHourChanged: (Int) -> Unit
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Switch header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💧 " + stringResource(R.string.water_reminder_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = uiState.waterReminderEnabled,
                    onCheckedChange = { onEnabledChanged(it) }
                )
            }

            // Sub-settings (hiện khi bật)
            AnimatedVisibility(visible = uiState.waterReminderEnabled) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Interval selector
                    Text(
                        text = stringResource(R.string.reminder_interval),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { interval ->
                            FilterChip(
                                selected = uiState.waterIntervalHours == interval,
                                onClick = { onIntervalChanged(interval) },
                                label = { Text(stringResource(R.string.reminder_interval_hours, interval)) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Giờ bắt đầu / kết thúc
                    Text(
                        text = stringResource(R.string.reminder_timeframe),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.reminder_start_hour, uiState.waterStartHour))
                        }
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.reminder_end_hour, uiState.waterEndHour))
                        }
                    }
                }
            }
        }
    }

    // TimePicker Dialogs
    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = uiState.waterStartHour,
            initialMinute = 0,
            onConfirm = { hour, _ ->
                onStartHourChanged(hour)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = uiState.waterEndHour,
            initialMinute = 0,
            onConfirm = { hour, _ ->
                onEndHourChanged(hour)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}
// CUSTOM REMINDERS SETTINGS SECTION - Quản lý nhắc nhở tùy chỉnh
@Composable
private fun CustomRemindersSettingsSection(
    uiState: HabitUiState,
    onAddReminder: (String, Int, Int) -> Unit,
    onUpdateReminder: (CustomReminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onToggleReminder: (String, Boolean) -> Unit,
    withPermission: (() -> Unit) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<CustomReminder?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.custom_reminders_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = {
                        withPermission {
                            showAddDialog = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_reminder_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (uiState.customReminders.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_reminders_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                uiState.customReminders.forEachIndexed { index, reminder ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reminder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.daily_at, reminder.hour, reminder.minute),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    withPermission {
                                        editingReminder = reminder
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onDeleteReminder(reminder.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            Switch(
                                checked = reminder.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        withPermission {
                                            onToggleReminder(reminder.id, true)
                                        }
                                    } else {
                                        onToggleReminder(reminder.id, false)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditReminderDialog(
            reminder = null,
            onConfirm = { name, hour, minute ->
                onAddReminder(name, hour, minute)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingReminder != null) {
        AddEditReminderDialog(
            reminder = editingReminder,
            onConfirm = { name, hour, minute ->
                onUpdateReminder(editingReminder!!.copy(name = name, hour = hour, minute = minute))
                editingReminder = null
            },
            onDismiss = { editingReminder = null }
        )
    }
}
// DIALOG: ADD/EDIT CUSTOM REMINDER
@Composable
private fun AddEditReminderDialog(
    reminder: CustomReminder?,
    onConfirm: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(reminder?.name ?: "") }
    var hour by remember { mutableStateOf(reminder?.hour ?: 7) }
    var minute by remember { mutableStateOf(reminder?.minute ?: 0) }

    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reminder == null) stringResource(R.string.add_reminder_title) else stringResource(R.string.edit_reminder_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tên nhắc nhở
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.reminder_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Trigger chọn giờ
                Column {
                    Text(stringResource(R.string.reminder_time_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(String.format("%02d:%02d", hour, minute), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), hour, minute)
                    }
                },
                enabled = name.isNotBlank()
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

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
// TIME PICKER DIALOG - Helper
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int = 0,
    onConfirm: (hour: Int, minute: Int) -> Unit,
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
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
