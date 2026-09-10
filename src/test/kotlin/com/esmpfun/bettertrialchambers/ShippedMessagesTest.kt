package com.esmpfun.bettertrialchambers

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStreamReader

/**
 * Guards the wording the plugin ships in `messages.yml`.
 *
 * Two things are pinned here.
 *
 * First, that a server owner whose own `messages.yml` predates a new line of
 * text still sees that text. Their file is read with the shipped one behind it,
 * so anything they have not got falls back to the plugin's own English instead
 * of putting a literal "<missing: some.key>" on screen. Their wording still
 * wins wherever they have written one, blank ones included.
 *
 * Second, that every line of GUI text the code asks for is actually in the
 * shipped file. Asking for a key that was never written is invisible until
 * somebody opens that exact screen, which is a poor way to find out.
 */
class ShippedMessagesTest {

    private fun shipped(): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream("messages.yml")
        assertNotNull(stream, "messages.yml should be on the classpath as a plugin resource")
        return YamlConfiguration.loadConfiguration(InputStreamReader(stream!!, Charsets.UTF_8))
    }

    @Test
    fun `an owner's older file falls back to the shipped wording`() {
        val owner = YamlConfiguration()
        owner.set("gui.loot-editor.save-name", "&aMijn eigen tekst")
        owner.set("gui.loot-editor.mode-weighted", "")
        owner.setDefaults(shipped())

        assertEquals("&aMijn eigen tekst", owner.getString("gui.loot-editor.save-name"),
            "a line the owner wrote should win over the shipped one")
        assertEquals("", owner.getString("gui.loot-editor.mode-weighted"),
            "a line the owner deliberately blanked should stay blank")
        assertNotNull(owner.getString("gui.loot-editor.chance-name"),
            "a line the owner has never had should come from the shipped file")
        assertTrue(owner.getStringList("gui.loot-editor.chance-lore").isNotEmpty(),
            "a lore block the owner has never had should come from the shipped file too")
    }

    @Test
    fun `the pool chance controls have their wording`() {
        val messages = shipped()
        assertNotNull(messages.getString("gui.loot-editor.chance-name"))
        assertTrue(messages.getStringList("gui.loot-editor.chance-lore").isNotEmpty())
        assertTrue(messages.getStringList("gui.pool-selector.pool-lore-sometimes").isNotEmpty())
    }

    @Test
    fun `every line of GUI text the code asks for exists`() {
        val sourceRoot = File("src/main/kotlin")
        assertTrue(sourceRoot.isDirectory, "expected to run from the project root, so the source can be read")

        val messages = shipped()
        val asked = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> KEY_PATTERN.findAll(file.readText()).map { file.name to it.groupValues[1] } }
            // A key built up a piece at a time is not a key; it is a prefix.
            .filterNot { (_, key) -> key.endsWith(".") || key.endsWith("-") }
            .toList()

        assertTrue(asked.size > 100, "expected to find plenty of GUI text keys, found ${asked.size}")

        val missing = asked
            .filter { (_, key) -> !messages.contains(key) || messages.isConfigurationSection(key) }
            .distinctBy { it.second }
        assertTrue(
            missing.isEmpty(),
            "these lines of GUI text are asked for but not shipped:\n" +
                missing.joinToString("\n") { (file, key) -> "  $key  (in $file)" }
        )
    }

    private companion object {
        /** A whole `gui....` key written out as a literal in the source. */
        val KEY_PATTERN = Regex("\"(gui\\.[A-Za-z0-9_.-]+)\"")
    }
}
