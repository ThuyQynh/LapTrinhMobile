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

    // Khởi tạo cấu hình ImageCapture 
    val imageCapture = remember {
        ImageCapture.Builder()
            // Ưu tiên giảm thiểu tối đa độ trễ khi nhấn nút chụp.
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

    // điều hướng màn hình 
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

    // Giao diện UI camera
    if (!uiState.hasCameraPermission) {
        // Trạng thái bị từ chối quyền truy cập
        PermissionDeniedScreen(
            onBack = { navController.popBackStack() },
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview
            val previewView = remember { PreviewView(context) }

            // Setup camera in LaunchedEffect to use await()
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
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // giao diện Thanh công cụ phía trên
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
                Spacer(modifier = Modifier.weight(1f)) // Đẩy text vào giữa
                Text(
                    text = "Chụp ảnh thực phẩm",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f)) // Đẩy spacer bên phải để cân bằng
                Spacer(modifier = Modifier.size(48.dp)) // Tạo khoảng trống giả để giữ Text ở giữa
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter) // Đặt cố định ở đáy màn hình
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(56.dp))

                // Capture button
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

                // Barcode button
                IconButton(
                    // Chuyển sang màn hình quét mã vạch
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

            // Processing overlay
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

            // Màu báo lỗi (Error) snackbar
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
                // Lỗi sẽ được hiện qua error state
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
