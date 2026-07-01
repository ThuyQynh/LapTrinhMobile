package com.team.smartnutrition.pantry.util

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * ═══════════════════════════════════════════
 * EXPIRY STATUS - Logic cảnh báo hạn sử dụng
 * ═══════════════════════════════════════════
 *
 * Quy tắc:
 * - 🟢 FRESH:    còn > 3 ngày
 * - 🟡 EXPIRING: còn 1-3 ngày
 * - 🔴 EXPIRED:  đã hết hạn (≤ 0 ngày)
 */
enum class ExpiryStatus(
    val label: String,
    val colorHex: String,
    val firestoreValue: String
) {
    FRESH("Còn tươi", "#4CAF50", "fresh"),
    EXPIRING("Sắp hết hạn!", "#FFC107", "expiring"),
    EXPIRED("ĐÃ HẾT HẠN", "#F44336", "expired");
}

/**
 * Tính số ngày còn lại cho đến khi hết hạn.
 * @return số ngày (âm nếu đã quá hạn), hoặc Long.MAX_VALUE nếu không có ngày hết hạn
 */
fun daysUntilExpiry(expiryDate: Timestamp?): Long {
    if (expiryDate == null) return Long.MAX_VALUE

    val expiryLocalDate = expiryDate.toDate().toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    return ChronoUnit.DAYS.between(LocalDate.now(), expiryLocalDate)
}

/**
 * Xác định trạng thái hạn sử dụng dựa trên ngày hết hạn.
 * @return ExpiryStatus tương ứng
 */
fun calculateExpiryStatus(expiryDate: Timestamp?): ExpiryStatus {
    val days = daysUntilExpiry(expiryDate)

    return when {
        days > 3 -> ExpiryStatus.FRESH
        days >= 1 -> ExpiryStatus.EXPIRING
        else -> ExpiryStatus.EXPIRED
    }
}

/**
 * Tạo text hiển thị cho trạng thái hạn sử dụng.
 * VD: "Còn 5 ngày", "Sắp hết hạn!", "ĐÃ HẾT HẠN"
 */
fun expiryDisplayText(expiryDate: Timestamp?): String {
    val days = daysUntilExpiry(expiryDate)
    val status = calculateExpiryStatus(expiryDate)

    return when (status) {
        ExpiryStatus.FRESH -> "Còn $days ngày"
        ExpiryStatus.EXPIRING -> "Còn $days ngày - Sắp hết hạn!"
        ExpiryStatus.EXPIRED -> {
            if (days == 0L) "Hết hạn hôm nay"
            else "Đã hết hạn ${-days} ngày"
        }
    }
}
