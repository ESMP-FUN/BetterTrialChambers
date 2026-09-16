package com.esmpfun.bettertrialchambers.setup

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

/**
 * Every setting the setup tour offers must be one the plugin actually reads.
 *
 * Two of them were written under the wrong section and so did nothing at all,
 * while the tour reported them as turned on.
 */
class SetupCatalogPathsTest {

    private fun shippedConfig(): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream("config.yml")
        assertNotNull(stream, "config.yml should be on the classpath as a plugin resource")
        return YamlConfiguration.loadConfiguration(InputStreamReader(stream!!, Charsets.UTF_8))
    }

    @Test
    fun `every setup step points at a setting in config yml`() {
        val config = shippedConfig()
        val paths = (0 until SetupCatalog.count).mapNotNull { SetupCatalog.stepAt(it)?.configPath }
        assertTrue(paths.isNotEmpty(), "the tour should offer some settings")
        paths.forEach { path ->
            assertTrue(config.contains(path), "setup offers '$path', which config.yml does not have")
        }
    }
}
