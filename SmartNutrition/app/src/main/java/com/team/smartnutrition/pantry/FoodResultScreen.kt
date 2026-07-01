package com.team.smartnutrition.pantry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.common.components.GradientButton
import com.team.smartnutrition.common.components.SmartTopBar
import com.team.smartnutrition.navigation.Screen
import com.team.smartnutrition.pantry.viewmodel.FoodResultViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * MODULE 2 - TV2: KẾT QUẢ NHẬN DIỆN AI
 *
 * Hiển thị kết quả từ Gemini / barcode / empty (manual):
 * - Pre-fill tên, calo, protein
 * - Cho user chỉnh sửa
 * - Chọn số lượng, đơn vị, ngày hết hạn
 * - Nút "Lưu vào kho" → Firestore
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodResultScreen(
    navController: NavController,
    viewModel: FoodResultViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showUnitDropdown by remember { mutableStateOf(false) }

    // Khởi tạo từ màn hình trước
    val foodResultJson = navController.previousBackStackEntry
        ?.savedStateHandle?.get<String>("food_result_json")
    val foodSource = navController.previousBackStackEntry
        ?.savedStateHandle?.get<String>("food_source")
    val foodBarcode = navController.previousBackStackEntry
        ?.savedStateHandle?.get<String>("food_barcode")
        
    // Theo dõi biến saveSuccess để điều hướng quay về kho
    LaunchedEffect(Unit) {
        viewModel.initFromResult(foodResultJson, foodSource, foodBarcode)
    }

    // Lưu thành công và quay lại màn hình trước
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            navController.popBackStack(Screen.Pantry.route, inclusive = false)
        }
    }

    Scaffold(
        topBar = {
            SmartTopBar(
                title = when (uiState.source) {
                    "camera" -> "🤖 Kết quả AI"
                    "barcode" -> "📱 Sản phẩm barcode"
                    else -> "✏️ Nhập thủ công"
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Thẻ 1: Thông tin dinh dưỡng
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Fastfood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thông tin dinh dưỡng",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tên thực phẩm
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Tên thực phẩm") },
                        placeholder = { Text("VD: Ức gà") },
                        leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Calo và Protein trên cùng 1 hàng
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.caloriesPer100g,
                            onValueChange = { viewModel.updateCalories(it) },
                            label = { Text("Calo/100g") },
                            suffix = { Text("kcal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = uiState.proteinPer100g,
                            onValueChange = { viewModel.updateProtein(it) },
                            label = { Text("Protein/100g") },
                            suffix = { Text("g") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Thẻ 2: Chi tiết kho thực phẩm
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thông tin bổ sung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Số lượng và Đơn vị
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.quantityGrams,
                            onValueChange = { viewModel.updateQuantity(it) },
                            label = { Text("Số lượng") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Unit dropdown
                        ExposedDropdownMenuBox(
                            expanded = showUnitDropdown,
                            onExpandedChange = { showUnitDropdown = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = when (uiState.unit) {
                                    "gram" -> "Gram (g)"
                                    "piece" -> "Cái/Quả"
                                    "ml" -> "Mililit (ml)"
                                    else -> uiState.unit
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Đơn vị") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitDropdown)
                                },
                                modifier = Modifier.menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = showUnitDropdown,
                                onDismissRequest = { showUnitDropdown = false }
                            ) {
                                listOf("gram" to "Gram (g)", "piece" to "Cái/Quả", "ml" to "Mililit (ml)")
                                    .forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.updateUnit(value)
                                                showUnitDropdown = false
                                            }
                                        )
                                    }
                            }
                        }
                    }

                    // Ngày hết hạn
                    OutlinedTextField(
                        value = uiState.expiryDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        } ?: "Chọn ngày",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hạn sử dụng") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.EditCalendar, contentDescription = "Chọn ngày")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Hiển thị thông báo lỗi
            if (uiState.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "⚠️ ${uiState.errorMessage}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Nút lưu dữ liệu
            GradientButton(
                text = if (uiState.isSaving) "Đang lưu..." else "💾 Lưu vào kho",
                onClick = { viewModel.saveToKho() },
                icon = Icons.Filled.Save
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Hộp thoại chọn ngày hết hạn
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.expiryDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.setExpiryDate(millis)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Hủy") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
