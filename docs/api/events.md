# Event API

TrialChamberPro fires Bukkit events at the key points in its lifecycle so other plugins can hook in without forking. All events live under `io.github.darkstarworks.trialChamberPro.api.events` and follow the standard Bukkit `Event` / `Cancellable` contracts — register a listener with `@EventHandler` and you're done.

<div data-gb-custom-block data-tag="hint" data-style="info">

**Threading note**: every TrialChamberPro event reports `isAsynchronous() == !Bukkit.isPrimaryThread()` at construction. On Paper, vault and key-drop events fire from the main/region thread (sync); reset and discovery events fire from coroutine-IO threads (async). On Folia, all of them are dispatched as async events because Folia has no single primary thread. **Listeners that touch the Bukkit API must schedule themselves onto the appropriate region thread, not assume sync delivery.**

</div>

## Events

### `ChamberResetEvent` (cancellable)

Fired immediately before a chamber begins resetting. Cancel to abort the reset entirely (useful for "don't reset while a player is mid-vault" or replacing the reset with a custom implementation).

| Field | Type | Notes |
|---|---|---|
| `chamber` | `Chamber` | The chamber about to reset. |
| `reason` | `Reason` | `SCHEDULED`, `MANUAL`, or `FORCED`. |
| `triggeringPlayer` | `Player?` | Null for `SCHEDULED` resets. |

```kotlin
@EventHandler
fun onChamberReset(event: ChamberResetEvent) {
    if (event.reason == ChamberResetEvent.Reason.SCHEDULED &&
        event.chamber.getPlayersInside().isNotEmpty()) {
        event.isCancelled = true  // Defer auto-resets while occupied
    }
}
```

### `ChamberResetCompleteEvent` (not cancellable)

Fired after a chamber has finished resetting. Useful for follow-up announcements, scoreboard updates, or webhook notifications.

| Field | Type | Notes |
|---|---|---|
| `chamber` | `Chamber` | The chamber that just reset. |
| `durationMs` | `Long` | Wall-clock duration of the reset. |
| `blocksRestored` | `Int` | Blocks the snapshot apply touched. `0` if no snapshot. |

### `VaultOpenedEvent` (not cancellable)

Fired immediately after a player has successfully opened a vault — the loot has been generated and delivered, the key has been consumed.

| Field | Type | Notes |
|---|---|---|
| `player` | `Player` | The player who opened the vault. |
| `vault` | `VaultData` | Database row for the opened vault. |
| `chamber` | `Chamber?` | Null in the pathological case of a deleted-while-open chamber. |
| `lootTableName` | `String` | Effective loot table (chamber override resolved against vault default). |
| `items` | `List<ItemStack>` | Snapshot clones — safe to inspect after the player's inventory mutates. |

```kotlin
@EventHandler
fun onVaultOpen(event: VaultOpenedEvent) {
    val totalValue = event.items.sumOf { economy.priceOf(it) }
    discordWebhook.send("${event.player.name} looted $totalValue from ${event.chamber?.name}")
}
```

### `SpawnerWaveCompleteEvent` (not cancellable)

Fired when a trial spawner finishes a wave (all spawned mobs killed). Fires for both registered chambers and wild spawners.

| Field | Type | Notes |
|---|---|---|
| `spawnerLocation` | `Location` | Block-aligned location of the spawner. |
| `chamber` | `Chamber?` | Null for wild spawners. |
| `ominous` | `Boolean` | True if the wave was ominous-mode at start. |
| `participants` | `Set<UUID>` | UUIDs credited as participants. |
| `durationMs` | `Long` | Wall-clock duration of the wave. |

### `ChamberClearedEvent` (not cancellable)

Fired once per reset cycle when **every** trial spawner inside a registered chamber has completed a wave — i.e. the chamber was "cleared" in one continuous run, before the next auto- or manual reset. Tracking resets on every `ChamberResetEvent`, so a chamber cleared, reset, and cleared again fires twice. Wild spawners (outside any registered chamber) do not contribute, and paused chambers do not fire.

| Field | Type | Notes |
|---|---|---|
| `chamber` | `Chamber` | The chamber that was cleared. |
| `participants` | `Set<UUID>` | Cumulative participants unioned across every wave in the run. |
| `durationMs` | `Long` | Wall-clock duration from first wave-start to last wave-complete. |

```kotlin
@EventHandler
fun onChamberCleared(event: ChamberClearedEvent) {
    // e.g. award a per-run bonus to everyone who took part
    event.participants.mapNotNull(Bukkit::getPlayer).forEach { it.giveExp(100) }
}
```

This is the signal the premium **Mythic Trials** module uses to bump each participant's per-chamber tier and pay out chamber-clear rewards. Added in **v1.5.0**.

### `ChamberEnteredEvent` / `ChamberExitedEvent` (not cancellable, may fire async)

