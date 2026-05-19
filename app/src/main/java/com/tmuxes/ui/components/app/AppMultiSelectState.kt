package com.tmuxes.ui.components.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Multi-select state shared by screens with batch operations
 * (Snippets / LibraryDetail). Pair with [AppMultiSelectTopBar].
 */
@Stable
class AppMultiSelectState {
    var isActive by mutableStateOf(false)
        private set

    private var _selectedKeys by mutableStateOf(emptySet<Any>())
    val selectedKeys: Set<Any> get() = _selectedKeys
    val selectedCount: Int get() = _selectedKeys.size

    fun activate(initialKey: Any? = null) {
        isActive = true
        _selectedKeys = if (initialKey != null) setOf(initialKey) else emptySet()
    }

    fun deactivate() {
        isActive = false
        _selectedKeys = emptySet()
    }

    fun toggleItem(key: Any) {
        _selectedKeys = if (key in _selectedKeys) _selectedKeys - key else _selectedKeys + key
    }

    fun isSelected(key: Any): Boolean = key in _selectedKeys

    fun selectAll(keys: Collection<Any>) {
        _selectedKeys = _selectedKeys + keys
    }

    fun deselectAll() {
        _selectedKeys = emptySet()
    }

    fun isAllSelected(totalKeys: Collection<Any>): Boolean =
        totalKeys.isNotEmpty() && _selectedKeys.containsAll(totalKeys)
}

@Composable
fun rememberAppMultiSelectState(): AppMultiSelectState = remember { AppMultiSelectState() }
