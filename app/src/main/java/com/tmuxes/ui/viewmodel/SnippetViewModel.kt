package com.tmuxes.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmuxes.TmuxesApp
import com.tmuxes.data.model.CommandSnippet
import com.tmuxes.data.model.EnabledSnippet
import com.tmuxes.data.model.SnippetLibrary
import com.tmuxes.data.model.SnippetsConfig
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.util.AppLogger
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * YAML-native snippet ViewModel. The repository's [SnippetsConfig]
 * StateFlow is the single source of truth; UI consumers get derived
 * StateFlows ([libraries] / [enabledSnippets]) and call mutator
 * functions that delegate to atomic transforms in the repository.
 *
 * All snippet-scoped operations REQUIRE a libraryId — the data model
 * is tree-shaped (library owns snippets), so navigation must include
 * the parent. UI screens always have library context available.
 */
class SnippetViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as TmuxesApp).snippetRepository

    /** Single source: full document state (rare to use; prefer derived flows). */
    val config: StateFlow<SnippetsConfig> = repo.config

    /** Sorted libraries (by sortOrder, name). */
    val libraries: StateFlow<List<SnippetLibrary>> = repo.libraries
        .catch { e ->
            AppLogger.w(AppLogger.Category.DB) { "SnippetVM libraries flow error: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Snippets visible in the command panel: library AND snippet both enabled. */
    val enabledSnippets: StateFlow<List<EnabledSnippet>> = repo.enabledSnippets
        .catch { e ->
            AppLogger.w(AppLogger.Category.DB) { "SnippetVM enabledSnippets flow error: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -----------------------------------------------------------------
    // Error + undo + last-created scratch state
    // -----------------------------------------------------------------

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    /** Last-deleted snippet WITH its library context (for undo). */
    private val _lastDeletedSnippet = MutableStateFlow<Pair<Long, CommandSnippet>?>(null)
    val lastDeletedSnippet: StateFlow<Pair<Long, CommandSnippet>?> = _lastDeletedSnippet.asStateFlow()

    fun undoDeleteSnippet() {
        val (libraryId, snippet) = _lastDeletedSnippet.value ?: return
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                repo.addSnippet(libraryId, snippet)
                _lastDeletedSnippet.value = null
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to undo: {error}", "error" to e.message)
            }
        }
    }

    fun clearLastDeletedSnippet() { _lastDeletedSnippet.value = null }

    private val _lastCreatedLibraryId = MutableStateFlow<Long?>(null)
    val lastCreatedLibraryId: StateFlow<Long?> = _lastCreatedLibraryId.asStateFlow()

    fun clearLastCreatedLibraryId() { _lastCreatedLibraryId.value = null }

    // -----------------------------------------------------------------
    // Library actions
    // -----------------------------------------------------------------

    fun addLibrary(library: SnippetLibrary) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                val id = repo.addLibrary(library)
                _lastCreatedLibraryId.value = id
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to add library: {error}", "error" to e.message)
            }
        }
    }

    fun updateLibrary(library: SnippetLibrary) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                // Preserve existing snippets — caller passes only the library
                // metadata; snippets list comes from the existing record.
                repo.updateLibrary(library.id) {
                    library.copy(snippets = this.snippets)
                }
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to update library: {error}", "error" to e.message)
            }
        }
    }

    fun deleteLibrary(library: SnippetLibrary) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                repo.deleteLibrary(library.id)
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to delete library: {error}", "error" to e.message)
            }
        }
    }

    fun toggleLibraryEnabled(libraryId: Long) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                repo.updateLibrary(libraryId) { copy(isEnabled = !isEnabled) }
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to toggle library: {error}", "error" to e.message)
            }
        }
    }

    fun reorderLibraries(orderedIds: List<Long>) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.reorderLibraries(orderedIds) }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to reorder: {error}", "error" to e.message) }
        }
    }

    // -----------------------------------------------------------------
    // Snippet actions (always library-scoped)
    // -----------------------------------------------------------------

    fun addSnippet(libraryId: Long, snippet: CommandSnippet) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.addSnippet(libraryId, snippet) }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to add snippet: {error}", "error" to e.message) }
        }
    }

    fun updateSnippet(libraryId: Long, snippet: CommandSnippet) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.updateSnippet(libraryId, snippet.id) { snippet } }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to update snippet: {error}", "error" to e.message) }
        }
    }

    fun deleteSnippet(libraryId: Long, snippet: CommandSnippet) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                repo.deleteSnippet(libraryId, snippet.id)
                _lastDeletedSnippet.value = libraryId to snippet
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to delete snippet: {error}", "error" to e.message)
            }
        }
    }

    fun toggleSnippetEnabled(libraryId: Long, snippetId: Long) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.updateSnippet(libraryId, snippetId) { copy(isEnabled = !isEnabled) } }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to toggle snippet: {error}", "error" to e.message) }
        }
    }

    fun toggleSnippetFavorited(libraryId: Long, snippetId: Long) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.updateSnippet(libraryId, snippetId) { copy(isFavorited = !isFavorited) } }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to toggle favorite: {error}", "error" to e.message) }
        }
    }

    fun moveSnippet(fromLibraryId: Long, snippetId: Long, toLibraryId: Long) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.moveSnippet(fromLibraryId, snippetId, toLibraryId) }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to move snippet: {error}", "error" to e.message) }
        }
    }

    fun duplicateSnippet(
        libraryId: Long,
        snippet: CommandSnippet,
        targetLibraryId: Long? = null
    ) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try {
                val copy = snippet.copy(id = 0L, name = "${snippet.name} (copy)")
                repo.addSnippet(targetLibraryId ?: libraryId, copy)
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to duplicate snippet: {error}", "error" to e.message)
            }
        }
    }

    fun reorderSnippets(libraryId: Long, orderedIds: List<Long>) {
        viewModelScope.safeLaunch(tag = "SnippetVM") {
            try { repo.reorderSnippets(libraryId, orderedIds) }
            catch (e: Exception) { _errorMessage.value = I18nRuntime.t("Failed to reorder: {error}", "error" to e.message) }
        }
    }
}
