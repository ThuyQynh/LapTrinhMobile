package com.team.smartnutrition

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.team.smartnutrition.common.components.LoadingScreen
import com.team.smartnutrition.navigation.BottomNavBar
import com.team.smartnutrition.navigation.Screen
import com.team.smartnutrition.navigation.SmartNutritionNavHost
import com.team.smartnutrition.navigation.bottomNavItems
import com.team.smartnutrition.ui.theme.SmartNutritionTheme
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * MainActivity - Activity DUY NHẤT của app.
 * Tất cả UI được quản lý bằng Compose Navigation.
 * KHÔNG CẦN TẠO THÊM Activity khác.
 *
 * Auth Flow:
 * 1. App launch → check currentUser
 * 2. null → Login screen
 * 3. exists → check Firestore profile → Home hoặc ProfileSetup
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val appPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
            
            var isDarkModeState by remember { 
                mutableStateOf(appPrefs.getBoolean("dark_mode", false)) 
            }
            var languageState by remember {
                mutableStateOf(appPrefs.getString("language", "vi") ?: "vi")
            }
            
            val listener = remember {
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "dark_mode" -> {
                            isDarkModeState = appPrefs.getBoolean("dark_mode", false)
                        }
                        "language" -> {
                            languageState = appPrefs.getString("language", "vi") ?: "vi"
                        }
                    }
                }
            }
            
            DisposableEffect(appPrefs) {
                appPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    appPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val locale = remember(languageState) { Locale(languageState) }
            val config = remember(languageState) { 
                val c = android.content.res.Configuration(context.resources.configuration)
                c.setLocale(locale)
                c.setLayoutDirection(locale)
                c
            }
            val localizedContext = remember(languageState) {
                LocalizedContext(context, config)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides config
            ) {
                SmartNutritionTheme(darkTheme = isDarkModeState) {
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    var isCheckingAuth by remember { mutableStateOf(true) }

                    // Check auth state on launch
                    LaunchedEffect(Unit) {
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        startDestination = when {
                            currentUser == null -> Screen.Login.route
                            else -> {
                                // Check if user has profile in Firestore
                                try {
                                    val doc = FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(currentUser.uid)
                                        .get()
                                        .await()
                                    if (doc.exists()) Screen.Home.route
                                    else Screen.ProfileSetup.route
                                } catch (e: Exception) {
                                    // Offline or error → go to Home (Firestore offline cache may help)
                                    Screen.Home.route
                                }
                            }
                        }
                        isCheckingAuth = false
                    }

                    if (isCheckingAuth) {
                        LoadingScreen(message = androidx.compose.ui.res.stringResource(R.string.checking_auth))
                    } else {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        // Chỉ hiện Bottom Nav khi ở các tab chính
                        val showBottomBar = currentRoute in bottomNavItems.map { it.route }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                if (showBottomBar) {
                                    BottomNavBar(
                                        navController = navController,
                                        currentRoute = currentRoute
                                    )
                                }
                            }
                        ) { innerPadding ->
                            SmartNutritionNavHost(
                                navController = navController,
                                modifier = Modifier.padding(innerPadding),
                                startDestination = startDestination ?: Screen.Login.route
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * LocalizedContext - Wrapper bảo toàn base context của Activity để các Compose Locals
 * (như ActivityResultRegistryOwner, OnBackPressedDispatcherOwner) hoạt động bình thường,
 * đồng thời cung cấp tài nguyên dịch (localized resources).
 */
class LocalizedContext(
    base: Context, 
    private val config: android.content.res.Configuration
) : android.content.ContextWrapper(base) {
    private val localizedContext: Context by lazy {
        base.createConfigurationContext(config)
    }

    override fun getResources(): android.content.res.Resources {
        return localizedContext.resources
    }

    override fun getAssets(): android.content.res.AssetManager {
        return localizedContext.assets
    }
}
