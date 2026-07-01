package com.team.smartnutrition.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.team.smartnutrition.analytics.AnalyticsScreen
import com.team.smartnutrition.analytics.SettingsScreen
import com.team.smartnutrition.auth.LoginScreen
import com.team.smartnutrition.auth.ProfileSetupScreen
import com.team.smartnutrition.auth.ProfileViewScreen
import com.team.smartnutrition.auth.RegisterScreen
import com.team.smartnutrition.auth.WeightLogScreen
import com.team.smartnutrition.habit.HabitDashboardScreen
import com.team.smartnutrition.habit.ReminderSettingsScreen
import com.team.smartnutrition.meal.MealDetailScreen
import com.team.smartnutrition.meal.MealPlanWeekScreen
import com.team.smartnutrition.pantry.BarcodeScanScreen
import com.team.smartnutrition.pantry.CameraCaptureScreen
import com.team.smartnutrition.pantry.FoodDetailScreen
import com.team.smartnutrition.pantry.FoodResultScreen
import com.team.smartnutrition.pantry.PantryListScreen
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team.smartnutrition.meal.viewmodel.MealPlanViewModel

/**
 * NAVIGATION GRAPH - Bản đồ điều hướng toàn app
 *
 * startDestination = "login" → Mở app → Màn hình Login
 * Sau khi login thành công → navigate("home")
 *
 * CÁCH THÊM MÀN HÌNH MỚI:
 * 1. Thêm route vào Screen.kt
 * 2. Thêm composable() vào đây
 * 3. Navigate bằng navController.navigate(Screen.XYZ.route)
 */
@Composable
fun SmartNutritionNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // AUTH FLOW (Module 1 - TV1)
        // Không có Bottom Nav
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(navController = navController)
        }
        // TAB 1: TRANG CHỦ (Module 1 - TV1)
        composable(Screen.Home.route) {
            ProfileViewScreen(navController = navController)
        }
        composable(Screen.WeightLog.route) {
            WeightLogScreen(navController = navController)
        }
        composable(Screen.ProfileView.route) {
            ProfileViewScreen(navController = navController)
        }
        // TAB 2: KHO THỰC PHẨM (Module 2 - TV2)
        composable(Screen.Pantry.route) {
            PantryListScreen(navController = navController)
        }
        composable(Screen.CameraCapture.route) {
            CameraCaptureScreen(navController = navController)
        }
        composable(Screen.FoodResult.route) {
            FoodResultScreen(navController = navController)
        }
        composable(Screen.BarcodeScan.route) {
            BarcodeScanScreen(navController = navController)
        }
        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            FoodDetailScreen(navController = navController, itemId = itemId)
        }
        // TAB 3: THỰC ĐƠN AI (Module 3 - TV3)
        composable(Screen.MealPlan.route) { backStackEntry ->
            val mealViewModel: MealPlanViewModel = viewModel(backStackEntry)
            MealPlanWeekScreen(navController = navController, viewModel = mealViewModel)
        }
        composable(
            route = Screen.MealDetail.route,
            arguments = listOf(
                navArgument("dayIndex") { type = NavType.IntType },
                navArgument("mealType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val dayIndex = backStackEntry.arguments?.getInt("dayIndex") ?: 0
            val mealType = backStackEntry.arguments?.getString("mealType") ?: ""
            
            // Lấy NavBackStackEntry của màn hình cha (MealPlan) để dùng chung ViewModel
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.MealPlan.route)
            }
            val mealViewModel: MealPlanViewModel = viewModel(parentEntry)
            
            MealDetailScreen(
                navController = navController,
                dayIndex = dayIndex,
                mealType = mealType,
                viewModel = mealViewModel
            )
        }
        // TAB 4: THÓI QUEN (Module 4 - TV4)
        composable(Screen.Habit.route) {
            HabitDashboardScreen(navController = navController)
        }
        composable(Screen.ReminderSettings.route) {
            ReminderSettingsScreen(navController = navController)
        }
        // TAB 5: THỐNG KÊ & CÀI ĐẶT (Module 5 - TV5)
        composable(Screen.Analytics.route) {
            AnalyticsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
