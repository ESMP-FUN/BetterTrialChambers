# PlaceholderAPI

BetterTrialChambers ships a [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) expansion so you can show chamber stats, leaderboards, and live state on scoreboards, tab lists, holograms, chat formats, and anywhere else PlaceholderAPI is read.

{% hint style="info" %}
**Setup:** install PlaceholderAPI, then install BetterTrialChambers — the `tcp` expansion registers itself automatically (it's bundled, no `/papi ecloud download` needed). Verify with `/papi list` (look for `tcp`) or test one with `/papi parse me %btc_vaults_opened%`.
{% endhint %}

All placeholders use the `tcp_` prefix. Unless noted, they resolve for the player the placeholder is parsed against.

***

## Player statistics

| Placeholder                | Returns                                                                                      |
| -------------------------- | -------------------------------------------------------------------------------------------- |
| `%btc_vaults_opened%`      | Total vaults opened (normal + ominous)                                                       |
| `%btc_vaults_normal%`      | Normal vaults opened                                                                         |
| `%btc_vaults_ominous%`     | Ominous vaults opened                                                                        |
| `%btc_chambers_completed%` | Chambers completed                                                                           |
| `%btc_mobs_killed%`        | Mobs killed inside chambers                                                                  |
| `%btc_deaths%`             | Deaths inside chambers                                                                       |
| `%btc_kdr%`                | Kill/death ratio (mobs killed ÷ deaths), 2 decimals. With 0 deaths it equals the kill count. |
| `%btc_time_spent%`         | Time spent in chambers, formatted (e.g. `1h 30m 45s`)                                        |
| `%btc_time_spent_raw%`     | Time spent in chambers, raw seconds                                                          |

***

## Current state

| Placeholder                    | Returns                                                                                                                                             |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `%btc_current_chamber%`        | Name of the chamber the player is standing in, or `None`                                                                                            |
| `%btc_in_chamber%`             | `true` / `false` — whether the player is in a chamber                                                                                               |
| `%btc_current_chamber_reset%`  | Time until the current chamber resets (formatted). `None` if the player isn't in a chamber; `Never` if that chamber's automatic resets are disabled |
| `%btc_current_chamber_paused%` | `true` / `false` — whether the current chamber is paused (`false` when not in one)                                                                  |
| `%btc_chamber_count%`          | Number of registered chambers on the server                                                                                                         |

***

## Leaderboard rank (the player's own position)

1-based rank; **`0` means the player is unranked** (outside the tracked top 100).

| Placeholder                  | Ranks by                               |
| ---------------------------- | -------------------------------------- |
| `%btc_leaderboard_vaults%`   | Total vaults opened (normal + ominous) |
| `%btc_leaderboard_chambers%` | Chambers completed                     |
| `%btc_leaderboard_time%`     | Time spent in chambers                 |
| `%btc_leaderboard_mobs%`     | Mobs killed                            |

***

## Top players (for scoreboards / leaderboards)

Replace `<board>` with one of **`vaults`**, **`chambers`**, **`time`**, or **`mobs`**, `<pos>` with a position **`1`–`10`**, and the suffix with **`name`** or **`value`**:

```
%btc_top_<board>_<pos>_name%      # the player's name at that rank
%btc_top_<board>_<pos>_value%     # their value at that rank
```

Examples:

| Placeholder                 | Returns                                |
| --------------------------- | -------------------------------------- |
| `%btc_top_vaults_1_name%`   | #1 player by total vaults              |
| `%btc_top_vaults_1_value%`  | That player's vault total              |
| `%btc_top_chambers_3_name%` | #3 player by chambers completed        |
| `%btc_top_time_1_value%`    | #1 time, **formatted** (e.g. `12h 4m`) |
| `%btc_top_mobs_5_name%`     | #5 player by mobs killed               |

For the `time` board, `_value` is formatted like `%btc_time_spent%`; the others are plain numbers. **An unfilled slot** (e.g. asking for `_5_` when only 3 players are ranked) returns `---`.

***

## Caching & freshness

The expansion never blocks the server thread to hit the database:

* **Player stats** are cached per player for **30 seconds**.
* **Leaderboards** (rank + top boards) are cached for **60 seconds**, computed from the top 100 of each stat.
* Because lookups are non-blocking, the **first** read of a not-yet-cached value can return `0` / defaults while the data loads in the background; it fills in on the next refresh.

***

## Examples

Scoreboard / tab (via a plugin that reads PlaceholderAPI):

```
&eVaults: &f%btc_vaults_opened%   &eK/D: &f%btc_kdr%
&eChamber: &f%btc_current_chamber%  &7(reset %btc_current_chamber_reset%)
&6Top vault hunter: &f%btc_top_vaults_1_name% &7(%btc_top_vaults_1_value%)
```

Hologram leaderboard (lines 1–3):

```
#1 %btc_top_chambers_1_name% — %btc_top_chambers_1_value%
#2 %btc_top_chambers_2_name% — %btc_top_chambers_2_value%
#3 %btc_top_chambers_3_name% — %btc_top_chambers_3_value%
```

***

Next: [**Commands →**](../reference/commands.md)
