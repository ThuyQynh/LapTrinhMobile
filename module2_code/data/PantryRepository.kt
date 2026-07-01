package com.team.smartnutrition.pantry.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.team.smartnutrition.pantry.model.PantryItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * ═══════════════════════════════════════════
 * PANTRY REPOSITORY - Lớp dữ liệu kho thực phẩm
 * ═══════════════════════════════════════════
 *
 * Đóng gói toàn bộ Firestore operations cho collection:
 *   users/{uid}/pantry/{autoId}
 *
 * Convention: giống UserRepository (Module 1)
 * - Dùng withTimeoutOrNull cho mọi Firestore call
 * - Suspend functions cho one-shot operations
 * - Flow cho realtime listeners
 */
class PantryRepository {

    private val firestore = FirebaseFirestore.getInstance()

    /** UID của user hiện tại */
    val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // ═══ HELPER ═══

    /** Reference tới pantry collection của user */
    private fun pantryRef(uid: String) =
        firestore.collection("users").document(uid).collection("pantry")

    // ═══ CREATE ═══

    /**
     * Thêm thực phẩm mới vào kho.
     * @return document ID được tạo tự động
     */
    suspend fun addItem(uid: String, item: PantryItem): String {
        val data = itemToMap(item).toMutableMap()
        data["addedAt"] = Timestamp.now()

        // Tạo document ID tự động trước ở client
        val docRef = pantryRef(uid).document()
        // Ghi bất đồng bộ (offline-first) - ghi lập tức vào SQLite cache của thiết bị
        docRef.set(data)

        return docRef.id
    }

    // ═══ READ ═══

    /**
     * Lấy realtime danh sách tất cả thực phẩm trong kho.
     * Sắp xếp theo ngày hết hạn (sắp hết hạn lên trước).
     * @return Flow tự cập nhật khi data thay đổi
     */
    fun getItems(uid: String): Flow<List<PantryItem>> = callbackFlow {
        val listener = pantryRef(uid)
            .orderBy("expiryDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.map { doc ->
                    doc.toObject(PantryItem::class.java)?.copy(id = doc.id)
                        ?: PantryItem(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Lấy 1 thực phẩm theo document ID.
     * @return PantryItem hoặc null nếu không tồn tại
     */
    suspend fun getItem(uid: String, itemId: String): PantryItem? {
        val doc = try {
            kotlinx.coroutines.withTimeout(3000) {
                pantryRef(uid).document(itemId).get().await()
            }
        } catch (e: Exception) {
            // Fallback đọc từ cache local
            pantryRef(uid).document(itemId).get(com.google.firebase.firestore.Source.CACHE).await()
        }

        return doc.toObject(PantryItem::class.java)?.copy(id = doc.id)
    }

    /**
     * Lấy danh sách thực phẩm chưa hết hạn, sắp xếp sắp hết hạn trước.
     * Dùng cho Module 3 (AI Meal Planner) để lấy nguyên liệu.
     */
    suspend fun getAvailableItems(uid: String): List<PantryItem> {
        val snapshot = try {
            kotlinx.coroutines.withTimeout(3000) {
                pantryRef(uid)
                    .whereNotEqualTo("status", "expired")
                    .orderBy("status")
                    .orderBy("expiryDate", Query.Direction.ASCENDING)
                    .get()
                    .await()
            }
        } catch (e: Exception) {
            // Fallback đọc từ cache local
            pantryRef(uid)
                .whereNotEqualTo("status", "expired")
                .orderBy("status")
                .orderBy("expiryDate", Query.Direction.ASCENDING)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
        }

        return snapshot.documents.map { doc ->
            doc.toObject(PantryItem::class.java)?.copy(id = doc.id)
                ?: PantryItem(id = doc.id)
        }
    }

    // ═══ UPDATE ═══

    /**
     * Cập nhật fields cụ thể của 1 thực phẩm.
     * @param updates Map chứa field name → value mới
     */
    suspend fun updateItem(uid: String, itemId: String, updates: Map<String, Any>) {
        // Ghi bất đồng bộ (offline-first)
        pantryRef(uid).document(itemId).update(updates)
    }

    // ═══ DELETE ═══

    /**
     * Xóa 1 thực phẩm khỏi kho.
     */
    suspend fun deleteItem(uid: String, itemId: String) {
        // Ghi bất đồng bộ (offline-first)
        pantryRef(uid).document(itemId).delete()
    }

    // ═══ HELPER: Convert PantryItem → Map ═══

    /**
     * Convert PantryItem thành Map để lưu Firestore.
     * Không bao gồm 'id' vì đó là document ID.
     */
    private fun itemToMap(item: PantryItem): Map<String, Any?> {
        return mapOf(
            "name" to item.name,
            "caloriesPer100g" to item.caloriesPer100g,
            "proteinPer100g" to item.proteinPer100g,
            "quantityGrams" to item.quantityGrams,
            "unit" to item.unit,
            "source" to item.source,
            "imageUrl" to item.imageUrl,
            "expiryDate" to item.expiryDate,
            "status" to item.status,
            "barcode" to item.barcode
        )
    }
}
