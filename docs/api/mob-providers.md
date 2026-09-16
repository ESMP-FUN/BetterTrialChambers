# Custom Mob Providers (Developer Guide)

Register a new mob source for trial spawner waves. For using an existing provider (MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens) as a server admin, see [Custom Mobs](../configuration/custom-mobs.md).

## Pipeline

When a chamber has a non-vanilla provider configured:

1. The vanilla trial spawner spawns its mob.
2. On `CreatureSpawnEvent`, the plugin checks the chamber's provider.
3. If the provider is non-vanilla and `isAvailable()`, the vanilla mob is removed the same tick.
4. `provider.spawnMob(mobId, location, ominous)` is called at the same location.
5. The returned entity is tracked by the wave system: boss bars, kill counts, cooldowns, key drops all continue.

Your provider is step 4. Everything else is handled.

## The interface

```kotlin
package com.esmpfun.bettertrialchambers.providers

import org.bukkit.Location
import org.bukkit.entity.Entity

interface TrialMobProvider {
    /** Short stable id stored in config (e.g. "vanilla", "mythicmobs"). */
    val id: String

    /** Human-readable name shown in GUI and messages. */
    val displayName: String

    /** True when the backing plugin is present, enabled, and its API is reachable. */
    fun isAvailable(): Boolean

    /**
     * Spawns a custom mob at [location].
     *
     * @param mobId Provider-specific identifier
     * @param location Where to spawn; chunk/region ownership already validated by the caller
     * @param ominous True when the originating spawner was in ominous mode
     * @return the spawned entity, or null on failure. Null makes the wave system
     *         skip tracking this spawn (the vanilla mob is already gone).
     */
    fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity?

    /** Cheap validation for GUI/YAML inputs. May return true optimistically. */
    fun validateMobId(mobId: String): Boolean
}
```

## Threading constraints

- `spawnMob` and `validateMobId` run on the spawner's region thread during a live `CreatureSpawnEvent`. Non-blocking only: no I/O, no waiting on async results, no `runBlocking`.
- Need async work to decide the spawn? Schedule it and return `null` to skip the slot.
- Never throw from `spawnMob`. It runs inside an event handler; an uncaught exception affects every later listener.
- Return `null` to fail soft. The wave continues, the slot is skipped, and a warning is logged under `debug.verbose-logging`.
- Cache `isAvailable()`. It is called on the hot path.
- Register during `onEnable` (after BTC has loaded). A provider registered after a wave has started is not seen by that wave.
- One provider per `id`. Re-registering the same `id` overwrites the previous instance.

## Full example

**plugin.yml:**

```yaml
softdepend: [BetterTrialChambers]
```

**BossPluginProvider.kt:**

```kotlin
class BossPluginProvider(private val btc: BetterTrialChambers) : TrialMobProvider {
    override val id = "bossplugin"
    override val displayName = "BossPlugin"

    private var available: Boolean? = null

    override fun isAvailable(): Boolean {
        available?.let { return it }
        val present = Bukkit.getPluginManager().getPlugin("BossPlugin")?.isEnabled == true
        return present.also { available = it }
    }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        if (!isAvailable()) return null
        return try {
            val cls = Class.forName("com.example.bossplugin.api.BossManager")
            val instance = cls.getMethod("getInstance").invoke(null)
            val spawn = cls.getMethod("spawn", String::class.java, Location::class.java)
            spawn.invoke(instance, mobId, location) as? Entity
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            if (btc.config.getBoolean("debug.verbose-logging", false)) {
                btc.logger.warning("[bossplugin] spawn failed for '$mobId': ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean = mobId.isNotBlank()
}
```

**MyPlugin.kt:**

```kotlin
override fun onEnable() {
    val btc = server.pluginManager.getPlugin("BetterTrialChambers") as? BetterTrialChambers
    if (btc == null) {
        logger.info("BetterTrialChambers not present, skipping provider registration.")
        return
    }
    btc.trialMobProviderRegistry.register(BossPluginProvider(btc))
}
```

Once registered, admins select the provider via `/trial mobs <chamber> provider bossplugin` or the per-chamber Custom Mob Provider GUI screen.

## Registry API

`plugin.trialMobProviderRegistry` exposes:

| Method                | Notes                                                          |
| --------------------- | ------------------------------------------------------------ |
| `register(provider)`  | Call on the main thread during startup.                       |
| `get(id): TrialMobProvider?` | Null if not registered. Thread-safe.                   |
| `getOrVanilla(id)`    | Falls back to the always-available vanilla provider.          |
| `all()`               | Every registered provider.                                    |
| `available()`         | Providers whose backing plugin is present and enabled.        |

## The six built-in providers

MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, and Citizens ship reflection-based integrations rather than compile-time bindings, so they tolerate version skew and pull in no heavy dependency. Reuse that pattern (see the `Class.forName` block in the example above).

## Wild spawners

The free provider system only intercepts spawns inside a registered chamber. Wild spawners (placed by players, often from a `/trial give <preset>` item) are handled by a separate seam, `com.esmpfun.bettertrialchambers.api.WildSpawnerResolver` (v1.4.0+), which the premium Wild Spawners module implements. Register one as a Bukkit service:

```kotlin
Bukkit.getServicesManager().register(
    WildSpawnerResolver::class.java,
    MyResolver(),
    myPlugin,
    ServicePriority.Normal
)
```

`resolve(spawnerLocation, presetId)` returns a `WildSpawnerResolver.Config(providerId, normalIds, ominousIds)` to substitute custom mobs, or `null` to leave the vanilla spawn intact. Only the highest-priority registered resolver is consulted. The `providerId` must match a provider in `plugin.trialMobProviderRegistry` or the spawn falls back to vanilla.

## Extending BTC as a module

For a full add-on with its own lifecycle, implement `com.esmpfun.bettertrialchambers.api.TCPModule` and register it with `plugin.moduleRegistry.register(module)` in your `onEnable`. The registry calls `onLoad(tcp)` once BTC is fully initialized and `onUnload(tcp)` when your plugin (or BTC) is disabled; both run on the primary thread. Modules are auto-unregistered on `PluginDisableEvent`.

## Versioning

`TrialMobProvider` is part of the v1.3.0+ public API. Method signatures, the `id` / `displayName` contract, and the registry's `register` / `get` / `all` methods are stable. New methods may arrive with default implementations in minor releases; removals go through a deprecation cycle.
