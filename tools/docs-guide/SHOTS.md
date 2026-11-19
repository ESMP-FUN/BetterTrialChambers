# Docs pictures: shot list

Every picture in `docs/.gitbook/assets/`, in the order they are taken. Each
one depends only on the shots above it, so a retake starts from the nearest
earlier shot. `install.sh` builds the server, `crops.py` cuts the full
pictures (`full-*.png`) into the ones the pages use.

## The setup

* **Server.** `test-servers/profiles/btc-docs-26.3`, made by `install.sh`:
  Paper 26.3-248, offline mode, port 25599, only the BetterTrialChambers jar
  from `build/libs`, a fresh world on a fixed seed, peaceful off (mobs are
  needed), daylight and weather cycles off, noon, clear weather.
* **Plugin settings.** All defaults, except `naming.name-pool` set to just
  `Bastion` so the first chamber's display name is known in advance. The tour
  in the pictures does every other change on camera.
* **Player.** The hidden test client as `BSTester` (offline account), operator
  for the admin shots, a normal player for the "What players see" shots.
* **Client.** Minecraft 26.3 on Xephyr `:5` at 1280x800, GUI scale 2 (scale 3
  pushes the tour's buttons off the screen), render distance 6, sound off,
  chat cleared before each chat shot (F3+D). Driven with `drive.py` here,
  which can type capitals and punctuation and right click without turning
  the camera (`use`).
* **Positions.** In `positions.txt` here, so a retake teleports to the same
  spot with the same facing. Found with `locate structure
  minecraft:trial_chambers`, the vault and spawner rows in `database.db`, and
  `execute if block ... run say` checks for open floor.
* **The tour reminder** only shows to an operator who never ran the tour.
  To retake shot 1, stop the server and delete
  `plugins/BetterTrialChambers/setup-state.yml` first.

## Shots

| # | File | Page | What it shows | Needs |
| --- | --- | --- | --- | --- |
| 1 | setup-reminder.png | installation | The chat line offering the tour, with the green [Start /trial setup] button | First join, operator, tour never run |
| 2 | tour-discovery.png | quick-start, README, store pages | Tour window, question 1 of 16, Auto-discover, Currently: Disabled | `/trial setup` |
| 3 | tour-auto-snapshot.png | quick-start | Question 2 of 16, Snapshot discovered chambers | Enable on question 1 |
| 4 | tour-fawe-missing.png | quick-start | Question 5 of 16, Enable greyed out "FastAsyncWorldEdit not present" | Enable on 2, Skip 3 and 4 |
| 5 | tour-reset-interval.png | quick-start | Question 6 of 16, six choice buttons, Currently: 2 days (default) | Skip 5 |
| 6 | tour-loot-mode.png | quick-start | Question 7 of 16, three choice buttons | Skip 6 |
| 7 | tour-complete.png | quick-start | The "Setup complete" window | Skip 7 to 16 |
| 8 | discovery-message.png | first-chamber, store pages | "Auto-discovered chamber auto_world_... with N vaults and N spawners." | Teleport above the chamber, wait for the chunks |
| 9 | list-current.png | first-chamber | "You are inside auto_world_... (volume ...)" | Standing in the chamber, `/trial list current` |
| 10 | chamber-info.png | first-chamber | The Chamber Info lines, Display Name: Bastion, Snapshot: Created | `/trial info <name>` |
| 11 | player-boss-bar.png | what-players-see | A spawner fight with mobs and the boss bar | Deop, survival, Resistance V, in front of a spawner |
| 12 | player-vault-opened.png | what-players-see | "You opened a Normal Vault!" in front of the vault | Two trial keys in hand, facing a vault, right click |
| 13 | player-vault-locked.png | what-players-see | "You have already opened this Normal Vault! It opens again when Bastion resets in ..." | Right click the same vault |
| 14 | player-cannot-place.png | what-players-see | Holding cobblestone, "You cannot place blocks in a Trial Chamber!" | Right click the floor |
| 15 | hero-chamber.png | docs README | The boss bar shot, uncropped | Same as 11 |
| 16 | menu-main.png | admin-menu | The "BetterTrialChambers Admin" menu | Op again, creative, `/trial menu` |
| 17 | menu-chambers.png | admin-menu | The Chambers list, the chamber's tooltip open | Click Chambers, hover the chamber |
| 18 | menu-chamber.png | admin-menu | The chamber's page | Click the chamber |
| 19 | rename-prompt.png | names | "Type a new display name for auto_world_... in chat (or 'cancel')." | Click Rename |
| 20 | rename-done.png | names | "Chamber display name set to Emerald Depths." | Type the name |
| 21 | menu-settings.png | resets | The chamber's settings menu | `/trial menu <name>`, Chamber Settings |
| 22 | menu-reset-interval.png | resets | Reset Interval tooltip, Current: 24 hours | Right click Reset Interval once, hover |
| 23 | menu-chamber-protection.png | protection | The chamber's own protection menu, Allow PvP set for this chamber | Protection, click Allow PvP |
| 24 | menu-protection.png | protection | The server-wide Protection Settings menu | Main menu, Protection Settings |
| 25 | loot-pools.png | loot | The three pools of the chamber's normal loot | Chamber page, Normal Loot |
| 26 | loot-editor.png | loot | The main-reward editor | Click main-reward |
| 27 | loot-item-tooltip.png | loot | The emerald entry's tooltip | Hover the emeralds, then Discard |
| 28 | reset-done.png | resets | Chat after a reset straight away | Chamber page, shift and right click Reset Chamber |
| 29 | stats-chat.png | stats | `/trial stats` lines | Kills credited in shot 11 (`damage ... by BSTester`) |
| 30 | leaderboard-chat.png | stats | `/trial leaderboard mobs` | Same |
| 31 | menu-leaderboard.png | stats | The Mobs Killed leaderboard menu with a head | Main menu, Statistics, Mobs Killed |
| 32 | hand-generate.png | your-first-chamber | "Chamber Arena created", "Scanning complete", "Snapshot created" | Console `trial delete <name>`, then `/trial generate coords ... Arena` over the same chamber |

The full shots are not kept in the repo; `crops.py` lists which full shot
each picture comes from. The reset warning on "What players see" stays a code block: catching it in a
picture means waiting minutes on a timer.

## After a retake

Run `python3 tools/docs-guide/crops.py <folder with full-*.png> docs/.gitbook/assets`,
then `oxipng -o 4 docs/.gitbook/assets/*.png`, and check the
crop boxes in it against the new full pictures: chat crops follow the number
of chat lines, menu crops follow the GUI scale and window size above.
