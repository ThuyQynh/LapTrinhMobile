package com.team.smartnutrition.pantry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.smartnutrition.pantry.data.PantryRepository
import com.team.smartnutrition.pantry.model.PantryItem
import com.team.smartnutrition.pantry.util.ExpiryStatus
import com.team.smartnutrition.pantry.util.calculateExpiryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════
 * PANTRY LIST VIEW MODEL
 * ═══════════════════════════════════════════
 *
 * Xử lý:
 * - Load realtime danh sách thực phẩm từ Firestore
 * - Lọc theo trạng thái hạn sử dụng (Tất cả / Sắp hết hạn / Đã hết hạn)
 * - Tìm kiếm theo tên
 * - Xóa thực phẩm
 * - Tự động cập nhật status khi hạn sử dụng thay đổi
 */

data class PantryListUiState(
    val items: List<PantryItem> = emptyList(),
    val filteredItems: List<PantryItem> = emptyList(),
    val selectedFilter: ExpiryFilter = ExpiryFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showDeleteDialog: Boolean = false,
    val itemToDelete: PantryItem? = null,
    val showAddSheet: Boolean = false
)

enum class ExpiryFilter(val label: String) {
    ALL("Tất cả"),
    EXPIRING("Sắp hết hạn"),
    EXPIRED("Đã hết hạn")
}

class PantryListViewModel : ViewModel() {

    private val repository = PantryRepository()

    private val _uiState = MutableStateFlow(PantryListUiState())
    val uiState: StateFlow<PantryListUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    /**
     * Load realtime danh sách từ Firestore.
     * Tự cập nhật status nếu cần.
     */
    private fun loadItems() {
        val uid = repository.currentUid ?: return

        viewModelScope.launch {
            repository.getItems(uid)
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Lỗi tải kho: ${e.message}")
                    }
                }
                .collect { items ->
                    // Tự cập nhật status cho items có hạn sử dụng thay đổi
                    val updatedItems = refreshStatuses(uid, items)

                    _uiState.update { state ->
                        state.copy(
                            items = updatedItems,
                            filteredItems = applyFilters(
                                updatedItems, state.selectedFilter, state.searchQuery
                            ),
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    /**
     * Đặt bộ lọc trạng thái hạn sử dụng.
     */
    fun setFilter(filter: ExpiryFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                filteredItems = applyFilters(state.items, filter, state.searchQuery)
            )
        }
    }

    /**
     * Cập nhật query tìm kiếm.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredItems = applyFilters(state.items, state.selectedFilter, query)
            )
        }
    }

    /**
     * Hiện confirm dialog xóa.
     */
    fun showDeleteConfirm(item: PantryItem) {
        _uiState.update { it.copy(showDeleteDialog = true, itemToDelete = item) }
    }

    /**
     * Ẩn confirm dialog xóa.
     */
    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteDialog = false, itemToDelete = null) }
    }

    /**
     * Xóa thực phẩm khỏi kho.
     */
    fun deleteItem(item: PantryItem) {
        val uid = repository.currentUid ?: return

        viewModelScope.launch {
            try {
                repository.deleteItem(uid, item.id)
                _uiState.update { it.copy(showDeleteDialog = false, itemToDelete = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Không thể xóa: ${e.message}",
                        showDeleteDialog = false,
                        itemToDelete = null
                    )
                }
            }
        }
    }

    /** Toggle hiển thị BottomSheet thêm mới */
    fun toggleAddSheet(show: Boolean) {
        _uiState.update { it.copy(showAddSheet = show) }
    }

    /** Clear error message */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ═══ PRIVATE HELPERS ═══

    /**
     * Áp dụng bộ lọc + tìm kiếm lên danh sách items.
     */
    private fun applyFilters(
        items: List<PantryItem>,
        filter: ExpiryFilter,
        searchQuery: String
    ): List<PantryItem> {
        var result = items

        // Lọc theo trạng thái
        result = when (filter) {
            ExpiryFilter.ALL -> result
            ExpiryFilter.EXPIRING -> result.filter {
                calculateExpiryStatus(it.expiryDate) == ExpiryStatus.EXPIRING
            }
            ExpiryFilter.EXPIRED -> result.filter {
                calculateExpiryStatus(it.expiryDate) == ExpiryStatus.EXPIRED
            }
        }

        // Lọc theo tìm kiếm (case-insensitive)
        if (searchQuery.isNotBlank()) {
            result = result.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }

        return result
    }

    /**
     * Tự cập nhật status nếu hạn sử dụng thay đổi so với Firestore.
     * VD: item ngày hôm qua còn "fresh", hôm nay thành "expiring".
     */
    private fun refreshStatuses(uid: String, items: List<PantryItem>): List<PantryItem> {
        return items.map { item ->
            val currentStatus = calculateExpiryStatus(item.expiryDate)
            if (currentStatus.firestoreValue != item.status) {
                // Update Firestore async (fire-and-forget)
                viewModelScope.launch {
                    try {
                        repository.updateItem(
                            uid, item.id,
                            mapOf("status" to currentStatus.firestoreValue)
                        )
                    } catch (_: Exception) {
                        // Silently ignore — sẽ retry lần load tiếp
                    }
                }
                item.copy(status = currentStatus.firestoreValue)
            } else {
                item
            }
        }
    }
}
