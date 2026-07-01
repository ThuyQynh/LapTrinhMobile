package com.team.smartnutrition.pantry

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.team.smartnutrition.navigation.Screen
import com.team.smartnutrition.pantry.viewmodel.CameraCaptureViewModel

/**
 * Module 2 - TV2: Chụp ảnh thực phẩm
 *
 * CameraX Preview toàn màn hình + nút chụp.
 * Sau khi chụp → gửi Gemini Vision API → navigate FoodResult.
 */
@Composable
fun CameraCaptureScreen(
    navController: NavController,
    viewModel: CameraCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // Cấu hình use case chụp ảnh (ImageCapture) của CameraX
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

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
        if (uiState.navigateToResult && uiState.resultJson != null) {
            navController.currentBackStackEntry?.savedStateHandle?.apply {
                set("food_result_json", uiState.resultJson)
                set("food_source", "camera")
            }
            navController.navigate(Screen.FoodResult.route)
            viewModel.onNavigated()
        }
    }

    // Giao diện UI
    if (!uiState.hasCameraPermission) {
        // Màn hình hiển thị khi quyền truy cập camera bị từ chối
        PermissionDeniedScreen(
            onBack = { navController.popBackStack() },
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // View hiển thị luồng Camera trước (Camera Preview)
            val previewView = remember { PreviewView(context) }

            // Cài đặt cấu hình camera trong LaunchedEffect sử dụng cơ chế bất đồng bộ await()
            LaunchedEffect(uiState.hasCameraPermission) {
                if (!uiState.hasCameraPermission) return@LaunchedEffect
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(context).await()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (_: Exception) {
                    // Liên kết camera thất bại
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Thanh công cụ phía trên hiển thị đè lên màn hình camera (Top bar overlay)
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
                    text = "Chụp ảnh thực phẩm",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                // Khoảng trống giả lập để giữ cân đối bố cục (Placeholder)
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Các nút bấm điều khiển ở hàng dưới (chụp ảnh, quét mã vạch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Khoảng đệm bên trái để cân đối nút chụp nằm giữa
                Spacer(modifier = Modifier.size(56.dp))

                // Nút chụp ảnh thực phẩm
                IconButton(
                    onClick = {
                        if (!uiState.isProcessing) {
                            capturePhoto(imageCapture, context) { bitmap ->
                                viewModel.onPhotoCaptured(bitmap)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color.White
                    ) {}
                }

                // Nút chuyển hướng sang màn hình quét mã vạch
                IconButton(
                    onClick = { navController.navigate(Screen.BarcodeScan.route) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Quét mã vạch",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Lớp phủ hiển thị trạng thái đang xử lý và nhận diện ảnh bằng AI (Loading screen)
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "🤖 AI đang nhận diện...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Thanh thông báo khi xảy ra lỗi (Snackbar)
            if (uiState.errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Đóng", color = Color.White)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(uiState.errorMessage!!)
                }
            }
        }
    }
}

/**
 * Chụp ảnh từ CameraX ImageCapture.
 */
private fun capturePhoto(
    imageCapture: ImageCapture,
    context: android.content.Context,
    onCaptured: (Bitmap) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxy.toBitmap()
                // Xoay ảnh theo rotation nếu cần
                val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                imageProxy.close()
                onCaptured(rotatedBitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                // Xử lý khi lỗi chụp ảnh xảy ra và cập nhật trạng thái lỗi
            }
        }
    )
}

/**
 * Xoay bitmap theo rotationDegrees từ ImageProxy.
 */
private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap

    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Màn hình khi quyền camera bị từ chối.
 */
@Composable
private fun PermissionDeniedScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.CameraAlt,
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
                text = "Ứng dụng cần quyền camera để chụp ảnh thực phẩm và nhận diện bằng AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Cấp quyền Camera")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBack) {
                Text("Quay lại")
            }
        }
    }
}
