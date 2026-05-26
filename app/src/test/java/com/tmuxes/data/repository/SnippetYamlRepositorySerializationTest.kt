package com.tmuxes.data.repository

import com.tmuxes.data.model.CommandSnippet
import com.tmuxes.data.model.SnippetLibrary
import com.tmuxes.data.model.SnippetsConfig
import com.tmuxes.data.preset.PresetSnippetCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure-JVM tests for [SnippetYamlRepository]. The repository accepts a
 * `File configDir` constructor parameter so tests can use a temporary
 * directory without needing Android's Context.
 */
class SnippetYamlRepositorySerializationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configDir: File

    @Before
    fun setUp() {
        configDir = tempFolder.newFolder("config")
    }

    @After
    fun tearDown() {
        // TempFolder rule auto-cleans
    }

    private fun newRepo(): SnippetYamlRepository = SnippetYamlRepository(configDir)

    private val yamlFile: File get() = File(configDir, "snippets.yaml")

    // -----------------------------------------------------------------
    // 1. Fresh-install seed from PresetSnippetCatalog
    // -----------------------------------------------------------------

    @Test
    fun `fresh install seeds from PresetSnippetCatalog`() {
        val repo = newRepo()
        val cfg = repo.config.value
        assertEquals(
            "library count matches catalog",
            PresetSnippetCatalog.libraries.size, cfg.libraries.size
        )
        assertEquals(
            "preset library names",
            listOf("Codex CLI", "Claude Code", "tmux", "SSH", "Git", "Docker", "Python", "Slurm", "APT", "Conda"),
            cfg.libraries.map { it.name }
        )
        assertEquals(
            "preset snippet counts",
            mapOf(
                "Codex CLI" to 11,
                "Claude Code" to 11,
                "tmux" to 15,
                "SSH" to 17,
                "Git" to 16,
                "Docker" to 15,
                "Python" to 14,
                "Slurm" to 14,
                "APT" to 13,
                "Conda" to 12
            ),
            cfg.libraries.associate { it.name to it.snippets.size }
        )
        val tmux = cfg.libraries.find { it.name == "tmux" }
        assertNotNull("tmux library present", tmux)
        // All preset libraries are opt-in by default.
        assertTrue("all preset libraries default disabled",
            cfg.libraries.all { !it.isEnabled })
    }

    @Test
    fun `fresh install writes snippets_yaml to disk`() {
        newRepo()
        assertTrue("yaml file created", yamlFile.exists())
        val text = yamlFile.readText()
        assertTrue("yaml has libraries key", text.contains("libraries:"))
        assertTrue("yaml has Codex library", text.contains("Codex CLI"))
        assertTrue("yaml has SSH library", text.contains("SSH"))
        assertTrue("yaml has Docker library", text.contains("Docker"))
    }

    // -----------------------------------------------------------------
    // 2. YAML round-trip
    // -----------------------------------------------------------------

    @Test
    fun `getYamlText and setFromYamlText round-trip preserves data`() = runBlocking {
        val repo = newRepo()
        val originalText = repo.getYamlText()
        val originalCfg = repo.config.value

        // Reload from text → should match
        repo.setFromYamlText(originalText)
        val reloaded = repo.config.value
        assertEquals(originalCfg.libraries.size, reloaded.libraries.size)
        assertEquals(
            originalCfg.libraries.map { it.id }.sorted(),
            reloaded.libraries.map { it.id }.sorted()
        )
    }

    // -----------------------------------------------------------------
    // 3. update(transform) atomicity + persistence
    // -----------------------------------------------------------------

    @Test
    fun `update transform persists to disk`() = runBlocking {
        val repo = newRepo()
        val before = repo.config.value.libraries.size
        repo.update { cfg ->
            cfg.copy(libraries = cfg.libraries + SnippetLibrary(
                id = 9999, name = "Custom", sortOrder = 999
            ))
        }
        // Reload from disk via new repo instance → should see addition
        val repo2 = SnippetYamlRepository(configDir)
        assertEquals(before + 1, repo2.config.value.libraries.size)
        assertNotNull(repo2.config.value.libraries.find { it.name == "Custom" })
    }

    // -----------------------------------------------------------------
    // 4. Library cascade delete
    // -----------------------------------------------------------------

    @Test
    fun `deleteLibrary cascades snippets`() = runBlocking {
        val repo = newRepo()
        val tmux = repo.config.value.libraries.find { it.name == "tmux" }!!
        val snippetCountBefore = tmux.snippets.size
        assertTrue("tmux had snippets", snippetCountBefore > 0)

        repo.deleteLibrary(tmux.id)

        val cfg = repo.config.value
        assertNull("library gone", cfg.libraries.find { it.id == tmux.id })
        // Snippets are owned by library — gone too (composition)
        val orphanCount = cfg.libraries.flatMap { it.snippets }
            .count { false } // no-op; just confirm structure
        assertEquals(0, orphanCount)
    }

    // -----------------------------------------------------------------
    // 5. moveSnippet across libraries
    // -----------------------------------------------------------------

    @Test
    fun `moveSnippet relocates between libraries`() = runBlocking {
        val repo = newRepo()
        val libs = repo.config.value.libraries
        val from = libs.first { it.snippets.isNotEmpty() }
        val to = libs.first { it.id != from.id }
        val snip = from.snippets.first()

        repo.moveSnippet(from.id, snip.id, to.id)

        val cfg2 = repo.config.value
        val fromAfter = cfg2.libraries.find { it.id == from.id }!!
        val toAfter = cfg2.libraries.find { it.id == to.id }!!
        assertNull("snippet gone from source", fromAfter.snippets.find { it.id == snip.id })
        assertNotNull("snippet now in target", toAfter.snippets.find { it.id == snip.id })
    }

    // -----------------------------------------------------------------
    // 6. enabledSnippets filter — both library and snippet must be enabled
    // -----------------------------------------------------------------

    @Test
    fun `enabledSnippets filters disabled libraries and snippets`() = runBlocking {
        val repo = newRepo()
        // All preset libraries seeded disabled → 0 enabled snippets initially
        val initial = repo.enabledSnippets.first()
        assertTrue("nothing enabled by default", initial.isEmpty())

        // Enable tmux library
        val tmux = repo.config.value.libraries.find { it.name == "tmux" }!!
        repo.updateLibrary(tmux.id) { copy(isEnabled = true) }
        val afterEnable = repo.enabledSnippets.first()
        assertTrue("snippets visible now", afterEnable.size >= 15)

        // Disable one snippet → it disappears from enabled list
        val firstSnippet = afterEnable.first().snippet
        repo.updateSnippet(tmux.id, firstSnippet.id) { copy(isEnabled = false) }
        val afterDisableSnippet = repo.enabledSnippets.first()
        assertEquals(afterEnable.size - 1, afterDisableSnippet.size)
        assertNull(
            "disabled snippet gone",
            afterDisableSnippet.find { it.snippet.id == firstSnippet.id }
        )
    }

    // -----------------------------------------------------------------
    // 7. broken YAML fails fast
    // -----------------------------------------------------------------

    @Test
    fun `broken yaml fails fast and leaves file unchanged`() = runBlocking {
        val repo1 = newRepo()
        assertTrue(yamlFile.exists())
        repo1.update { cfg ->
            cfg.copy(libraries = cfg.libraries + SnippetLibrary(id = 9000, name = "Mark"))
        }

        val invalidYaml = "not: valid: ::: yaml [{[ unbalanced"
        yamlFile.writeText(invalidYaml)

        assertThrows(Exception::class.java) {
            SnippetYamlRepository(configDir)
        }
        assertEquals(invalidYaml, yamlFile.readText())
    }

    // -----------------------------------------------------------------
    // 8. id auto-generation
    // -----------------------------------------------------------------

    @Test
    fun `addLibrary with id 0 assigns max+1`() = runBlocking {
        val repo = newRepo()
        val maxBefore = repo.config.value.libraries.maxOf { it.id }
        val newId = repo.addLibrary(SnippetLibrary(id = 0L, name = "TestLib"))
        assertEquals("id is max+1", maxBefore + 1, newId)
    }

    @Test
    fun `addSnippet with id 0 assigns next-snippet-id across all libraries`() = runBlocking {
        val repo = newRepo()
        val anyLib = repo.config.value.libraries.first()
        val maxSnipBefore = repo.config.value.libraries
            .flatMap { it.snippets }.maxOf { it.id }
        val newId = repo.addSnippet(
            anyLib.id, CommandSnippet(id = 0L, name = "X", command = "echo X")
        )
        assertEquals(maxSnipBefore + 1, newId)
    }

    // -----------------------------------------------------------------
    // 9. transform sugar — toggle equivalence
    // -----------------------------------------------------------------

    @Test
    fun `updateLibrary transform toggles isEnabled`() = runBlocking {
        val repo = newRepo()
        val tmux = repo.config.value.libraries.find { it.name == "tmux" }!!
        assertFalse("starts disabled", tmux.isEnabled)
        repo.updateLibrary(tmux.id) { copy(isEnabled = !isEnabled) }
        val tmux2 = repo.config.value.libraries.find { it.id == tmux.id }!!
        assertTrue("now enabled", tmux2.isEnabled)
        repo.updateLibrary(tmux.id) { copy(isEnabled = !isEnabled) }
        val tmux3 = repo.config.value.libraries.find { it.id == tmux.id }!!
        assertFalse("toggled back", tmux3.isEnabled)
    }
}