*Added in v1.5.4.* Fired when a player crosses into / out of the bounding box of a registered, non-paused chamber. Each entry fires exactly one `ChamberEnteredEvent` and is always balanced by exactly one `ChamberExitedEvent` — including on disconnect (`PlayerQuitEvent` fires the exit for any player still inside), so listeners that allocate per-player state on entry can release it reliably. A player moving directly between two chambers fires an exit for the old chamber then an entry for the new one.

Both events fire **unconditionally** — unlike entry/exit messages and time tracking, they are *not* gated on the `statistics.*` config flags, so your integration can't be silently disabled by the server admin's stats preferences.

| Field | Type | Notes |
|---|---|---|
| `player` | `Player` | Who crossed the boundary (on exit-via-quit, the player is about to go offline). |
| `chamber` | `Chamber` | The chamber entered / exited. |

```kotlin
@EventHandler
fun onChamberEntered(event: ChamberEnteredEvent) {
    // Fires off the player's region thread — schedule Bukkit API calls back:
    plugin.scheduler.runAtEntity(event.player, Runnable {
        event.player.sendActionBar(Component.text("Entering ${event.chamber.name}…"))
    })
}
```

<div data-gb-custom-block data-tag="hint" data-style="warning">

These events fire from a coroutine off the player's region thread. Don't call Bukkit API directly in the handler — schedule onto the entity's thread first (see example). This is the signal TCP-MythicTrials uses to drive its in-chamber HUD.

</div>

### `ChamberDiscoveredEvent` (cancellable)

Fired by the auto-discovery system after a candidate chamber passes validation but **before** it is registered. Cancel to abort auto-registration (e.g. world-restricted whitelist, custom registration logic).

| Field | Type | Notes |
|---|---|---|
| `world` | `World` | World the candidate is in. |
| `suggestedName` | `String` | `auto_<world>_<x>_<z>`. |
| `minCorner` | `Location` | Inclusive AABB min corner. |
| `maxCorner` | `Location` | Inclusive AABB max corner. |
| `vaultCount` | `Int` | Vault blocks counted inside the AABB. |
| `spawnerCount` | `Int` | Trial spawner blocks counted inside the AABB. |
| `method` | `Method` | `CHUNK_LOAD` or `STARTUP_SWEEP`. |

```kotlin
@EventHandler
fun gateDiscovery(event: ChamberDiscoveredEvent) {
    if (event.world.name != "survival") {
        event.isCancelled = true   // only auto-register in the main world
    }
}
```

### `StatisticsUpdatedEvent` (not cancellable, **async**)

Fired after every statistic write reaches the database. Designed as the outbound signal for cross-server / network-sync modules that need to broadcast stat changes; single-server installs can mostly ignore this.

Fires asynchronously on the IO dispatcher (writes happen off-thread). If your listener calls Bukkit API, schedule it back onto the main / region thread yourself.

| Field | Type | Notes |
|---|---|---|
| `playerUuid` | `UUID` | The player whose stats changed. |
| `reason` | `Reason` | Why the write happened — see enum below. |

`Reason` enum: `VAULT_OPENED`, `MOB_KILLED`, `PLAYER_DEATH`, `CHAMBER_TIME_FLUSH`, `CHAMBER_COMPLETED`, `BATCH_FLUSH`.

```kotlin
@EventHandler
fun onStatsUpdated(event: StatisticsUpdatedEvent) {
    // e.g. broadcast a Redis message for cross-server invalidation
    redisPublisher.publish("tcp:stats:invalidate", event.playerUuid.toString())
}
```

Added in **v1.5.0** as part of the network-sync foundation.

### `TrialKeyDropEvent` (cancellable)

Fired immediately before the plugin drops a trial key for a wave participant. **Provider-driven waves only** — vanilla trial spawners drop their own keys via the spawner state machine and do not pass through this event.

Fires once per participant per wave completion (so a four-player wave produces four events). Cancel to suppress an individual key drop without affecting other participants.

| Field | Type | Notes |
|---|---|---|
| `location` | `Location` | The drop location (centered on the spawner, slightly above). |
| `keyType` | `Material` | `TRIAL_KEY` or `OMINOUS_TRIAL_KEY`. |
| `ownerUuid` | `UUID` | The participant the key is being dropped for. |

## Registering listeners

Standard Bukkit registration:

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        server.pluginManager.registerEvents(MyTcpListener(), this)
    }
}

class MyTcpListener : Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onVaultOpen(event: VaultOpenedEvent) { /* ... */ }
}
```

If your project pulls in TrialChamberPro as a soft dependency, gate listener registration behind a `pluginManager.getPlugin("TrialChamberPro") != null` check — the API classes won't be on the classpath if TCP isn't installed, so referencing them eagerly will `ClassNotFoundException`.

## Versioning

The event API is part of v1.3.0+ (`ChamberClearedEvent` and `StatisticsUpdatedEvent` added in v1.5.0). Event class names, field names, and `Reason`/`Method` enum constants are considered stable; new events and new enum constants may be added in minor releases. Removals or renames will be flagged in the changelog and given a deprecation cycle.
