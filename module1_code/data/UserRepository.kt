package com.team.smartnutrition.auth.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.team.smartnutrition.auth.model.User
import com.team.smartnutrition.auth.model.WeightEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * ═══════════════════════════════════════════
 * USER REPOSITORY - Lớp dữ liệu duy nhất
 * ═══════════════════════════════════════════
 *
 * Đóng gói toàn bộ Firebase Auth + Firestore operations.
 * Tất cả ViewModel dùng chung repository này.
 */
class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // ═══ FIREBASE AUTH ═══

    /** User hiện tại (null nếu chưa login) */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** UID của user hiện tại */
    val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Đăng nhập bằng Email/Password.
     * @throws Exception nếu thất bại
     */
    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = kotlinx.coroutines.withTimeoutOrNull(15000) {
            auth.signInWithEmailAndPassword(email, password).await()
        } ?: throw Exception("timeout")
        return result.user ?: throw Exception("Đăng nhập thất bại")
    }

    /**
     * Đăng nhập bằng Google Credential.
     * @param credential AuthCredential từ Google Sign-In flow
     */
    suspend fun signInWithCredential(credential: AuthCredential): FirebaseUser {
        val result = kotlinx.coroutines.withTimeoutOrNull(15000) {
            auth.signInWithCredential(credential).await()
        } ?: throw Exception("timeout")
        return result.user ?: throw Exception("Đăng nhập Google thất bại")
    }

    /**
     * Đăng ký tài khoản mới bằng Email/Password.
     * @throws Exception nếu email đã tồn tại, password yếu, v.v.
     */
    suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        val result = kotlinx.coroutines.withTimeoutOrNull(15000) {
            auth.createUserWithEmailAndPassword(email, password).await()
        } ?: throw Exception("timeout")
        return result.user ?: throw Exception("Đăng ký thất bại")
    }

    /** Đăng xuất */
    fun signOut() {
        auth.signOut()
    }

    // ═══ FIRESTORE: USER PROFILE ═══

    /**
     * Kiểm tra user đã có profile trên Firestore chưa.
     * Dùng khi login để quyết định navigate ProfileSetup hay Home.
     */
    suspend fun hasProfile(uid: String): Boolean {
        return try {
            val doc = kotlinx.coroutines.withTimeout(3000) {
                firestore.collection("users").document(uid).get().await()
            }
            doc.exists()
        } catch (e: Exception) {
            // Fallback đọc từ cache local
            val doc = firestore.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.CACHE).await()
            doc.exists()
        }
    }

    /**
     * Lấy profile user từ Firestore.
     * @return User hoặc null nếu chưa có
     */
    suspend fun getUser(uid: String): User? {
        val doc = try {
            kotlinx.coroutines.withTimeout(3000) {
                firestore.collection("users").document(uid).get().await()
            }
        } catch (e: Exception) {
            // Fallback đọc từ cache local
            firestore.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.CACHE).await()
        }
        return doc.toObject(User::class.java)?.copy(uid = uid)
    }

    /**
     * Lưu/cập nhật profile user lên Firestore.
     * Dùng cho cả ProfileSetup (lần đầu) và Profile Edit.
     */
    suspend fun saveUser(user: User) {
        val data = hashMapOf(
            "email" to user.email,
            "displayName" to user.displayName,
            "gender" to user.gender,
            "birthYear" to user.birthYear,
            "heightCm" to user.heightCm,
            "weightKg" to user.weightKg,
            "activityLevel" to user.activityLevel,
            "goal" to user.goal,
            "bmi" to user.bmi,
            "bmr" to user.bmr,
            "tdee" to user.tdee,
            "proteinTarget" to user.proteinTarget,
            "carbTarget" to user.carbTarget,
            "fatTarget" to user.fatTarget,
            "calorieTarget" to user.calorieTarget,
            "updatedAt" to Timestamp.now()
        )
        // Chỉ set createdAt khi tạo mới
        if (user.createdAt == null) {
            data["createdAt"] = Timestamp.now()
        }

        // Ghi bất đồng bộ (offline-first) - ghi lập tức vào SQLite cache
        firestore.collection("users").document(user.uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
    }

    // ═══ FIRESTORE: WEIGHT LOG ═══

    /**
     * Lưu entry cân nặng ngày hôm nay.
     * Document ID = date → mỗi ngày chỉ 1 record (upsert).
     * Đồng thời cập nhật weightKg + bmi trong user profile.
     */
    suspend fun logWeight(uid: String, date: String, weightKg: Double, bmi: Double) {
        val entry = hashMapOf(
            "weightKg" to weightKg,
            "bmi" to bmi,
            "loggedAt" to Timestamp.now()
        )

        // Batch write: cập nhật cả weightLog và user profile cùng lúc bất đồng bộ
        val batch = firestore.batch()

        // 1. Upsert weightLog/{date}
        val weightRef = firestore.collection("users").document(uid)
            .collection("weightLog").document(date)
        batch.set(weightRef, entry)

        // 2. Update user profile weightKg + bmi
        val userRef = firestore.collection("users").document(uid)
        batch.update(userRef, mapOf(
            "weightKg" to weightKg,
            "bmi" to bmi,
            "updatedAt" to Timestamp.now()
        ))

        // Ghi bất đồng bộ (offline-first)
        batch.commit()
    }

    /**
     * Lấy lịch sử cân nặng, sắp xếp mới nhất trước.
     * @param limit số lượng tối đa entries trả về
     */
    suspend fun getWeightHistory(uid: String, limit: Long = 30): List<WeightEntry> {
        val snapshot = try {
            kotlinx.coroutines.withTimeout(3000) {
                firestore.collection("users").document(uid)
                    .collection("weightLog")
                    .orderBy("loggedAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()
            }
        } catch (e: Exception) {
            // Fallback đọc từ cache local
            firestore.collection("users").document(uid)
                .collection("weightLog")
                .orderBy("loggedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
        }

        return snapshot.documents.map { doc ->
            doc.toObject(WeightEntry::class.java)?.copy(date = doc.id)
                ?: WeightEntry(date = doc.id)
        }
    }
}
