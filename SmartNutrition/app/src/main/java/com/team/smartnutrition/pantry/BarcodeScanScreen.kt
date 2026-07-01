package com.team.smartnutrition.pantry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.team.smartnutrition.common.components.GradientButton
import com.team.smartnutrition.navigation.Screen
import com.team.smartnutrition.pantry.viewmodel.BarcodeScanViewModel

/**
 * Module 2 - TV2: Quét mã vạch
 *
 * Camera quét barcode real-time bằng ML Kit.
 * Phát hiện barcode → tra cứu BarcodeDatabase → navigate FoodResult.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    navController: NavController,
    viewModel: BarcodeScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // Xử lý quyền truy cập camera
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updatePermission(granted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.updatePermission(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Hiệu ứng điều hướng màn hình (Navigation)
    LaunchedEffect(uiState.navigateToResult) {
        if (uiState.navigateToResult) {
            navController.currentBackStackEntry?.savedStateHandle?.apply {
                if (uiState.resultJson != null) {
                    set("food_result_json", uiState.resultJson)
                }
                set("food_source", "barcode")
                set("food_barcode", uiState.detectedBarcode ?: "")
            }
            navController.navigate(Screen.FoodResult.route)
            viewModel.onNavigated()
        }
    }

    // Giao diện UI
    if (!uiState.hasCameraPermission) {
        // Reuse permission denied pattern
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Cần quyền truy cập Camera",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ứng dụng cần quyền camera để quét mã vạch sản phẩm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Cấp quyền Camera")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Quay lại")
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview with Barcode Analyzer
            val previewView = remember { PreviewView(context) }

            // Setup camera with barcode analyzer
            LaunchedEffect(uiState.hasCameraPermission) {
                if (!uiState.hasCameraPermission) return@LaunchedEffect
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(context).await()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Chiến lược chống ùn ứ dữ liệu
                        .build()
                        .also {
                            it.setAnalyzer(
                                ContextCompat.getMainExecutor(context),
                                BarcodeAnalyzer { rawValue ->
                                    viewModel.onBarcodeDetected(rawValue)
                                }
                            )
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) {}
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Scan frame overlay
            ScanFrameOverlay()

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Quét mã vạch",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Instruction text
            Text(
                text = "Hướng camera vào mã vạch sản phẩm",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )

            // Loading overlay
            if (uiState.isLookingUp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Đang tra cứu sản phẩm...", color = Color.White)
                    }
                }
            }
        }
    }

    // Khung hiển thị kết quả
    if (uiState.showResultSheet && uiState.lookupResult != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissResultSheet() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "✅ Sản phẩm tìm thấy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mã vạch: ${uiState.detectedBarcode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = uiState.lookupResult!!.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${uiState.lookupResult!!.calories}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("kcal/100g", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${uiState.lookupResult!!.protein}g",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text("protein/100g", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                GradientButton(
                    text = "Thêm vào kho",
                    onClick = { viewModel.confirmResult() },
                    icon = Icons.Filled.Add
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { viewModel.resetScan() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Quét mã khác") }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Hộp thoại không tìm thấy
    if (uiState.showNotFoundDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNotFoundDialog() },
            icon = { Icon(Icons.Filled.SearchOff, contentDescription = null) },
            title = { Text("Không tìm thấy sản phẩm") },
            text = {
                Text("Mã vạch: ${uiState.detectedBarcode}\n\nSản phẩm này chưa có trong cơ sở dữ liệu. Bạn có muốn nhập thông tin thủ công?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.navigateManualEntry() }) {
                    Text("Nhập thủ công")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissNotFoundDialog() }) {
                    Text("Quét lại")
                }
            }
        )
    }
}

/**
 * Scan frame overlay: semi-transparent background + scan window ở giữa.
 */
@Composable
// Khung Quét
private fun ScanFrameOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanWidth = size.width * 0.75f
        val scanHeight = 200f
        val left = (size.width - scanWidth) / 2
        val top = (size.height - scanHeight) / 2

        // Semi-transparent overlay
        // Top
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset.Zero,
            size = Size(size.width, top)
        )
        // Bottom
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, top + scanHeight),
            size = Size(size.width, size.height - top - scanHeight)
        )
        // Left
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, top),
            size = Size(left, scanHeight)
        )
        // Right
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(left + scanWidth, top),
            size = Size(size.width - left - scanWidth, scanHeight)
        )

        // Corner lines
        val cornerLength = 30f
        val cornerColor = Color(0xFF4CAF50)
        val strokeWidth = 4f

        // Top-left corner
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
        // Top-right corner
        drawLine(cornerColor, Offset(left + scanWidth - cornerLength, top), Offset(left + scanWidth, top), strokeWidth)
        drawLine(cornerColor, Offset(left + scanWidth, top), Offset(left + scanWidth, top + cornerLength), strokeWidth)
        // Bottom-left corner
        drawLine(cornerColor, Offset(left, top + scanHeight - cornerLength), Offset(left, top + scanHeight), strokeWidth)
        drawLine(cornerColor, Offset(left, top + scanHeight), Offset(left + cornerLength, top + scanHeight), strokeWidth)
        // Bottom-right corner
        drawLine(cornerColor, Offset(left + scanWidth, top + scanHeight - cornerLength), Offset(left + scanWidth, top + scanHeight), strokeWidth)
        drawLine(cornerColor, Offset(left + scanWidth - cornerLength, top + scanHeight), Offset(left + scanWidth, top + scanHeight), strokeWidth)
    }
}

/**
 * ML Kit Barcode Analyzer cho CameraX ImageAnalysis.
 */
private class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A
            )
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    //Hàm này chạy liên tục
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close() // Giải phóng frame hình nếu rỗng
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage, imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { value ->
                    onBarcodeDetected(value) // Bắn mã vạch tìm được về ViewModel
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
