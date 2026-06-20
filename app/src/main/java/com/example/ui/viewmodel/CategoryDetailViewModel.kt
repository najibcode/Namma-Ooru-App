package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreShopRepository
import com.example.domain.Shop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class CategoryDetailUiState(
    val isLoading: Boolean = true,
    val shops: List<Shop> = emptyList(),
    val errorMessage: String? = null
)

class CategoryDetailViewModel : ViewModel() {
    private val repository = FirestoreShopRepository.instance

    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    fun loadShopsByCategory(categoryName: String) {
        repository.getShopsByCategory(categoryName)
            .onEach { shops ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        shops = shops,
                        errorMessage = null
                    )
                }
            }
            .catch { e ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = "தகவல்களை ஏற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும்."
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}
