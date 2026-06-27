# 🧪 Example Setups

TrialChamberPro is a big plugin with ten-plus subsystems (resets, snapshots, protection, statistics, leaderboards, spawner waves, spectator mode, custom mob providers, …). You don't have to use all of them — most are designed to be turned off cleanly, and the ones you keep stay on independently.

This page collects ready-to-paste config recipes for common "I only want X" setups.

{% hint style="info" %}
All recipes only touch the keys that matter for that setup. Everything else stays at default — paste these on top of a fresh `config.yml` and you're done. Run `/tcp reload` after editing.
{% endhint %}

***

## Recipe: Reusable Vaults Only

> _"I just want players to be able to open the same vault more than once. I don't want chamber resets, snapshots, protection, stats, or any of that. Pretend TCP is a vault-cooldown plugin."_

This is the leanest possible install. Players explore Trial Chambers as vanilla generates them, and TCP only does one thing: tracks a per-player cooldown on each vault so the same player can reopen the same vault after the cooldown expires. Other players are unaffected — each player has their own cooldown.

### What you get

* Vault that player A opened is **still openable by player B** immediately (per-player cooldowns).
* After `normal-cooldown-hours`, player A can reopen the **same vault** for a fresh loot roll.
* Chambers never reset, never get snapshotted, never get protected, never get scanned for stats.
* No `/tcp generate` needed — chambers are auto-discovered as players explore.

### config.yml

```yaml
discovery:
  enabled: true              # auto-register Trial Chambers as players find them
                             # — you never have to /tcp generate anything

global:
  default-reset-interval: 0  # disables all automatic chamber resets
                             # → no block restoration, no snapshots needed, no TPS cost

vaults:
  normal-cooldown-hours: 1   # how long until a player can reopen the SAME vault
  ominous-cooldown-hours: 1  # set to whatever feels right — 1, 6, 24 …
                             # set to 0 for "immediately reusable"

protection:
  enabled: false             # off — players can mine / build inside chambers

statistics:
  enabled: false             # off — no DB writes for kills / vault opens / chamber time

spawner-waves:
  enabled: false             # off — vanilla trial spawners behave exactly as Mojang ships them

spectator-mode:
  enabled: false             # off — no spectate-on-death prompt
```

### How it works

The vault-cooldown system is the **only** part of TCP that runs in this configuration. When a player right-clicks a Trial Vault, the [`VaultInteractListener`](../reference/commands.md) checks:

1. Is this vault inside a registered chamber? If no — vanilla behavior (no per-player cooldown).
2. Has this **specific player** opened this **specific vault** in the last `normal-cooldown-hours`? If yes — show "already opened" message. If no — open it, consume the key, give the loot, record the timestamp.

That's it. No background scheduler runs, no protection listeners fire, no statistics queries hit the database.

{% hint style="warning" %}
**Auto-discovery is required.** Without `discovery.enabled: true`, vaults in chambers that TCP doesn't know about behave like vanilla — there's no per-player cooldown at all. Auto-discovery quietly registers chambers as players walk into them, so this stays zero-touch.
{% endhint %}

### Tuning the cooldown

| `normal-cooldown-hours` | Effect                                                                                           |
| ----------------------- | ------------------------------------------------------------------------------------------------ |
| `0`                     | Vault is reusable **immediately** by the same player. Useful for skill-arena / minigame servers. |
| `1`                     | Reusable after 1 hour. A casual mid-session reset.                                               |
| `6`                     | Reusable after 6 hours. Once-per-play-session feel for most players.                             |
| `24`                    | Once per day per player. Vanilla-but-renewable.                                                  |
| `168`                   | Once per week per player. Vanilla-but-eventually-renewable.                                      |

`ominous-cooldown-hours` controls the cooldown on ominous vaults (the ones unlocked by Ominous Trial Keys) — typically longer than normal vaults since the loot is better, but it's your call.

***

## Other minimal setups

If your "just one thing" is different from the recipe above, the same idea applies — turn off the systems you don't care about by flipping their top-level `enabled` flag:

| Setting                  | What it disables when set to `false`                                                            |
| ------------------------ | ----------------------------------------------------------------------------------------------- |
| `protection.enabled`     | All chamber protection (block break/place, container access, mob griefing, PvP rules)           |
| `statistics.enabled`     | DB writes for player stats; `/tcp stats` and `/tcp leaderboard` show no data                    |
| `spawner-waves.enabled`  | Boss-bar wave tracking; trial spawners revert to vanilla behavior                               |
| `spectator-mode.enabled` | The "press to spectate on death" prompt                                                         |
| `discovery.enabled`      | Auto-registration of naturally-generated chambers (you'd register manually via `/tcp generate`) |

Combine them however you like. TCP is built so each subsystem stands alone — flipping one to `false` won't break any of the others.
