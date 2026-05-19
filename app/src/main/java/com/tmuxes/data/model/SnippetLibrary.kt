package com.tmuxes.data.model

/**
 * A snippet library — a category of commands. Owns its [snippets] by
 * value (composition). NO foreign-key style libraryId on the snippet
 * side; navigation is through this object.
 */
data class SnippetLibrary(
    val id: Long,
    val name: String,
    val description: String = "",
    val iconName: String = "Code",
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val snippets: List<CommandSnippet> = emptyList()
)
