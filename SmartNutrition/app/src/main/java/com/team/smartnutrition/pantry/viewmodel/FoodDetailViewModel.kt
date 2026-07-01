package com.team.smartnutrition.pantry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team.smartnutrition.pantry.data.PantryRepository
import com.team.smartnutrition.pantry.model.PantryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * FoodDetailViewModel
 *
 * Xử lý:
 * - Load chi tiết 1 thực phẩm theo itemId
 * - Chỉnh sửa số lượng
 * - Xóa thực phẩm
 */

data class FoodDetailUiState(
    val item: PantryItem? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val editQuantity: String = "",
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteSuccess: Boolean = false,
    val saveSuccess: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val errorMessage: String? = null
)

class FoodDetailViewModel : ViewModel() {

    private val repository = PantryRepository()

    private val _uiState = MutableStateFlow(FoodDetailUiState())
    val uiState: StateFlow<FoodDetailUiState> = _uiState.asStateFlow()

    /**
     * Load chi tiết item từ Firestore.
     */
    fun loadItem(itemId: String) {
        val uid = repository.currentUid ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val item = repository.getItem(uid, itemId)
                if (item != null) {
                    _uiState.update {
                        it.copy(
                            item = item,
                            isLoading = false,
                            editQuantity = item.quantityGrams.toString()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Thực phẩm không tồn tại")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Lỗi: ${e.message}")
                }
            }
        }
    }

    // Chế độ chỉnh sửa thông tin

    fun startEditing() {
        _uiState.update { state ->
            state.copy(
                isEditing = true,
                editQuantity = state.item?.quantityGrams?.toString() ?: "0"
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { state ->
            state.copy(
                isEditing = false,
                editQuantity = state.item?.quantityGrams?.toString() ?: "0"
            )
        }
    }

    fun updateEditQuantity(value: String) {
        _uiState.update { it.copy(editQuantity = value) }
    }

    fun saveChanges() {
        val uid = repository.currentUid ?: return
        val item = _uiState.value.item ?: return
        val newQuantity = _uiState.value.editQuantity.toIntOrNull()

        if (newQuantity == null || newQuantity <= 0) {
            _uiState.update { it.copy(errorMessage = "Số lượng phải lớn hơn 0") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                repository.updateItem(
                    uid, item.id,
                    mapOf("quantityGrams" to newQuantity)
                )
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        isEditing = false,
                        item = state.item?.copy(quantityGrams = newQuantity)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Lưu thất bại: ${e.message}")
                }
            }
        }
    }

    // Xóa thực phẩm

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteItem() {
        val uid = repository.currentUid ?: return
        val item = _uiState.value.item ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, showDeleteDialog = false) }
            try {
                repository.deleteItem(uid, item.id)
                _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDeleting = false, errorMessage = "Xóa thất bại: ${e.message}")
                }
            }
        }
    }

    /** Clear error */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
