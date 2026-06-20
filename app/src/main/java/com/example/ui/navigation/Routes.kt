package com.example.ui.navigation

object Splash { val route = "Splash" }
object Onboarding { val route = "Onboarding" }
object Home { val route = "Home" }
object Orders { val route = "Orders" }
object Help { val route = "Help" }
object Order {
    val route = "Order/{shopId}"
    fun createRoute(shopId: String) = "Order/$shopId"
}
object Success {
    val route = "Success/{shopId}/{orderItemsStr}/{totalCostStr}"
    fun createRoute(shopId: String, orderItemsStr: String, totalCostStr: String) = "Success/$shopId/$orderItemsStr/$totalCostStr"
}
object CategoryDetail {
    val route = "CategoryDetail/{categoryName}"
    fun createRoute(categoryName: String) = "CategoryDetail/$categoryName"
}

