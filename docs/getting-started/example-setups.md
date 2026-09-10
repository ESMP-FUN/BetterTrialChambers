# Example Setups

Ready-to-paste `config.yml` recipes for common "I only want one thing" setups. BetterTrialChambers has many subsystems (resets, snapshots, protection, statistics, leaderboards, spawner waves, spectator mode, custom mob providers). Each can be turned off on its own without affecting the others.

{% hint style="info" %}
Each recipe only sets the keys that matter for that setup. Everything else stays at its default. Paste onto a fresh `config.yml`, then run `/trial reload`.
{% endhint %}

***

## Recipe: reusable vaults only

Use BTC as a vault-cooldown plugin and nothing else. Players explore vanilla-generated Trial Chambers; BTC only tracks a per-player cooldown on each vault.

### What you get

* A vault opened by player A is still openable by player B right away. Each player has their own cooldown.
* After `normal-cooldown-hours`, player A can reopen the same vault for fresh loot.
* Chambers are never reset, snapshotted, protected, or scanned for stats.
* No `/trial generate` needed. Chambers are registered automatically as players walk into them.

### config.yml

```yaml
discovery:
  enabled: true              # auto-register chambers as players find them

global:
  default-reset-interval: 0  # no automatic resets, so no snapshots needed

vaults:
  normal-cooldown-hours: 1   # hours until a player can reopen the SAME vault
  ominous-cooldown-hours: 1  # 0 = reopenable immediately

protection:
  enabled: false             # players can mine and build inside chambers

statistics:
  enabled: false             # no stat tracking

spawner-waves:
  enabled: false             # trial spawners behave exactly as in vanilla

spectator-mode:
  enabled: false             # no spectate-on-death prompt
```

{% hint style="warning" %}
`discovery.enabled: true` is required. Without it, BTC does not know about any chamber, and vaults behave like vanilla with no per-player cooldown.
{% endhint %}

### Cooldown values

`normal-cooldown-hours` sets how long before the same player can reopen the same vault:

| Value | Effect |
| --- | --- |
| `0` | Reopenable immediately. For skill-arena and minigame servers. |
| `1` | Reopenable after 1 hour. |
| `6` | Reopenable after 6 hours. Roughly once per play session. |
| `24` | Once per day per player. |
| `168` | Once per week per player. |

`ominous-cooldown-hours` is the same setting for ominous vaults (unlocked with Ominous Trial Keys). Set it longer than the normal cooldown since the loot is better, or match them.

***

## Other minimal setups

To drop a subsystem you do not want, set its top-level `enabled` to `false`:

| Setting | What `false` disables |
| --- | --- |
| `protection.enabled` | All chamber protection (block break/place, container access, mob griefing, PvP rules) |
| `statistics.enabled` | Player stat tracking; `/trial stats` and `/trial leaderboard` show no data |
| `spawner-waves.enabled` | Boss-bar wave tracking; trial spawners revert to vanilla behaviour |
| `spectator-mode.enabled` | The spectate-on-death prompt |
| `discovery.enabled` | Auto-registration of chambers (you would register manually with `/trial generate`) |

Combine these freely. Turning one off does not affect the others.
