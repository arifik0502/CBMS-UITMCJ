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
    val product: AdminProduct?,
    val sku: String,
    val name: String,
    val description: String,
    val price: String,
    val imageUrl: String?,
    val isActive: Boolean,
    val initialStock: String = "0"
) {
    val isNew: Boolean get() = product == null
    val priceCents: Int? get() = ProductValidation.priceCents(price)
    val initialStockValue: Int? get() = initialStock.toIntOrNull()?.takeIf { it in 0..100_000 }
    val isValid: Boolean
        get() = ProductValidation.normalizeSku(sku) != null &&
            name.trim().length in 2..80 &&
            description.trim().length <= 500 &&
            priceCents != null &&
            (!isNew || initialStockValue != null)
}

data class CatalogUiState(
    val products: List<AdminProduct> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
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
                        it.copy(
                            isLoading = false,
                            error = api.friendlyError(e, "Could not load product catalogue.")
                        )
                    }
                }
        }
    }

    fun addNew() = _state.update {
        it.copy(
            draft = CatalogDraft(
                product = null,
                sku = "",
                name = "",
                description = "",
                price = "",
                imageUrl = null,
                isActive = true,
                initialStock = "0"
            ),
            error = null,
            success = null
        )
    }

    fun edit(product: AdminProduct) = _state.update {
        it.copy(
            draft = CatalogDraft(
                product = product,
                sku = product.sku,
                name = product.name,
                description = product.description,
                price = ProductValidation.formatPrice(product.priceCents),
                imageUrl = product.imageUrl,
                isActive = product.isActive
            ),
            error = null,
            success = null
        )
    }

    fun updateDraft(transform: (CatalogDraft) -> CatalogDraft) =
        _state.update { s -> s.copy(draft = s.draft?.let(transform)) }

    fun dismissDraft() {
        if (_state.value.isSaving || _state.value.isUploadingImage) return
        _state.update { it.copy(draft = null) }
    }

    fun pictureProblem(message: String) =
        _state.update { it.copy(error = message) }

    fun uploadPicture(jpeg: ByteArray) {
        if (_state.value.isUploadingImage || _state.value.draft == null) return
        viewModelScope.launch {
            _state.update { it.copy(isUploadingImage = true, error = null) }
            runCatchingCancellable { api.uploadProductImage(jpeg) }
                .onSuccess { url ->
                    _state.update { state ->
                        state.copy(
                            isUploadingImage = false,
                            draft = state.draft?.copy(imageUrl = url)
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isUploadingImage = false,
                            error = api.friendlyError(e, "Could not upload the product photo.")
                        )
                    }
                }
        }
    }

    fun save() {
        val draft = _state.value.draft ?: return
        val cents = draft.priceCents ?: return
        if (!draft.isValid || _state.value.isSaving || _state.value.isUploadingImage) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, success = null) }
            val result = runCatchingCancellable {
                if (draft.isNew) {
                    api.createProduct(
                        sku = draft.sku,
                        name = draft.name,
                        description = draft.description,
                        priceCents = cents,
                        imageUrl = draft.imageUrl,
                        initialStock = requireNotNull(draft.initialStockValue)
                    )
                } else {
                    api.updateProductDetails(
                        productId = requireNotNull(draft.product).id,
                        sku = draft.sku,
                        name = draft.name,
                        description = draft.description,
                        priceCents = cents,
                        imageUrl = draft.imageUrl,
                        isActive = draft.isActive
                    )
                }
            }

            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = api.friendlyError(
                            result.exceptionOrNull() ?: IllegalStateException(),
                            if (draft.isNew) "Could not add the product." else "Could not update the product."
                        )
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    draft = null,
                    success = if (draft.isNew) "Product added to the catalogue." else "Product details updated."
                )
            }
            AdminDataBus.emit(AdminDataBus.Kind.PRODUCTS, origin = this@CatalogViewModel)
            AdminDataBus.emit(AdminDataBus.Kind.INVENTORY, origin = this@CatalogViewModel)
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
