package com.team.smartnutrition.analytics.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.team.smartnutrition.analytics.model.DailyCalorieEntry
import com.team.smartnutrition.analytics.model.WeightChartEntry
import com.team.smartnutrition.auth.model.User
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportAndShareReport(
        context: Context,
        userProfile: User?,
        weightEntries: List<WeightChartEntry>,
        calorieEntries: List<DailyCalorieEntry>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
        }

        var y = 50f

        // Title Header
        paint.apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#0D47A1") // Deep Blue
        }
        canvas.drawText("SMART NUTRITION - BÁO CÁO DINH DƯỠNG", 40f, y, paint)
        y += 25f

        // Date Info
        paint.apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            color = Color.GRAY
        }
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Ngày xuất báo cáo: $currentDate", 40f, y, paint)
        y += 30f

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 25f

        // Section 1: User Profile Information
        paint.apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#006064") // Dark Teal
        }
        canvas.drawText("1. Thông tin người dùng", 40f, y, paint)
        y += 20f

        paint.apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.BLACK
        }

        if (userProfile != null) {
            val age = Calendar.getInstance().get(Calendar.YEAR) - userProfile.birthYear
            val goalText = when (userProfile.goal) {
                "lose_weight" -> "Giảm cân"
                "maintain" -> "Duy trì cân nặng"
                "gain_muscle" -> "Tăng cơ"
                else -> userProfile.goal
            }
            val activityText = when (userProfile.activityLevel) {
                1.2 -> "Ít vận động"
                1.375 -> "Vận động nhẹ"
                1.55 -> "Vận động vừa"
                1.725 -> "Vận động nhiều"
                1.9 -> "Vận động cực nhiều"
                else -> userProfile.activityLevel.toString()
            }

            canvas.drawText("Họ và tên: ${userProfile.displayName}", 50f, y, paint)
            canvas.drawText("Mục tiêu: $goalText", 300f, y, paint)
            y += 18f
            canvas.drawText("Tuổi: $age", 50f, y, paint)
            canvas.drawText("Chiều cao: ${userProfile.heightCm} cm", 300f, y, paint)
            y += 18f
            canvas.drawText("Cân nặng hiện tại: ${userProfile.weightKg} kg", 50f, y, paint)
            canvas.drawText("Mức độ hoạt động: $activityText", 300f, y, paint)
            y += 18f
            canvas.drawText("Calo mục tiêu hàng ngày: ${userProfile.calorieTarget} kcal", 50f, y, paint)
            canvas.drawText("Chỉ số BMI hiện tại: ${String.format("%.1f", userProfile.bmi)}", 300f, y, paint)
            y += 25f
        } else {
            canvas.drawText("Chưa có thông tin hồ sơ người dùng.", 50f, y, paint)
            y += 20f
        }

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 25f

        // Section 2: Weight Log History
        paint.apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#006064")
        }
        canvas.drawText("2. Nhật ký cân nặng", 40f, y, paint)
        y += 20f

        paint.apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.BLACK
        }

        if (weightEntries.isEmpty()) {
            canvas.drawText("Không có dữ liệu nhật ký cân nặng.", 50f, y, paint)
            y += 20f
        } else {
            // Draw Table Header
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Ngày", 60f, y, paint)
            canvas.drawText("Cân nặng (kg)", 200f, y, paint)
            canvas.drawText("Chỉ số BMI", 340f, y, paint)
            y += 8f
            paint.color = Color.GRAY
            canvas.drawLine(40f, y, 555f, y, paint)
            y += 15f
            paint.color = Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            // Print entries (max 5 in history to avoid page overflow)
            weightEntries.takeLast(5).forEach { entry ->
                canvas.drawText(entry.date, 60f, y, paint)
                canvas.drawText("${entry.weightKg} kg", 200f, y, paint)
                canvas.drawText(String.format("%.1f", entry.bmi), 340f, y, paint)
                y += 18f
            }
            y += 10f
        }

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 25f

        // Section 3: Daily Calorie Consumption
        paint.apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#006064")
        }
        canvas.drawText("3. Thống kê năng lượng tiêu thụ hàng tuần", 40f, y, paint)
        y += 20f

        paint.apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.BLACK
        }

        if (calorieEntries.isEmpty()) {
            canvas.drawText("Không có dữ liệu năng lượng tiêu thụ từ thực đơn.", 50f, y, paint)
            y += 20f
        } else {
            // Draw Table Header
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Ngày", 60f, y, paint)
            canvas.drawText("Lượng calo tiêu thụ (kcal)", 200f, y, paint)
            canvas.drawText("Trạng thái so với mục tiêu", 380f, y, paint)
            y += 8f
            paint.color = Color.GRAY
            canvas.drawLine(40f, y, 555f, y, paint)
            y += 15f
            paint.color = Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val target = userProfile?.calorieTarget ?: 0

            calorieEntries.take(7).forEach { entry ->
                canvas.drawText(entry.dayLabel, 60f, y, paint)
                canvas.drawText("${entry.totalCalories} kcal", 200f, y, paint)
                if (target > 0) {
                    val difference = entry.totalCalories - target
                    val statusText = if (difference > 100) {
                        "Vượt chỉ tiêu (+${difference})"
                    } else if (difference < -100) {
                        "Thiếu hụt (${difference})"
                    } else {
                        "Đạt mục tiêu"
                    }
                    canvas.drawText(statusText, 380f, y, paint)
                } else {
                    canvas.drawText("-", 380f, y, paint)
                }
                y += 18f
            }
        }

        // Footer note
        y = 800f
        paint.color = Color.GRAY
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Tài liệu được tạo tự động bởi ứng dụng Smart Nutrition.", 40f, y, paint)

        pdfDocument.finishPage(page)

        // Save file to Cache directory
        val file = File(context.cacheDir, "BaoCaoDinhDuong.pdf")
        try {
            val fileOutputStream = FileOutputStream(file)
            pdfDocument.writeTo(fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            pdfDocument.close()

            // Share file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ báo cáo PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi khi tạo file PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
