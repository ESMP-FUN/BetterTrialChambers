# Events

Listen for these to hook into BetterTrialChambers without forking it. All classes live in `com.esmpfun.bettertrialchambers.api.events` and follow the standard Bukkit `Event` / `Cancellable` contracts. Register with `@EventHandler`.

{% hint style="info" %}
**Threading**: every event computes `isAsynchronous()` as `!Bukkit.isPrimaryThread()` at construction.

- On Paper, `ChamberMobSpawnedEvent`, `SpawnerWaveCompleteEvent` and `TrialKeyDropEvent` fire on the primary (region) thread; everything else fires from coroutine threads and is delivered async.
- On Folia, treat every event as async.

Listeners that call the Bukkit API must schedule onto the correct region thread themselves (`plugin.scheduler.runAtEntity(...)` / `runAtLocation(...)`). Do not assume sync delivery.
{% endhint %}

## `ChamberEnteredEvent`

Fires when a player crosses into the bounding box of a registered, non-paused chamber. Always fires, regardless of `statistics.*` config flags.

| Field     | Type      | Notes                              |
| --------- | --------- | ---------------------------------- |
| `player`  | `Player`  | The player who entered.            |
| `chamber` | `Chamber` | The chamber the player is now in.  |

Cancellable: no. Async: yes (fires off the player's region thread). Since v1.5.4.

```kotlin
@EventHandler
fun onEnter(event: ChamberEnteredEvent) {
    plugin.scheduler.runAtEntity(event.player, Runnable {
        event.player.sendActionBar(Component.text("Entering ${event.chamber.name}"))
    })
}
```

## `ChamberExitedEvent`

Fires when a player crosses out of a registered, non-paused chamber. Also fires on `PlayerQuitEvent` for any player still inside, so entry-allocated state can be released reliably. A player moving between two chambers fires an exit for the old then an entry for the new.

| Field     | Type      | Notes                                                    |
| --------- | --------- | ------------------------------------------------------- |
| `player`  | `Player`  | The player who exited (may be about to go offline).     |
| `chamber` | `Chamber` | The chamber they were inside.                           |

Cancellable: no. Async: yes (fires off the player's region thread). Since v1.5.4.

```kotlin
@EventHandler
fun onExit(event: ChamberExitedEvent) {
    hudState.remove(event.player.uniqueId)
}
```

## `ChamberDiscoveredEvent`

Fires after the auto-discovery system validates a candidate chamber (size, vault count, center-Y) but before it is registered in the database.

| Field           | Type       | Notes                                         |
| --------------- | ---------- | --------------------------------------------- |
| `world`         | `World`    | World the candidate is in.                    |
| `suggestedName` | `String`   | `auto_<world>_<x>_<z>`.                       |
| `minCorner`     | `Location` | Inclusive bounding-box min corner.            |
| `maxCorner`     | `Location` | Inclusive bounding-box max corner.            |
| `vaultCount`    | `Int`      | Vault blocks counted inside the box.          |
| `spawnerCount`  | `Int`      | Trial spawner blocks counted inside the box.  |
| `method`        | `Method`   | `CHUNK_LOAD` or `STARTUP_SWEEP`.              |

Cancellable: yes (cancel to abort auto-registration). Async: yes. Since v1.3.0.

```kotlin
@EventHandler
fun gateDiscovery(event: ChamberDiscoveredEvent) {
    if (event.world.name != "survival") event.isCancelled = true
}
```

## `ChamberResetEvent`

Fires immediately before a chamber begins resetting.

| Field              | Type         | Notes                                                                             |
| ------------------ | ------------ | -------------------------------------------------------------------------------- |
| `chamber`          | `Chamber`    | The chamber about to reset.                                                       |
| `reason`           | `Reason`     | `SCHEDULED`, `MANUAL`, or `FORCED`.                                               |
| `triggeringPlayer` | `Player?`    | Null for `SCHEDULED` resets.                                                      |
| `snapshotOverride` | `ByteArray?` | Mutable. Set non-null to restore these gzip-compressed snapshot bytes instead of the chamber's on-disk snapshot. Bad bytes fall back to the on-disk snapshot with a warning. Default null. Added in v1.4.0. |

Cancellable: yes (cancel to abort the reset). Async: yes on Paper and Folia (fires from a coroutine-IO thread). Since v1.3.0.

```kotlin
@EventHandler
fun onReset(event: ChamberResetEvent) {
    if (event.reason == ChamberResetEvent.Reason.SCHEDULED &&
        event.chamber.getPlayersInside().isNotEmpty()) {
        event.isCancelled = true
    }
}
```

## `ChamberResetCompleteEvent`

Fires after a chamber has finished resetting (blocks restored, vault cooldowns cleared, spawners reset).

| Field            | Type      | Notes                                                  |
| ---------------- | --------- | ------------------------------------------------------ |
| `chamber`        | `Chamber` | The chamber that finished resetting.                   |
| `durationMs`     | `Long`    | Wall-clock duration of the reset.                      |
| `blocksRestored` | `Int`     | Blocks the snapshot apply touched. `0` if no snapshot. |

Cancellable: no. Async: yes. Since v1.3.0.

```kotlin
@EventHandler
fun onResetDone(event: ChamberResetCompleteEvent) {
    logger.info("${event.chamber.name} reset in ${event.durationMs}ms")
}
```

## `ChamberClearedEvent`

Fires once per reset cycle when every trial spawner inside a registered chamber has completed a wave in one continuous run, before the next reset. Tracking resets on every reset cycle. Wild spawners do not contribute; paused chambers do not fire.

| Field          | Type        | Notes                                                            |
| -------------- | ----------- | -------------------------------------------------------------- |
| `chamber`      | `Chamber`   | The chamber that was cleared.                                   |
| `participants` | `Set<UUID>` | Cumulative participants unioned across every wave in the run.   |
| `durationMs`   | `Long`      | From first wave-start to last wave-complete.                    |

Cancellable: no. Async: yes on Folia; on Paper it fires on the wave's region thread. Since v1.5.0.

```kotlin
@EventHandler
fun onCleared(event: ChamberClearedEvent) {
    event.participants.mapNotNull(Bukkit::getPlayer).forEach { it.giveExp(100) }
}
```

## `ChamberMobSpawnedEvent`

Fires after a trial-spawner wave mob has been spawned and recorded, covering both vanilla spawns and custom-provider replacement spawns.

| Field             | Type       | Notes                                                                        |
| ----------------- | ---------- | -------------------------------------------------------------------------- |
| `entity`          | `Entity`   | The spawned mob (the replacement entity after provider substitution).        |
| `spawnerLocation` | `Location` | The trial spawner block that produced it.                                    |
| `chamber`         | `Chamber?` | The registered chamber, or null for a wild spawner.                          |
| `isOminous`       | `Boolean`  | Whether this is an ominous-wave spawn.                                       |
| `providerId`      | `String`   | `"vanilla"` if not replaced, else the provider id (`"mythicmobs"`, etc.).    |

Cancellable: no (the entity already exists). To prevent or substitute spawns, use Bukkit's `CreatureSpawnEvent` with `SpawnReason.TRIAL_SPAWNER`, or register a `TrialMobProvider`. Async: sync on Paper (primary thread), region thread on Folia. Since v1.3.3.

```kotlin
@EventHandler
fun onMobSpawn(event: ChamberMobSpawnedEvent) {
    val mob = event.entity as? LivingEntity ?: return
    if (event.isOminous) mob.customName(Component.text("Ominous ${mob.type}"))
}
```

## `SpawnerWaveCompleteEvent`

Fires when a trial spawner finishes a wave (all spawned mobs killed). Fires for both registered chambers and wild spawners.

| Field             | Type        | Notes                                       |
| ----------------- | ----------- | ------------------------------------------- |
| `spawnerLocation` | `Location`  | Block-aligned location of the spawner.      |
| `chamber`         | `Chamber?`  | Null for wild spawners.                     |
| `ominous`         | `Boolean`   | True if the wave was ominous-mode at start. |
| `participants`    | `Set<UUID>` | Players credited as participants (may be empty). |
| `durationMs`      | `Long`      | Wall-clock duration of the wave.            |

Cancellable: no. Async: sync on Paper, region thread on Folia. Since v1.3.0.

```kotlin
@EventHandler
fun onWaveDone(event: SpawnerWaveCompleteEvent) {
    if (event.chamber == null) return
    event.participants.forEach { rewards.grantWaveBonus(it, event.ominous) }
}
```

## `TrialKeyDropEvent`

Fires immediately before the plugin drops a trial key for a wave participant. Provider-driven waves only; vanilla spawners drop their own keys via the spawner state machine and skip this event. Fires once per participant per wave completion.

| Field       | Type       | Notes                                                        |
| ----------- | ---------- | ---------------------------------------------------------- |
| `location`  | `Location` | Drop location (centered on the spawner, slightly above).    |
| `keyType`   | `Material` | `TRIAL_KEY` or `OMINOUS_TRIAL_KEY`.                         |
| `ownerUuid` | `UUID`     | The participant the key is being dropped for.               |

Cancellable: yes (suppresses one participant's key without affecting others). Async: sync on Paper, region thread on Folia. Since v1.3.0.

```kotlin
@EventHandler
fun onKeyDrop(event: TrialKeyDropEvent) {
    if (Bukkit.getPlayer(event.ownerUuid)?.hasPermission("myserver.nokeys") == true) {
        event.isCancelled = true
    }
}
```

## `PreVaultOpenEvent`

Fires just before a vault open resolves its loot table, after the spam-click / cooldown / key-validation gates pass but before any reward is granted. Cancelling aborts the open the same way a missing loot table would: no key consumed, no loot, no stats, and no plugin message (a cancelling listener owns the feedback).

| Field               | Type                | Notes                                                                        |
| ------------------- | ------------------- | -------------------------------------------------------------------------- |
| `player`            | `Player`            | The opener.                                                                  |
| `vault`             | `VaultData`         | Database row for the vault being opened.                                     |
| `chamber`           | `Chamber?`          | Null if the vault's chamber row was deleted while the vault was open.        |
| `vaultType`         | `VaultType`         | `NORMAL` or `OMINOUS`.                                                       |
| `lootTableOverride` | `String?` (mutable) | Set non-null to use this loot table id instead of normal resolution (chamber override > vault default). A missing id fails the open with no key consumed. Default null. |

Cancellable: yes. Async: yes (runs on an IO-dispatcher coroutine, never the primary thread). Since v1.3.3.

```kotlin
@EventHandler
fun onPreOpen(event: PreVaultOpenEvent) {
    if (event.vaultType == VaultType.OMINOUS) event.lootTableOverride = "ominous-premium"
}
```

## `VaultOpenedEvent`

Fires immediately after a player successfully opens a vault: loot generated, key consumed, items delivered (to inventory or popped out, per `vaults.drop-loot-at-vault`).

| Field           | Type              | Notes                                                                   |
| --------------- | ----------------- | --------------------------------------------------------------------- |
| `player`        | `Player`          | The player who opened the vault.                                       |
| `vault`         | `VaultData`       | Database row for the opened vault.                                     |
| `chamber`       | `Chamber?`        | Null if the vault's chamber row was deleted while the vault was open.  |
| `lootTableName` | `String`          | Effective loot table (chamber override resolved against vault default).|
| `items`         | `List<ItemStack>` | `ItemStack` clones, safe to inspect after the player's inventory changes. |

Cancellable: no. Async: yes (fires from the vault-open coroutine). Since v1.3.0.

```kotlin
@EventHandler
fun onVaultOpen(event: VaultOpenedEvent) {
    val total = event.items.sumOf { economy.priceOf(it) }
    discordWebhook.send("${event.player.name} looted $total from ${event.chamber?.name}")
}
```

## `StatisticsUpdatedEvent`

Fires immediately after a player's statistics are persisted to the database. The canonical outbound hook for cross-server / network-sync modules. High-frequency reasons (notably `MOB_KILL`) are not debounced here.

| Field        | Type     | Notes                           |
| ------------ | -------- | ------------------------------- |
| `playerUuid` | `UUID`   | The player whose stats changed. |
| `reason`     | `Reason` | See enum below.                 |

`Reason` enum: `VAULT`, `MOB_KILL`, `CHAMBER_COMPLETE`, `DEATH`, `TIME`.

Cancellable: no. Async: yes (always runs on `Dispatchers.IO`, never the primary thread). Since v1.5.1.

```kotlin
@EventHandler
fun onStatsUpdated(event: StatisticsUpdatedEvent) {
    redisPublisher.publish("btc:stats:invalidate", event.playerUuid.toString())
}
```

## Registering listeners

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        if (server.pluginManager.getPlugin("BetterTrialChambers") == null) return
        server.pluginManager.registerEvents(MyTcpListener(), this)
    }
}

class MyTcpListener : Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onVaultOpen(event: VaultOpenedEvent) { /* ... */ }
}
```

Gate listener registration behind the `getPlugin("BetterTrialChambers") != null` check. The API classes are not on the classpath when BTC is absent, so referencing them eagerly throws `ClassNotFoundException`.

## Versioning

The event API is part of v1.3.0+. Class names, field names, and enum constants are stable; new events and new enum constants may be added in minor releases. Removals or renames go through a deprecation cycle and are flagged in the changelog.
