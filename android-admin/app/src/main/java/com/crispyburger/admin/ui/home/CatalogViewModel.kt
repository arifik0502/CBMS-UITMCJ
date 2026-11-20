package com.crispyburger.admin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminDataBus
import com.crispyburger.admin.data.AdminProduct
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.ProductValidation
import com.crispyburger.admin.data.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogDraft(
    val product: AdminProduct,
    val sku: String,
    val name: String,
    val description: String,
    val price: String
) {
    val priceCents: Int? get() = ProductValidation.priceCents(price)
    val isValid: Boolean
        get() = ProductValidation.normalizeSku(sku) != null &&
            name.trim().length in 2..80 &&
            description.trim().length <= 500 &&
            priceCents != null
}

data class CatalogUiState(
    val products: List<AdminProduct> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val draft: CatalogDraft? = null
)

class CatalogViewModel(private val api: AdminApi = AdminSupabase) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var loaded = false
    private var loadJob: Job? = null

    fun ensureLoaded() {
        if (!loaded) refresh()
    }

    fun refresh() {
        loaded = true
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { api.loadProducts() }
                .onSuccess { list -> _state.update { it.copy(products = list, isLoading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = api.friendlyError(e, "Could not load product catalogue."))
                    }
                }
        }
    }

    fun edit(product: AdminProduct) = _state.update {
        it.copy(
            draft = CatalogDraft(
                product = product,
                sku = product.sku,
                name = product.name,
                description = product.description,
                price = ProductValidation.formatPrice(product.priceCents)
            ),
            error = null,
            success = null
        )
    }

    fun updateDraft(transform: (CatalogDraft) -> CatalogDraft) =
        _state.update { s -> s.copy(draft = s.draft?.let(transform)) }

    fun dismissDraft() = _state.update { it.copy(draft = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        val cents = draft.priceCents ?: return
        if (!draft.isValid || _state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = null) }
            val result = runCatchingCancellable {
                api.updateProductDetails(draft.product.id, draft.sku, draft.name, draft.description, cents)
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = api.friendlyError(
                            result.exceptionOrNull() ?: IllegalStateException(),
                            "Could not update the product. Check the SKU and developer permissions."
                        )
                    )
                }
                return@launch
            }
            _state.update { it.copy(isSaving = false, draft = null, success = "Product details updated.") }
            AdminDataBus.emit(AdminDataBus.Kind.PRODUCTS, origin = this@CatalogViewModel)
            loadJob = null
            refresh()
        }
    }

    fun reset() {
        loadJob?.cancel()
        loaded = false
        _state.value = CatalogUiState()
    }
}
