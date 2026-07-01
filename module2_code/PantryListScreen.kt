package com.team.smartnutrition.pantry

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.R
import com.team.smartnutrition.common.components.ErrorCard
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.navigation.Screen
import com.team.smartnutrition.pantry.model.PantryItem
import com.team.smartnutrition.pantry.util.ExpiryStatus
import com.team.smartnutrition.pantry.util.calculateExpiryStatus
import com.team.smartnutrition.pantry.util.daysUntilExpiry
import com.team.smartnutrition.pantry.viewmodel.ExpiryFilter
import com.team.smartnutrition.pantry.viewmodel.PantryListViewModel

/**
 * ═══════════════════════════════════════════
 * MODULE 2 - TV2: DANH SÁCH KHO THỰC PHẨM
 * ═══════════════════════════════════════════
 *
 * Màn hình chính Module 2:
 * - LazyColumn hiển thị thực phẩm với badge hạn sử dụng (Xanh/Vàng/Đỏ)
 * - Bộ lọc: Tất cả / Sắp hết hạn / Đã hết hạn
 * - Tìm kiếm theo tên
 * - FAB → BottomSheet (Chụp ảnh | Quét barcode | Nhập thủ công)
 * - Swipe để xóa (confirm dialog)
 * - Click vào item → FoodDetailScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryListScreen(
    navController: NavController,
    viewModel: PantryListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleAddSheet(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_food_btn))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ═══ HEADER ═══
            Text(
                text = "🏪 " + stringResource(R.string.pantry_toolbar_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // ═══ SEARCH BAR ═══
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_placeholder))
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ═══ FILTER CHIPS ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpiryFilter.entries.forEach { filter ->
                    val filterLabel = when (filter) {
                        ExpiryFilter.ALL -> stringResource(R.string.filter_all)
                        ExpiryFilter.EXPIRING -> stringResource(R.string.expiry_status_expiring)
                        ExpiryFilter.EXPIRED -> stringResource(R.string.expiry_status_expired)
                    }
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filterLabel) },
                        leadingIcon = if (uiState.selectedFilter == filter) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ CONTENT ═══
            when {
                uiState.isLoading -> {
                    LoadingScreen(message = stringResource(R.string.loading_pantry))
                }
                uiState.errorMessage != null -> {
                    ErrorCard(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.clearError() }
                    )
                }
                uiState.filteredItems.isEmpty() -> {
                    EmptyPantryState(
                        isFiltered = uiState.selectedFilter != ExpiryFilter.ALL
                                || uiState.searchQuery.isNotBlank()
                    )
                }
                else -> {
                    // ═══ DANH SÁCH THỰC PHẨM ═══
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.filteredItems,
                            key = { it.id }
                        ) { item ->
                            SwipeToDismissItem(
                                item = item,
                                onDelete = { viewModel.showDeleteConfirm(item) },
                                onClick = {
                                    navController.navigate(Screen.FoodDetail.createRoute(item.id))
                                }
                            )
                        }

                        // Spacer cho FAB
                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    // ═══ ADD BOTTOM SHEET ═══
    if (uiState.showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleAddSheet(false) }
        ) {
            AddFoodBottomSheet(
                onCameraClick = {
                    viewModel.toggleAddSheet(false)
                    navController.navigate(Screen.CameraCapture.route)
                },
                onBarcodeClick = {
                    viewModel.toggleAddSheet(false)
                    navController.navigate(Screen.BarcodeScan.route)
                },
                onManualClick = {
                    viewModel.toggleAddSheet(false)
                    // Navigate FoodResult với source=manual (empty form)
                    navController.currentBackStackEntry?.savedStateHandle?.set("food_source", "manual")
                    navController.navigate(Screen.FoodResult.route)
                }
            )
        }
    }

    // ═══ DELETE CONFIRM DIALOG ═══
    if (uiState.showDeleteDialog && uiState.itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(R.string.delete_food_title)) },
            text = {
                Text(stringResource(R.string.delete_food_confirm, uiState.itemToDelete!!.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteItem(uiState.itemToDelete!!) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ═══════════════════════════════════════════
// SUB-COMPOSABLES
// ═══════════════════════════════════════════

/**
 * Item thực phẩm với swipe-to-delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissItem(
    item: PantryItem,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // Không tự dismiss, chờ confirm dialog
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Background đỏ khi swipe
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        PantryItemCard(item = item, onClick = onClick)
    }
}

/**
 * Card hiển thị 1 thực phẩm.
 */
@Composable
private fun PantryItemCard(
    item: PantryItem,
    onClick: () -> Unit
) {
    val expiryStatus = calculateExpiryStatus(item.expiryDate)
    val days = daysUntilExpiry(item.expiryDate)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            // Icon theo nguồn
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (item.source) {
                            "camera" -> Icons.Filled.CameraAlt
                            "barcode" -> Icons.Filled.QrCodeScanner
                            else -> Icons.Filled.Edit
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Thông tin
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.caloriesPer100g} kcal • ${item.proteinPer100g}g protein • ${item.quantityGrams}${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badge
            StatusBadge(status = expiryStatus, daysLeft = days)
        }
    }
}

/**
 * Badge hiển thị trạng thái hạn sử dụng (Xanh/Vàng/Đỏ).
 */
@Composable
fun StatusBadge(
    status: ExpiryStatus,
    daysLeft: Long,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(android.graphics.Color.parseColor(status.colorHex))

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Text(
            text = when (status) {
                ExpiryStatus.FRESH -> "${daysLeft}d"
                ExpiryStatus.EXPIRING -> "${daysLeft}d ⚠️"
                ExpiryStatus.EXPIRED -> stringResource(R.string.expiry_status_expired)
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Empty state khi kho trống.
 */
@Composable
private fun EmptyPantryState(isFiltered: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isFiltered) Icons.Filled.SearchOff else Icons.Filled.Kitchen,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isFiltered) stringResource(R.string.no_food_found)
                else stringResource(R.string.pantry_empty),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isFiltered) stringResource(R.string.filter_or_search_hint)
                else stringResource(R.string.pantry_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * BottomSheet chọn cách thêm thực phẩm.
 */
@Composable
private fun AddFoodBottomSheet(
    onCameraClick: () -> Unit,
    onBarcodeClick: () -> Unit,
    onManualClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.add_food_btn),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Option 1: Camera AI
        AddOptionItem(
            icon = Icons.Filled.CameraAlt,
            title = stringResource(R.string.photo_ai_title),
            description = stringResource(R.string.photo_ai_desc),
            onClick = onCameraClick
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Option 2: Barcode
        AddOptionItem(
            icon = Icons.Filled.QrCodeScanner,
            title = stringResource(R.string.barcode_scan_title),
            description = stringResource(R.string.barcode_scan_desc),
            onClick = onBarcodeClick
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Option 3: Manual
        AddOptionItem(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.manual_entry_title),
            description = stringResource(R.string.manual_entry_desc),
            onClick = onManualClick
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Item trong BottomSheet thêm thực phẩm.
 */
@Composable
private fun AddOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
