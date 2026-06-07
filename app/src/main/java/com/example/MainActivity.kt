package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.BottomNavBar
import com.example.ui.navigation.Home
import com.example.ui.navigation.Orders
import com.example.ui.navigation.Help
import com.example.ui.navigation.Order
import com.example.ui.navigation.Success
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.HelpScreen
import com.example.ui.screens.OrderScreen
import com.example.ui.screens.SuccessScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                
                // Track current route conceptually 
                val currentRoute = when (navBackStackEntry?.destination?.route) {
                    Home.route -> "Home"
                    Orders.route -> "Orders"
                    Help.route -> "Help"
                    else -> "Other"
                }
                
                // Show Bottom Nav on Home, Orders, and Help tabs
                val showBottomNav = currentRoute == "Home" || currentRoute == "Orders" || currentRoute == "Help"
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomNav) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { destination ->
                                    when (destination) {
                                        "Home" -> navController.navigate(Home.route) {
                                            popUpTo(Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        "Orders" -> navController.navigate(Orders.route) {
                                            popUpTo(Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        "Help" -> navController.navigate(Help.route) {
                                            popUpTo(Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Home.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Home.route) {
                            HomeScreen(
                                innerPadding = innerPadding,
                                onCategorySelected = { shopId ->
                                    navController.navigate(Order.createRoute(shopId))
                                }
                            )
                        }

                        composable(Orders.route) {
                            OrdersScreen(innerPadding = innerPadding)
                        }

                        composable(Help.route) {
                            HelpScreen(innerPadding = innerPadding)
                        }
                        
                        composable(Order.route) { backStackEntry ->
                            val shopId = backStackEntry.arguments?.getString("shopId") ?: "1"
                            OrderScreen(
                                shopId = shopId,
                                innerPadding = innerPadding,
                                onSuccessOrder = { id, items, total ->
                                    navController.navigate(Success.createRoute(id, items, total)) {
                                        popUpTo(Home.route) { inclusive = false }
                                    }
                                }
                            )
                        }
                        
                        composable(Success.route) { backStackEntry ->
                            val items = backStackEntry.arguments?.getString("orderItemsStr") ?: "04"
                            val total = backStackEntry.arguments?.getString("totalCostStr") ?: "₹850.00"
                            SuccessScreen(
                                itemsInfo = items,
                                totalInfo = total,
                                innerPadding = innerPadding,
                                onNavigateHome = {
                                    navController.navigate(Home.route) {
                                        popUpTo(0)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
