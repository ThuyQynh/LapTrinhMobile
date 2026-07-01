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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.common.components.ErrorCard
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.common.components.SmartTopBar
import com.team.smartnutrition.pantry.util.ExpiryStatus
import com.team.smartnutrition.pantry.util.calculateExpiryStatus
import com.team.smartnutrition.pantry.util.daysUntilExpiry
import com.team.smartnutrition.pantry.util.expiryDisplayText
import com.team.smartnutrition.pantry.viewmodel.FoodDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * ═══════════════════════════════════════════
 * MODULE 2 - TV2: CHI TIẾT THỰC PHẨM
 * ═══════════════════════════════════════════
 *
 * Hiển thị chi tiết 1 item trong kho:
 * - Thông tin dinh dưỡng (tên, calo, protein)
 * - Thông tin kho (số lượng, đơn vị, nguồn, mã vạch, ngày thêm, hạn SD)
 * - Badge trạng thái hạn sử dụng
 * - Edit mode cho số lượng
 * - Delete với confirm dialog
 */
@Composable
fun FoodDetailScreen(
    navController: NavController,
    itemId: String,
    viewModel: FoodDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Load item khi mở screen
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    // Navigate back sau khi xóa
    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            SmartTopBar(
                title = "Chi tiết thực phẩm",
                onBackClick = { navController.popBackStack() },
                actions = {
                    if (uiState.item != null && !uiState.isEditing) {
                        // Edit button
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Sửa")
                        }
                        // Delete button
                        IconButton(onClick = { viewModel.showDeleteConfirm() }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Xóa",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (uiState.isEditing) {
                        // Save button
                        IconButton(onClick = { viewModel.saveChanges() }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Lưu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Cancel button
                        IconButton(onClick = { viewModel.cancelEditing() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Hủy")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingScreen(
                    message = "Đang tải...",
                    modifier = Modifier.padding(paddingValues)
                )
            }
            uiState.item == null -> {
                ErrorCard(
                    message = uiState.errorMessage ?: "Không tìm thấy thực phẩm",
                    onRetry = { viewModel.loadItem(itemId) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                val item = uiState.item!!
                val expiryStatus = calculateExpiryStatus(item.expiryDate)
                val days = daysUntilExpiry(item.expiryDate)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ═══ STATUS BADGE (full width) ═══
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(android.graphics.Color.parseColor(expiryStatus.colorHex))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (expiryStatus) {
                                    ExpiryStatus.FRESH -> Icons.Filled.CheckCircle
                                    ExpiryStatus.EXPIRING -> Icons.Filled.Warning
                                    ExpiryStatus.EXPIRED -> Icons.Filled.Error
                                },
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = expiryDisplayText(item.expiryDate),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ═══ CARD 1: Thông tin dinh dưỡng ═══
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                NutrientInfo(
                                    label = "Calo",
                                    value = "${item.caloriesPer100g}",
                                    unit = "kcal/100g",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                NutrientInfo(
                                    label = "Protein",
                                    value = "${item.proteinPer100g}",
                                    unit = "g/100g",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // ═══ CARD 2: Thông tin kho ═══
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Thông tin kho",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider()

                            // Số lượng (editable khi isEditing)
                            if (uiState.isEditing) {
                                OutlinedTextField(
                                    value = uiState.editQuantity,
                                    onValueChange = { viewModel.updateEditQuantity(it) },
                                    label = { Text("Số lượng") },
                                    suffix = { Text(item.unit) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            } else {
                                DetailRow(
                                    icon = Icons.Filled.Scale,
                                    label = "Số lượng",
                                    value = "${item.quantityGrams} ${item.unit}"
                                )
                            }

                            DetailRow(
                                icon = when (item.source) {
                                    "camera" -> Icons.Filled.CameraAlt
                                    "barcode" -> Icons.Filled.QrCodeScanner
                                    else -> Icons.Filled.Edit
                                },
                                label = "Nguồn",
                                value = when (item.source) {
                                    "camera" -> "📸 Chụp ảnh AI"
                                    "barcode" -> "📱 Quét mã vạch"
                                    else -> "✏️ Nhập thủ công"
                                }
                            )

                            if (item.barcode.isNotBlank()) {
                                DetailRow(
                                    icon = Icons.Filled.QrCode,
                                    label = "Mã vạch",
                                    value = item.barcode
                                )
                            }

                            DetailRow(
                                icon = Icons.Filled.CalendarToday,
                                label = "Ngày thêm",
                                value = item.addedAt?.toDate()?.let { dateFormat.format(it) } ?: "—"
                            )

                            DetailRow(
                                icon = Icons.Filled.EventBusy,
                                label = "Hạn sử dụng",
                                value = item.expiryDate?.toDate()?.let { dateFormat.format(it) } ?: "—"
                            )
                        }
                    }

                    // ═══ ERROR MESSAGE ═══
                    if (uiState.errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "⚠️ ${uiState.errorMessage}",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // ═══ DELETE CONFIRM DIALOG ═══
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Xóa thực phẩm") },
            text = {
                Text("Bạn có chắc muốn xóa \"${uiState.item?.name}\" khỏi kho?")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteItem() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) { Text("Hủy") }
            }
        )
    }
}

// ═══════════════════════════════════════════
// SUB-COMPOSABLES
// ═══════════════════════════════════════════

@Composable
private fun NutrientInfo(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
