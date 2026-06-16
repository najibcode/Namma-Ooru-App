package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreShopRepository
import com.example.domain.Shop
import com.example.domain.ShopCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

// ══════════════════════════════════════════════════════════════════════════════
// HomeViewModel.kt — Firestore-backed shop directory for நம்ம ஊரு ஆப்
//
// Responsibilities:
//   • Own the FirestoreShopRepository lifecycle.
//   • Expose grouped shop lists (by category) as StateFlows for the Home screen.
//   • Track loading / error state for the real-time snapshot listener.
//
// Real-time updates:
//   The Firestore snapshot listener keeps [shops] current automatically.
//   Any merchant subscription change (isSubscribed toggle) pushed from the
//   Firebase console is reflected on the Home screen within seconds.
// ══════════════════════════════════════════════════════════════════════════════

private const val TAG = "HomeViewModel"

data class HomeUiState(
    val isLoading: Boolean       = true,
    val allShops: List<Shop>     = emptyList(),
    val errorMessage: String?    = null
) {
    // Convenience accessors keyed by ShopCategory
    val hotelShops: List<Shop>   get() = allShops.filter { it.category == ShopCategory.Hotel.labelTamil }
    val medicalShops: List<Shop> get() = allShops.filter { it.category == ShopCategory.Medical.labelTamil }
    val groceryShops: List<Shop> get() = allShops.filter { it.category == ShopCategory.Grocery.labelTamil }
    val meatShops: List<Shop>    get() = allShops.filter { it.category == ShopCategory.Meat.labelTamil }
}

class HomeViewModel : ViewModel() {

    private val shopRepository = FirestoreShopRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeShops()
    }

    private fun observeShops() {
        shopRepository.shops
            .onEach { shops ->
                Log.d(TAG, "Received ${shops.size} shops from repository")
                _uiState.update { current ->
                    current.copy(
                        isLoading    = false,
                        allShops     = shops,
                        errorMessage = null
                    )
                }
            }
            .catch { e ->
                Log.e(TAG, "Error observing shops: ${e.message}", e)
                _uiState.update { current ->
                    current.copy(
                        isLoading    = false,
                        errorMessage = "கடை தகவல்களை ஏற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும்."
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Returns the first subscribed shop in the given category (for the Order screen). */
    fun getFirstShopByCategory(category: ShopCategory): Shop? =
        _uiState.value.allShops.firstOrNull {
            it.category == category.labelTamil && it.isSubscribed
        }

    /** Returns a shop by its document ID. */
    fun getShopById(id: String): Shop? =
        _uiState.value.allShops.find { it.id == id }
}
