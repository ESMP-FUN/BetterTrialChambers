# PlaceholderAPI

Show chamber stats, leaderboards, and live state on scoreboards, tab lists, holograms, and chat formats using the bundled `btc` PlaceholderAPI expansion.

## Set it up

1. Install PlaceholderAPI, then install BetterTrialChambers. The `btc` expansion registers itself on startup. No `/papi ecloud download` needed.
2. Confirm it loaded: `/papi list` (look for `btc`).
3. Test one: `/papi parse me %btc_vaults_opened%`.

{% hint style="info" %}
The pre-2.0 `%tcp_*%` names still resolve, so old scoreboards and menus keep working.
{% endhint %}

Every placeholder resolves for the player it is parsed against, unless noted.

***

## Player statistics

| Placeholder                | Returns                                                                    |
| -------------------------- | ------------------------------------------------------------------------- |
| `%btc_vaults_opened%`      | Total vaults opened (normal + ominous)                                    |
| `%btc_vaults_normal%`      | Normal vaults opened                                                      |
| `%btc_vaults_ominous%`     | Ominous vaults opened                                                     |
| `%btc_chambers_completed%` | Chambers completed                                                        |
| `%btc_mobs_killed%`        | Mobs killed inside chambers                                               |
| `%btc_deaths%`             | Deaths inside chambers                                                    |
| `%btc_kdr%`                | Kills divided by deaths, 2 decimals. With 0 deaths it equals the kill count. |
| `%btc_time_spent%`         | Time spent in chambers, formatted (e.g. `1h 30m 45s`)                     |
| `%btc_time_spent_raw%`     | Time spent in chambers, whole seconds                                     |

***

## Current state

| Placeholder                    | Returns                                                                                                                       |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| `%btc_current_chamber%`        | Name of the chamber the player is standing in, or `None`                                                                     |
| `%btc_in_chamber%`             | `true` / `false`                                                                                                             |
| `%btc_current_chamber_reset%`  | Time until the current chamber resets. `None` if not in a chamber; `Never` if that chamber's automatic resets are turned off  |
| `%btc_current_chamber_paused%` | `true` / `false` (`false` when not in a chamber)                                                                             |
| `%btc_chamber_count%`          | Number of registered chambers on the server                                                                                  |

***

## The player's own leaderboard rank

1-based rank. `0` means the player is unranked (outside the top 100 that is tracked).

| Placeholder                  | Ranks by                               |
| ---------------------------- | -------------------------------------- |
| `%btc_leaderboard_vaults%`   | Total vaults opened (normal + ominous) |
| `%btc_leaderboard_chambers%` | Chambers completed                     |
| `%btc_leaderboard_time%`     | Time spent in chambers                 |
| `%btc_leaderboard_mobs%`     | Mobs killed                            |

***

## Top players (for scoreboards and holograms)

Set `<board>` to `vaults`, `chambers`, `time`, or `mobs`; `<pos>` to `1` through `10`; the suffix to `name` or `value`:

```
%btc_top_<board>_<pos>_name%      # the player's name at that rank
%btc_top_<board>_<pos>_value%     # their value at that rank
```

| Placeholder                 | Returns                                |
| --------------------------- | -------------------------------------- |
| `%btc_top_vaults_1_name%`   | #1 player by total vaults              |
| `%btc_top_vaults_1_value%`  | That player's vault total              |
| `%btc_top_chambers_3_name%` | #3 player by chambers completed        |
| `%btc_top_time_1_value%`    | #1 time, formatted (e.g. `12h 4m`)     |
| `%btc_top_mobs_5_name%`     | #5 player by mobs killed               |

For the `time` board, `_value` is formatted like `%btc_time_spent%`; the other boards return plain numbers. An unfilled slot (asking for `_5_` when only 3 players are ranked) returns `---`.

***

## Freshness

The expansion never blocks the server thread for a database read, so a value that is not cached yet reads as `0` or a default on the first request and fills in on the next refresh.

* Player stats: cached per player for 30 seconds.
* Leaderboards (rank and top boards): cached for 60 seconds, built from the top 100 of each stat.

***

## Examples

Scoreboard or tab line:

```
&eVaults: &f%btc_vaults_opened%   &eK/D: &f%btc_kdr%
&eChamber: &f%btc_current_chamber%  &7(reset %btc_current_chamber_reset%)
&6Top vault hunter: &f%btc_top_vaults_1_name% &7(%btc_top_vaults_1_value%)
```

Hologram leaderboard:

```
#1 %btc_top_chambers_1_name% - %btc_top_chambers_1_value%
#2 %btc_top_chambers_2_name% - %btc_top_chambers_2_value%
#3 %btc_top_chambers_3_name% - %btc_top_chambers_3_value%
```

***

Next: [**Commands**](../reference/commands.md)
