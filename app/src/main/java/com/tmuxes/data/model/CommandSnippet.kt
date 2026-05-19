package com.tmuxes.data.model

/**
 * A reusable command. Lives inside a [SnippetLibrary] by composition;
 * does not carry a libraryId field — its containing library is its
 * unique parent.
 */
data class CommandSnippet(
    val id: Long,
    val name: String,
    val command: String,
    val isEnabled: Boolean = true,
    val isFavorited: Boolean = false,
    val sortOrder: Int = 0
)
