package com.example.ui.navigation

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
