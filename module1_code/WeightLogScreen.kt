package com.team.smartnutrition.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.auth.util.HealthCalculator
import com.team.smartnutrition.auth.viewmodel.WeightLogViewModel
import com.team.smartnutrition.common.components.SmartTopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ═══════════════════════════════════════════
 * MODULE 1 - TV1: NHẬT KÝ CÂN NẶNG
 * ═══════════════════════════════════════════
 *
 * Tính năng:
 * - Nhập cân nặng hôm nay + tính BMI realtime
 * - Lưu vào weightLog/{date} + update user profile
 * - Hiển thị lịch sử cân nặng (LazyColumn DESC)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightLogScreen(
    navController: NavController,
    viewModel: WeightLogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Handle messages
    val successMsg = stringResource(R.string.weight_logged_success)
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar(successMsg)
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            SmartTopBar(
                title = stringResource(R.string.weight_log_btn),
                onBackClick = { navController.popBackStack() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ═══ Input Card ═══
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.today_weight_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = uiState.currentWeight,
                                onValueChange = { viewModel.updateWeight(it) },
                                label = { Text(stringResource(R.string.weight_label)) },
                                leadingIcon = { Icon(Icons.Filled.MonitorWeight, null) },
                                suffix = { Text("kg") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    viewModel.saveWeight()
                                }),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            // BMI Preview
                            uiState.calculatedBmi?.let { bmi ->
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.new_bmi, bmi, HealthCalculator.getBmiCategory(bmi)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { focusManager.clearFocus(); viewModel.saveWeight() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !uiState.isSaving && uiState.currentWeight.toDoubleOrNull() != null
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.save_today))
                                }
                            }
                        }
                    }
                }

                // ═══ History Header ═══
                if (uiState.history.isNotEmpty()) {
                    item {
                        Text("📅 " + stringResource(R.string.weight_history),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }

                // ═══ History Items ═══
                items(uiState.history) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val formattedDate = try {
                                    LocalDate.parse(entry.date).format(displayFormatter)
                                } catch (e: Exception) { entry.date }

                                Text(formattedDate, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("BMI: ${"%.2f".format(entry.bmi)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${"%.1f".format(entry.weightKg)} kg",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // ═══ Empty State ═══
                if (uiState.history.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📊", style = MaterialTheme.typography.displaySmall)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.no_weight_data), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.start_weight_log_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
