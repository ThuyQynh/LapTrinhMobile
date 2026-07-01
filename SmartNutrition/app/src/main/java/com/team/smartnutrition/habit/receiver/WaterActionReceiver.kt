package com.team.smartnutrition.habit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.team.smartnutrition.habit.data.HabitRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WATER ACTION RECEIVER
 *
 * Xử lý khi user nhấn nút "💧 Đã uống ✓" trên notification.
 *
 * Flow:
 * 1. Lấy UID từ FirebaseAuth
 * 2. Lấy ngày hôm nay
 * 3. Gọi Firestore FieldValue.increment(1) (fire-and-forget)
 * 4. Dismiss notification
 *
 * KHÔNG mở app — chạy hoàn toàn ngầm (background).
 */
class WaterActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Lấy UID
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 2. Ngày hôm nay
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 3. Increment waterCups trên Firestore (fire-and-forget, offline-safe)
        val repository = HabitRepository()
        repository.incrementWaterCups(uid, today)

        // 4. Dismiss notification
        val notificationId = intent.getIntExtra(
            "notification_id",
            WaterReminderReceiver.WATER_NOTIFICATION_ID
        )
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
