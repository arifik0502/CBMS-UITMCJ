package com.crispyburger.admin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crispyburger.admin.data.AdminApi
import com.crispyburger.admin.data.AdminSupabase
import com.crispyburger.admin.data.SiteConfig
import com.crispyburger.admin.data.SiteJson
import com.crispyburger.admin.data.SiteRules
import com.crispyburger.admin.data.SiteState
import com.crispyburger.admin.data.SiteVersion
import com.crispyburger.admin.data.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteUiState(
    val config: SiteConfig = SiteRules.DEFAULT_CONFIG,
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    /** Edits not yet stored as a draft on the server. */
    val dirty: Boolean = false,
    val isSaving: Boolean = false,
    val isPublishing: Boolean = false,
    val isUploading: Boolean = false,
    /** A draft exists that differs from what customers see. */
    val hasUnpublished: Boolean = false,
    val history: List<SiteVersion> = emptyList(),
    val error: String? = null,
    val info: String? = null
) {
    val problems: List<String> get() = SiteRules.problems(config)
    val canPublish: Boolean
        get() = loaded && problems.isEmpty() && !isPublishing && !isUploading && (hasUnpublished || dirty)
    val previewJson: String get() = SiteJson.encodeToString(SiteConfig.serializer(), config)
}

class SiteViewModel(
    private val api: AdminApi = AdminSupabase,
    private val autosaveDelayMs: Long = 1200L
) : ViewModel() {

    private val _state = MutableStateFlow(SiteUiState())
    val state: StateFlow<SiteUiState> = _state.asStateFlow()

    private var saveJob: Job? = null
    private var loadJob: Job? = null

    fun ensureLoaded() {
        if (!_state.value.loaded && loadJob?.isActive != true) refresh()
    }

    fun refresh() {
        saveJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { api.loadSiteState() }
                .onSuccess { applyServerState(it) }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = api.friendlyError(
                                e,
                                "Could not load the website settings. Check that the website customization migration has been applied."
                            )
                        )
                    }
                }
        }
    }

    private fun applyServerState(s: SiteState) {
        val base = s.draft ?: s.published ?: SiteRules.DEFAULT_CONFIG
        _state.update {
            it.copy(
                config = SiteRules.withAllBuiltIns(base),
                loaded = true,
                isLoading = false,
                dirty = false,
                hasUnpublished = s.hasUnpublished,
                error = null
            )
        }
    }

    /** Every change goes through here: update the working copy, then save it as a draft shortly after. */
    fun edit(transform: (SiteConfig) -> SiteConfig) {
        if (!_state.value.loaded) return
        _state.update { it.copy(config = transform(it.config), dirty = true, info = null) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(autosaveDelayMs)
            saveNow()
        }
    }

    private suspend fun saveNow(): Boolean {
        val snapshot = _state.value.config
        _state.update { it.copy(isSaving = true) }
        val result = runCatchingCancellable { api.saveSiteDraft(snapshot) }
        if (result.isFailure) {
            _state.update {
                it.copy(
                    isSaving = false,
                    error = api.friendlyError(
                        result.exceptionOrNull() ?: IllegalStateException(),
                        "Could not save your changes. Check your connection; they stay on this screen."
                    )
                )
            }
            return false
        }
        _state.update {
            it.copy(
                isSaving = false,
                // If the owner kept typing while this saved, the newer edit schedules its own save.
                dirty = it.config != snapshot,
                hasUnpublished = true,
                error = null
            )
        }
        return true
    }

    fun publish() {
        val s = _state.value
        if (!s.canPublish) return
        saveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isPublishing = true, error = null, info = null) }
            if (_state.value.dirty && !saveNow()) {
                _state.update { it.copy(isPublishing = false) }
                return@launch
            }
            val result = runCatchingCancellable { api.publishSite() }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isPublishing = false,
                        error = api.friendlyError(
                            result.exceptionOrNull() ?: IllegalStateException(),
                            "Could not publish. Check your connection and try again."
                        )
                    )
                }
                return@launch
            }
            runCatchingCancellable { api.loadSiteState() }.onSuccess { applyServerState(it) }
            _state.update { it.copy(isPublishing = false, hasUnpublished = false, info = "Published. Customers see the new website now.") }
        }
    }

    /** Throws away all unpublished changes and goes back to what customers see. */
    fun discard() {
        saveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, info = null) }
            val result = runCatchingCancellable { api.discardSiteDraft() }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = api.friendlyError(result.exceptionOrNull() ?: IllegalStateException(), "Could not undo the changes.")
                    )
                }
                return@launch
            }
            runCatchingCancellable { api.loadSiteState() }
                .onSuccess { applyServerState(it) }
                .onFailure { _state.update { s -> s.copy(isLoading = false) } }
            _state.update { it.copy(info = "Changes undone. Showing the live website.") }
        }
    }

    /** Puts the original design back into the editor (still a draft until published). */
    fun resetToOriginal() = edit { SiteRules.DEFAULT_CONFIG }

    fun uploadPicture(jpeg: ByteArray, onDone: (String) -> Unit) {
        if (_state.value.isUploading) return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null) }
            val result = runCatchingCancellable { api.uploadSiteImage(jpeg) }
            val url = result.getOrNull()
            if (url == null) {
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = api.friendlyError(
                            result.exceptionOrNull() ?: IllegalStateException(),
                            "Could not upload the picture. Check your connection and try again."
                        )
                    )
                }
                return@launch
            }
            _state.update { it.copy(isUploading = false) }
            onDone(url)
        }
    }

    fun pictureProblem(message: String) = _state.update { it.copy(error = message) }

    fun loadHistory() {
        viewModelScope.launch {
            runCatchingCancellable { api.loadSiteHistory() }
                .onSuccess { list -> _state.update { it.copy(history = list) } }
                .onFailure { e ->
                    _state.update { it.copy(error = api.friendlyError(e, "Could not load earlier versions.")) }
                }
        }
    }

    fun restore(id: String) {
        saveJob?.cancel()
        viewModelScope.launch {
            val result = runCatchingCancellable { api.restoreSiteVersion(id) }
            if (result.isFailure) {
                _state.update {
                    it.copy(error = api.friendlyError(result.exceptionOrNull() ?: IllegalStateException(), "Could not restore that version."))
                }
                return@launch
            }
            runCatchingCancellable { api.loadSiteState() }.onSuccess { applyServerState(it) }
            _state.update { it.copy(info = "Earlier version loaded as a draft. Preview it, then publish.") }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun reset() {
        saveJob?.cancel()
        loadJob?.cancel()
        _state.value = SiteUiState()
    }
}
