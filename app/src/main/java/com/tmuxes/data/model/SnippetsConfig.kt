package com.tmuxes.data.model

/**
 * Single in-memory representation of `snippets.yaml`. Persisted by
 * [com.tmuxes.data.repository.SnippetYamlRepository]. The full document
 * is the unit of mutation — all atomic transforms operate on this.
 */
data class SnippetsConfig(
    val libraries: List<SnippetLibrary> = emptyList()
)
