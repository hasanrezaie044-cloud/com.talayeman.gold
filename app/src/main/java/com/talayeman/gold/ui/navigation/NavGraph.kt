package com.talayeman.gold.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.talayeman.gold.ui.screens.assets.AssetDetailScreen
import com.talayeman.gold.ui.screens.assets.AssetEditScreen
import com.talayeman.gold.ui.screens.assets.AssetsScreen
import com.talayeman.gold.ui.screens.calculator.CalculatorScreen
import com.talayeman.gold.ui.screens.home.HomeScreen
import com.talayeman.gold.ui.screens.invoices.InvoicesScreen
import com.talayeman.gold.ui.screens.market.MarketScreen
import com.talayeman.gold.ui.screens.reports.ReportsScreen
import com.talayeman.gold.ui.screens.settings.SettingsScreen

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "خانه", Icons.Filled.Home, Icons.Outlined.Home)
    data object Assets : Screen("assets", "دارایی‌ها", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    data object Market : Screen("market", "قیمت بازار", Icons.Filled.ShowChart, Icons.Outlined.ShowChart)
    data object Calculator : Screen("calculator", "ماشین حساب", Icons.Filled.Calculate, Icons.Outlined.Calculate)
    data object Settings : Screen("settings", "تنظیمات", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Assets,
    Screen.Market,
    Screen.Calculator,
    Screen.Settings
)

@Composable
fun GoldNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Assets.route) {
            AssetsScreen(navController)
        }
        composable(Screen.Market.route) {
            MarketScreen(navController)
        }
        composable(Screen.Calculator.route) {
            CalculatorScreen(navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }
        composable(
            route = "asset_detail/{assetId}",
            arguments = listOf(navArgument("assetId") { type = NavType.LongType })
        ) { backStack ->
            val id = backStack.arguments?.getLong("assetId") ?: 0L
            AssetDetailScreen(navController, id)
        }
        composable(
            route = "asset_edit/{assetId}",
            arguments = listOf(navArgument("assetId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStack ->
            val id = backStack.arguments?.getLong("assetId") ?: -1L
            AssetEditScreen(navController, if (id < 0) null else id)
        }
        composable("invoices") {
            InvoicesScreen(navController)
        }
        composable("reports") {
            ReportsScreen(navController)
        }
    }
}
