package com.team.smartnutrition.pantry.util

import com.team.smartnutrition.pantry.model.FoodRecognitionResult

/**
 * BARCODE DATABASE - Tra cứu sản phẩm VN local
 *
 * HashMap hardcode các sản phẩm phổ biến tại Việt Nam.
 * Dùng làm fallback khi không có API tra cứu online.
 *
 * Barcode format: EAN-13 (13 số) — chuẩn phổ biến nhất tại VN.
 * Prefix 893 = Vietnam.
 */
object BarcodeDatabase {

    private val products = mapOf(
        // MÌ GÓI
        "8934563138028" to FoodRecognitionResult("Mì Hảo Hảo tôm chua cay", 360, 8),
        "8934563138011" to FoodRecognitionResult("Mì Hảo Hảo sa tế hành", 355, 7),
        "8936136160019" to FoodRecognitionResult("Mì Omachi xốt bò hầm", 370, 9),
        "8934680025256" to FoodRecognitionResult("Mì Kokomi tôm chua cay", 350, 7),

        // SỮA
        "8934673583220" to FoodRecognitionResult("Sữa Vinamilk có đường", 67, 3),
        "8934673583237" to FoodRecognitionResult("Sữa Vinamilk không đường", 46, 3),
        "8936036020427" to FoodRecognitionResult("Sữa TH True Milk", 63, 3),
        "8934804019529" to FoodRecognitionResult("Sữa đậu nành Fami", 42, 3),
        "8934673573344" to FoodRecognitionResult("Sữa tươi Vinamilk có đường 180ml", 76, 3),

        // NƯỚC GIẢI KHÁT
        "8935049500100" to FoodRecognitionResult("Trà xanh Không Độ", 18, 0),
        "8934588013058" to FoodRecognitionResult("Nước tăng lực Sting", 49, 0),
        "8935049501107" to FoodRecognitionResult("Nước C2 trà chanh", 30, 0),
        "5449000000996" to FoodRecognitionResult("Coca-Cola", 42, 0),
        "8934588062001" to FoodRecognitionResult("Pepsi", 44, 0),
        "8938535231014" to FoodRecognitionResult("Nước yến nha đam 500ml", 40, 0),

        // BÁNH KẸO
        "8934680027205" to FoodRecognitionResult("Bánh Chocopie", 440, 5),
        "8934680010016" to FoodRecognitionResult("Bánh Orion Custas", 400, 6),

        // GIA VỊ
        "8934804032160" to FoodRecognitionResult("Nước mắm Chinsu", 50, 8),
        "8936017360019" to FoodRecognitionResult("Nước tương Maggi", 60, 10),
        "8934563960018" to FoodRecognitionResult("Hạt nêm Knorr", 210, 12),

        // THỰC PHẨM ĐÓNG GÓI
        "8938506556015" to FoodRecognitionResult("Cá hộp Ba Cô Gái", 200, 25),
        "8934563789015" to FoodRecognitionResult("Xúc xích Vissan", 280, 12),
    )

    /**
     * Tra cứu sản phẩm theo mã vạch.
     * @param barcode chuỗi số barcode (EAN-13)
     * @return FoodRecognitionResult nếu tìm thấy, null nếu không có trong database
     */
    fun lookup(barcode: String): FoodRecognitionResult? {
        return products[barcode.trim()]
    }

    /**
     * Kiểm tra barcode có trong database không.
     */
    fun contains(barcode: String): Boolean {
        return products.containsKey(barcode.trim())
    }

    /**
     * Tổng số sản phẩm trong database.
     */
    val size: Int get() = products.size
}
