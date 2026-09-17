# loot.yml

`loot.yml` decides what players get from vaults: vanilla loot, custom items, enchanted gear, money, commands, or any mix.

{% hint style="info" %}
**Location:** `plugins/BetterTrialChambers/loot.yml`

After editing the file, run `/trial reload`.
{% endhint %}

{% hint style="danger" %}
**Never put TAB characters in loot.yml.** YAML only allows spaces. A single TAB makes the whole file fail to load silently, and then every vault gives no loot even though keys are still used up.

If the console says `Loot table not found: default (available: )` with an empty list, a TAB is the usual cause. Open the file in an editor that shows whitespace (VS Code, Notepad++, Sublime Text), replace every TAB with spaces, and set the editor to "insert spaces for tabs".
{% endhint %}

***

## Keep the exact vanilla loot

Do nothing. Out of the box the two shipped tables, `default` (normal vaults) and `ominous-default` (ominous vaults), are Minecraft's own Trial Chamber loot, item for item and chance for chance. They are built from the game's own files, not typed by hand.

To get a fresh copy back later, delete `loot.yml` and restart the server.

If you want vanilla loot that is guaranteed to stay in sync with the game and that you will never edit, point an entry straight at the game's table with `type: VANILLA_TABLE` (see [Vanilla & Datapack Loot Tables](loot.yml.md#vanilla--datapack-loot-tables-passthrough)). Use the shipped tables instead when you want to *adjust* vanilla's numbers.

***

## How a table is built

A table is made of one or more **pools**. Every pool rolls on its own, and the vault's loot is all the pools added together. Each shipped table has three pools:

1. `main-reward` gives one item (mostly from a rare list, sometimes from an everyday list).
2. `extra-supplies` gives one to three everyday items.
3. `unique` gives one standout item, but the whole pool only runs 25% of the time for a normal vault and 75% for an ominous one.

For one opening, each pool:

1. Skips itself entirely if it has a `chance` and the roll fails (nothing drops from it, not even guaranteed items).
2. Drops every `guaranteed-items` entry.
3. Draws `min-rolls` to `max-rolls` times from `weighted-items`, picking one item per draw by weight (this is [weighted mode](loot.yml.md#drop-chance-modes-weighted-vs-independent); independent mode works differently).
4. Rolls each `command-rewards` and `economy-rewards` entry on its own chance.

***

## Pool and table keys

These sit directly under a pool (or under the table itself in the single-pool format).

| Key | What it does | Values |
| --- | --- | --- |
| `min-rolls` / `max-rolls` | How many items this pool draws from `weighted-items`. A random number between the two. | Whole numbers. `1`/`1` = exactly one item. Negatives become 0; min above max is swapped. |
| `chance` | How often the whole pool runs. Leave it out and it always runs. Pool format only. | `0` to `1`, or a percentage (`0.25` and `25` both mean a quarter of the time). Out-of-range values are treated as "always". |
| `mode` | How `weighted-items` are drawn. See [Drop Chance Modes](loot.yml.md#drop-chance-modes-weighted-vs-independent). | `weighted` (default) or `independent`. An unknown value logs a warning and falls back to `weighted`. |
| `max-items` | Independent mode only: keep at most this many of the items that won, chosen at random. | Whole number. `0` or left out = no limit. |
| `guaranteed-items` | Items that always drop (subject to `chance` and the redeemable cap). | A list of item entries. |
| `weighted-items` | The items drawn by the rolls. | A list of item entries. |
| `command-rewards` | Console commands run on a chance. Not items. See [COMMAND Rewards](loot.yml.md#command-rewards-economy--permissions). | A list of command entries. |
| `economy-rewards` | Money paid through Vault on a chance. See [Economy Rewards](loot.yml.md#economy-rewards). | A list of payout entries. |

{% hint style="warning" %}
More rolls means more items. A vault that hands out 20 items is fun once and then trivial. Start low; buffing loot later is easier than nerfing it.
{% endhint %}

{% hint style="info" %}
**Why not `min-rolls: 0` instead of `chance`?** `min-rolls: 0` with `max-rolls: 1` drops something about half the time, not a quarter. Use `chance` when you want a specific percentage.
{% endhint %}

***

## Item entry keys

Every entry in `weighted-items` or `guaranteed-items` needs a `type` and (for weighted entries) a `weight`. Everything else is optional.

| Key | What it does | Values |
| --- | --- | --- |
| `type` | The item. A Bukkit [Material](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html) name, or one of the special types `CUSTOM_ITEM`, `VANILLA_TABLE`, `OMINOUS_BOTTLE`. | e.g. `DIAMOND`, `DIAMOND_SWORD`. A bad name logs a warning and skips the entry. |
| `weight` | How likely this entry is compared to the others in the pool (weighted mode), or its own drop chance from 0 to 100 (independent mode). | A number. `0` or less never drops (logs a warning). |
| `amount-min` / `amount-max` | Stack size range, rolled per drop. | Whole numbers, at least 1. Default 1. Min above max is swapped. Ignored for `VANILLA_TABLE`. |
| `name` | Custom display name. Supports `&` colour codes, `&#RRGGBB` hex, and MiniMessage tags. | A string, e.g. `"&b&lTrial Diamond"`. |
| `lore` | Custom lore lines, same formatting as `name`. | A list of strings. |
| `enchantments` | Fixed enchantments. | A list of `"NAME:LEVEL"`, e.g. `"SHARPNESS:5"`. On an `ENCHANTED_BOOK` these are stored as book enchantments (usable at an anvil). |
| `enchantment-ranges` | Enchantments with a random level in a range. | A list of `"NAME:MIN:MAX"`, e.g. `"SHARPNESS:1:5"`. |
| `random-enchantment-pool` | Pick ONE enchantment at random from this list, at a random level. | A list of `"NAME:MIN:MAX"`. |
| `enchant-with-levels-min` / `-max` | Enchant the item the way an enchanting table would, at a random cost between these two levels. Both are required together. Takes precedence over the three keys above. | Whole numbers. This is what vanilla does for chamber bows, crossbows, axes, and chestplates. |
| `enchant-with-levels-treasure` | Allow the enchant-with-levels roll to produce treasure-only enchantments (Mending, Soul Speed, and so on). | `true` / `false`. Default `false`, matching vanilla chambers. |
| `potion-type` | Effect for `POTION`, `SPLASH_POTION`, `LINGERING_POTION`, `TIPPED_ARROW`. | A [PotionType](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/potion/PotionType.html) name, e.g. `POISON`, `STRENGTH`. |
| `potion-level` | Effect level. | `0` = level I, `1` = level II, and so on. |
| `potion-level-min` / `-max` | Roll the effect level in a range, per drop. Both required together. Also used for `OMINOUS_BOTTLE`. | Whole numbers. Min above max is swapped. |
| `custom-effect-type` | For effects with no standard `PotionType`, on a `POTION` / `SPLASH_POTION` etc. | A [PotionEffectType](https://jd.papermc.io/paper/1.21/org/bukkit/potion/PotionEffectType.html) name, e.g. `HERO_OF_THE_VILLAGE`, `GLOWING`, `LUCK`. |
| `effect-duration` | Override how long the effect lasts. Left out, it is worked out from the potion type and item form. | Ticks (20 ticks = 1 second). |
| `durability-min` / `-max` | Drop the item pre-damaged, with a random damage value in this range. | Whole numbers. Higher = more worn. |
| `instrument` | Goat horn variant, for `type: GOAT_HORN`. | `PONDER`, `SING`, `SEEK`, `FEEL`, `ADMIRE`, `CALL`, `YEARN`, `DREAM` (or the full `PONDER_GOAT_HORN` form). |
| `custom-model-data` | Give a vanilla item a resource-pack model id. | A number. |
| `serialized-item` | A whole item saved by the game itself (enchants, effects, name, lore, NBT, third-party tags). Written automatically when you add an item from the GUI. When present, every structured key above is ignored except the amount range. | Base64 text. Do not hand-edit. |
| `redeemable` | Cap how often one player can win this entry. See [Limiting How Often a Player Can Win an Item](loot.yml.md#limiting-how-often-a-player-can-win-an-item-redeemable). | `per-reset` (default), `per-chamber`, `once`. |
| `redeem-id` | Hidden identity used to remember who has won a capped entry. Written automatically. | Text. Do not edit or delete. |
| `enabled` | Set `false` to keep an entry in the file but stop it dropping. Toggled by the GUI. | `true` / `false`. Default `true`. |
| `plugin` / `item-id` | For `type: CUSTOM_ITEM`. See [Custom Plugin Items](loot.yml.md#custom-plugin-items). | Plugin name and its item id. |
| `table` | For `type: VANILLA_TABLE`. See [Vanilla & Datapack Loot Tables](loot.yml.md#vanilla--datapack-loot-tables-passthrough). | A loot table key like `"minecraft:chests/trial_chambers/reward"`. |

***

## Add a basic item to a table

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    guaranteed-items: []
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 10.0
```

1. Open `loot.yml`.
2. Under the table's `weighted-items`, add a `- type:` line with the Material name.
3. Add `weight` (how common it is) and, if you want more than one, `amount-min` / `amount-max`.
4. Save and run `/trial reload`.

### Add it from the GUI

1. Run `/trial menu`, then **Loot Tables**, then click the table (a multi-pool table asks which pool first).
2. Hold the item you want and click **+ Add from Hand**. The whole item is captured, including enchantments, potion data, custom name, lore, and NBT, into `serialized-item`.
3. To add many at once, click **Bulk add (drag items in)**, drag or shift-click items into the chest, and close it.
4. Changes are written to `loot.yml` and apply to every chamber using that table.

***

## Item recipes

<details>

<summary><strong>Custom name and lore</strong></summary>

```yaml
- type: DIAMOND
  amount-min: 1
  amount-max: 3
  weight: 10.0
  name: "&b&lTrial Diamond"
  lore:
    - "&7Earned from conquering"
    - "&7the Trial Chamber"
    - ""
    - "&6&lRare Drop"
```

`&0`-`&9` / `&a`-`&f` are colours; `&l` bold, `&o` italic, `&n` underline, `&m` strikethrough, `&k` magic. `&#RRGGBB` hex and MiniMessage tags also work.

</details>

<details>

<summary><strong>Enchanted gear</strong></summary>

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 5.0
  name: "&c&lTrial Blade"
  enchantments:
    - "SHARPNESS:5"
    - "UNBREAKING:3"
    - "FIRE_ASPECT:2"
```

Format is `NAME:LEVEL`. Names are the in-game ids (`SHARPNESS` or `minecraft:sharpness`). See the [Enchantment list](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/enchantments/Enchantment.html).

</details>

<details>

<summary><strong>Enchanted books</strong></summary>

```yaml
- type: ENCHANTED_BOOK
  amount-min: 1
  amount-max: 1
  weight: 8.0
  enchantments:
    - "MENDING:1"
```

Use `type: ENCHANTED_BOOK`, not `BOOK`. On a real enchanted book the plugin stores the enchantment where an anvil can use it. Enchantment lines on a plain `BOOK` only make it look enchanted.

Tip: hand out books for specific enchantments rather than enchanted gear, so players choose what to apply them to.

</details>

<details>

<summary><strong>Tipped arrows</strong></summary>

```yaml
- type: TIPPED_ARROW
  amount-min: 8
  amount-max: 16
  weight: 15.0
  potion-type: POISON
  potion-level: 1        # 0 = I, 1 = II
  name: "&aPoison Arrows"
```

More examples:

```yaml
# Poison II arrows
- type: TIPPED_ARROW
  amount-min: 12
  amount-max: 24
  weight: 15.0
  potion-type: POISON
  potion-level: 1

# Slowness IV arrows for PvP
- type: TIPPED_ARROW
  amount-min: 8
  amount-max: 16
  weight: 10.0
  potion-type: SLOWNESS
  potion-level: 3

# Longer-lasting poison arrows
- type: TIPPED_ARROW
  amount-min: 4
  amount-max: 8
  weight: 12.0
  potion-type: POISON
  potion-level: 1
  effect-duration: 1200    # 60 seconds
  name: "&2Long-Lasting Poison Arrow"
```

Common `potion-type` values: `SPEED`, `SLOWNESS`, `STRENGTH`, `HEALING`, `HARMING`, `JUMP_BOOST`, `REGENERATION`, `RESISTANCE`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `INVISIBILITY`, `NIGHT_VISION`, `WEAKNESS`, `POISON`, `WITHER`, `TURTLE_MASTER`, `SLOW_FALLING`.

</details>

<details>

<summary><strong>Potion effect duration</strong></summary>

Leave `effect-duration` out and the plugin works the length out from the potion type, scaled by the item form:

| Item form | Multiplier |
| --- | --- |
| `POTION` | 1.0x (the potion's own length) |
| `SPLASH_POTION` | 1.0x |
| `LINGERING_POTION` | 0.25x |
| `TIPPED_ARROW` | 0.125x |

Minimums are enforced so an effect never comes out at 0 seconds (5s for arrows, 10s for lingering, 30s for other potions). To set an exact length, add `effect-duration: <ticks>` (20 ticks = 1 second).

</details>

<details>

<summary><strong>Potions with a set level</strong></summary>

```yaml
- type: POTION
  amount-min: 1
  amount-max: 2
  weight: 12.0
  potion-type: STRENGTH
  potion-level: 1        # Strength II
  name: "&cStrength Potion II"
```

Works with `POTION`, `SPLASH_POTION`, and `LINGERING_POTION`.

```yaml
# Healing II splash potion
- type: SPLASH_POTION
  amount-min: 2
  amount-max: 4
  weight: 10.0
  potion-type: HEALING
  potion-level: 1

# Regeneration III lingering potion
- type: LINGERING_POTION
  amount-min: 1
  amount-max: 2
  weight: 8.0
  potion-type: REGENERATION
  potion-level: 2
```

</details>

<details>

<summary><strong>Ominous bottles (Bad Omen)</strong></summary>

`type: OMINOUS_BOTTLE` produces the real `minecraft:ominous_bottle` item, which stacks with bottles from vanilla vaults.

```yaml
# Fixed level
- type: OMINOUS_BOTTLE
  amount-min: 1
  amount-max: 1
  weight: 5.0
  potion-level: 2            # 0 = I ... 4 = V

# Random level per drop, the way vanilla ominous vaults roll it
- type: OMINOUS_BOTTLE
  amount-min: 1
  amount-max: 1
  weight: 2.0
  potion-level-min: 2       # Bad Omen III
  potion-level-max: 4       # up to V
```

Vanilla reference: normal vaults drop levels I-II (`potion-level-min: 0`, `potion-level-max: 1`); ominous vaults drop III-V (`2` to `4`).

Ominous bottles can only hold Bad Omen. A `potion-type` on one is ignored (logged), and a level outside 0-4 is clamped (logged). Custom `name` / `lore` still work but are not needed.

Older configs faked this with a `POTION` plus `custom-effect-type: BAD_OMEN` and a purple name. That still works but does not stack with vanilla bottles; replace it with `type: OMINOUS_BOTTLE`.

</details>

<details>

<summary><strong>Other custom effects</strong></summary>

Use `custom-effect-type` for effects with no standard `potion-type`:

```yaml
# Hero of the Village
- type: POTION
  custom-effect-type: HERO_OF_THE_VILLAGE
  potion-level: 1
  weight: 1.0
  name: "&aVillage Hero Potion"

# Luck
- type: POTION
  custom-effect-type: LUCK
  potion-level: 2
  weight: 3.0
  name: "&2Fortune's Favor"

# Glowing splash potion
- type: SPLASH_POTION
  custom-effect-type: GLOWING
  potion-level: 0
  weight: 5.0
  name: "&eGlowing Splash Potion"
```

Use `potion-type` for normal effects (Strength, Speed, Healing) and `custom-effect-type` for the rest. `effect-duration` works with both.

</details>

<details>

<summary><strong>Random enchantment level</strong></summary>

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 10.0
  enchantment-ranges:
    - "SHARPNESS:1:5"    # random Sharpness I to V
    - "LOOTING:1:3"      # random Looting I to III
  name: "&bRandom Enchanted Sword"
```

Format: `NAME:MIN:MAX`. Every listed enchantment is applied, each at its own random level.

```yaml
- type: DIAMOND_PICKAXE
  amount-min: 1
  amount-max: 1
  weight: 8.0
  enchantment-ranges:
    - "EFFICIENCY:3:5"
    - "FORTUNE:1:3"
```

</details>

<details>

<summary><strong>Pick one enchantment from a pool</strong></summary>

```yaml
- type: ENCHANTED_BOOK
  amount-min: 1
  amount-max: 1
  weight: 15.0
  random-enchantment-pool:    # exactly one of these is chosen
    - "SHARPNESS:3:5"
    - "PROTECTION:2:4"
    - "UNBREAKING:2:3"
    - "EFFICIENCY:3:5"
    - "MENDING:1:1"
```

Great for enchanted books: each player gets a different one.

```yaml
# Armour with one random protection type
- type: DIAMOND_CHESTPLATE
  amount-min: 1
  amount-max: 1
  weight: 5.0
  random-enchantment-pool:
    - "PROTECTION:3:4"
    - "BLAST_PROTECTION:3:4"
    - "PROJECTILE_PROTECTION:3:4"
    - "FIRE_PROTECTION:3:4"
```

</details>

<details>

<summary><strong>Enchant like an enchanting table</strong></summary>

```yaml
- type: BOW
  amount-min: 1
  amount-max: 1
  weight: 10.0
  enchant-with-levels-min: 5     # as if enchanted at level 5
  enchant-with-levels-max: 15    # through level 15
```

This is what Minecraft does for the bows, crossbows, axes, and chestplates in chamber vaults, which is why they come out with a full believable set of enchantments. A higher range means better and more enchantments. Both lines are required; one alone does nothing.

Treasure-only enchantments (Mending, Soul Speed) are excluded by default. Add `enchant-with-levels-treasure: true` to allow them.

Use this when you want an enchanting-table feel and do not mind the exact result. Use `random-enchantment-pool` when you need to control which enchantments can appear.

</details>

<details>

<summary><strong>Combine enchantment features</strong></summary>

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 5.0
  name: "&6&lLegendary Blade"
  lore:
    - "&7Forged in ancient trials"
  enchantments:
    - "UNBREAKING:3"          # always Unbreaking III
  enchantment-ranges:
    - "SHARPNESS:4:5"         # plus random Sharpness IV-V
  random-enchantment-pool:    # plus one random bonus
    - "LOOTING:2:3"
    - "SWEEPING_EDGE:2:3"
    - "FIRE_ASPECT:1:2"
```

`enchantments`, `enchantment-ranges`, and `random-enchantment-pool` stack. (`enchant-with-levels-min`/`-max`, if set, replaces all three.)

</details>

<details>

<summary><strong>Pre-damaged ("used") items</strong></summary>

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 10.0
  enchantments:
    - "SHARPNESS:4"
  durability-min: 200        # damage value, higher = more worn
  durability-max: 800
  name: "&bUsed Diamond Sword"
  lore:
    - "&7Found in a Trial Chamber"
```

`durability-min` / `-max` are damage amounts, not remaining durability. A diamond sword has 1561 max durability, so 200-800 damage leaves it around half to two-thirds intact. Values above the item's max are capped.

```yaml
- type: NETHERITE_PICKAXE
  amount-min: 1
  amount-max: 1
  weight: 3.0
  enchantment-ranges:
    - "EFFICIENCY:4:5"
    - "FORTUNE:2:3"
  durability-min: 500
  durability-max: 1500
  name: "&5Veteran's Pickaxe"
```

</details>

<details>

<summary><strong>Goat horns</strong></summary>

```yaml
- type: GOAT_HORN
  amount-min: 1
  amount-max: 1
  weight: 8.0
  instrument: PONDER
  name: "&6Horn of Contemplation"
```

`instrument` values: `PONDER`, `SING`, `SEEK`, `FEEL` (regular goats); `ADMIRE`, `CALL`, `YEARN`, `DREAM` (screaming goats, rarer). The full form `PONDER_GOAT_HORN` also works.

</details>

<details>

<summary><strong>Custom model data</strong></summary>

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 5.0
  custom-model-data: 1042
  name: "&6Special Sword"
```

Works on any vanilla item and combines with enchantments, lore, and durability ranges.

</details>

<details>

<summary><strong>Everything at once</strong></summary>

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 2.0
  name: "&5&lUltimate Trial Weapon"
  lore:
    - "&7Found in the deepest chamber"
  enchantments:
    - "UNBREAKING:3"
  enchantment-ranges:
    - "SHARPNESS:4:5"
  random-enchantment-pool:
    - "LOOTING:2:3"
    - "SWEEPING_EDGE:2:3"
    - "FIRE_ASPECT:1:2"
  durability-min: 100
  durability-max: 300
```

</details>

***

## Set an item's drop chance (weight)

In weighted mode, `weight` is relative. Double a weight and that item comes up twice as often. Weights do not need to add up to anything.

<details>

<summary><strong>Worked example</strong></summary>

```yaml
weighted-items:
  - type: DIAMOND          # weight 10
  - type: EMERALD          # weight 20
  - type: IRON_INGOT       # weight 15
  - type: COAL             # weight 55
```

Total weight 100, so per draw: Diamond 10%, Emerald 20%, Iron 15%, Coal 55%. If the total were 200 the same ratios would give half those percentages.

</details>

Rough starting weights: common items 20-50, uncommon 5-20, rare 1-5, jackpot below 1. Fewer rolls means you want higher weights; more rolls means lower.

***

## Drop Chance Modes: Weighted vs Independent

_(Added in 2.0.2.)_ A table or pool draws its `weighted-items` one of two ways. Set it with a `mode:` line. Leave it out for the classic **weighted** behaviour; existing tables are unchanged.

**Weighted** (default): one shared draw. Each item's `weight` is its share of the draw, all items compete, and the pool makes `min-rolls` to `max-rolls` draws per opening. Adding an item shrinks everything else's share.

**Independent**: each item rolls its own coin. `weight` is read as that item's own chance from 0 to 100. Items do not compete and the numbers do not need to total 100. `min-rolls` / `max-rolls` and the Luck bonus are ignored; use `max-items` to cap the haul.

{% hint style="success" %}
**Which to use:** a handful of items and you want a "one prize per opening" feel, use **weighted**. Lots of items (30, 50, 130) and you just want to say "this one 20%, that one 5%" with no mental math, use **independent**.
{% endhint %}

### Turn on independent mode

1. Add `mode: independent` to the table or pool.
2. Change each item's `weight` to the percentage you want (0 to 100).
3. Optionally add `max-items` to limit how many can drop at once.
4. Save and `/trial reload`.

```yaml
loot-tables:
  my-big-table:
    mode: independent
    max-items: 3           # optional, 0 or omit = no limit
    min-rolls: 1           # ignored here, kept so you can switch back
    max-rolls: 1
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 2
        weight: 40.0       # 40% chance on its own
      - type: NETHERITE_SCRAP
        amount-min: 1
        amount-max: 1
        weight: 10.0       # 10% chance
      - type: EMERALD
        amount-min: 3
        amount-max: 6
        weight: 75.0       # 75% chance
```

Every opening the diamond rolls its own 40%, the scrap its own 10%, the emerald its own 75%. If more than 3 win, 3 are kept at random.

{% hint style="warning" %}
Switching an existing table to independent reinterprets every `weight` as a percentage: an old weight of `10` becomes "10% chance", anything over 100 becomes "always". Check your numbers after switching. Guaranteed items still always drop in both modes.
{% endhint %}

### Do it from the GUI

1. Run `/trial menu`, then **Loot Tables**, then pick a table.
2. Click the **Roll Mode** button to flip between Weighted and Independent. Item tooltips update to match.
3. In weighted mode each item's tooltip shows its weight and the rough % per draw.
4. In independent mode each item shows its own % (shift-click to adjust), and the "Draws per Opening" button becomes a **Max Items** cap.

Big tables are paginated in the editor (2.0.1+), so more than 36 items get Previous/Next arrows.

***

## Limiting How Often a Player Can Win an Item (Redeemable)

_(Added in 2.1.0.)_ By default every item can be won again after each chamber reset, so a player can farm a rare drop by resetting the same chamber. The `redeemable` setting caps how often one player can win a specific entry. This is what you want for vanilla armour trims (Silence, Flow, Bolt, Wayfinder).

| Value | Meaning |
| --- | --- |
| `per-reset` _(default)_ | No limit. Winnable again after every reset. Leave the line out for normal items. |
| `per-chamber` | Each player wins it only once from this chamber, and that stays true after resets. They can still win it from a different chamber. |
| `once` | Each player wins it only once, ever, from any chamber on the server. |

Once a player has won a capped entry it is quietly dropped from that player's roll and the other items fill the gap. Nobody else is affected.

### Set it from the GUI

1. Run `/trial menu`, then **Loot Tables**, pick a table, click the item, and open its **amount page**.
2. Click **"How often can a player get this?"** to cycle through Every reset, Once per chamber, and Once ever.
3. Save. The plugin writes `redeemable` (and a hidden `redeem-id`) to `loot.yml`.

### Set it by hand

```yaml
weighted-items:
  # A trim each player can only ever win once, server-wide
  - type: SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE
    amount-min: 1
    amount-max: 1
    weight: 2.0
    redeemable: once

  # A book each player gets once per chamber
  - type: ENCHANTED_BOOK
    amount-min: 1
    amount-max: 1
    weight: 5.0
    redeemable: per-chamber
    enchantments:
      - "MENDING:1"
```

Accepted values: `per-reset` (the default, omit the line), `per-chamber`, `once`.

{% hint style="info" %}
**The hidden `redeem-id` line.** When the menu sets a cap it also writes a random `redeem-id`. That is how the plugin remembers which entry each player has won, so do not edit or delete it. If you write the entry entirely by hand without a `redeem-id`, the plugin fills in a stable one based on the item type.
{% endhint %}

{% hint style="warning" %}
Wins are stored in the database permanently and there is no in-game command to wipe them, so test with a spare non-op account. The cap is checked per player, and op accounts are capped too once they have claimed the item.
{% endhint %}

### Farm-proof vanilla trims

```yaml
weighted-items:
  - type: VANILLA_TABLE
    table: "minecraft:chests/trial_chambers/reward_ominous"
    weight: 100.0
    redeemable: per-chamber
```

{% hint style="info" %}
A `VANILLA_TABLE` entry rolls a whole table of many items, so `per-chamber` / `once` here caps the **whole entry**, not each trim. For per-trim caps, list the trim templates as their own entries and cap each one.
{% endhint %}

***

## Guaranteed items

Entries in `guaranteed-items` always drop, ignoring rolls and weight (but still subject to the pool's `chance` and the redeemable cap).

```yaml
loot-tables:
  daily-bonus:
    min-rolls: 2
    max-rolls: 4
    guaranteed-items:
      - type: GOLDEN_APPLE
        amount-min: 1
        amount-max: 1
        name: "&6Daily Bonus Apple"
    weighted-items:
      - type: DIAMOND
        weight: 10.0
```

Every player gets 1 Golden Apple plus 2-4 weighted items. Good for participation rewards, event tokens, or progression fragments.

***

## Multi-pool format

A table can use `pools:` to hold several pools that each roll on their own, like vanilla's common / rare / unique split. Whether a table is single-pool or multi-pool is decided by one thing: if it has a `pools:` key, it is multi-pool. The single-pool format (keys directly under the table name) keeps working and needs no changes.

<details>

<summary><strong>Example</strong></summary>

```yaml
loot-tables:
  vanilla-style:
    pools:
      - name: common
        min-rolls: 2
        max-rolls: 3
        weighted-items:
          - type: IRON_INGOT
            amount-min: 3
            amount-max: 7
            weight: 30.0
          - type: GOLD_INGOT
            amount-min: 2
            amount-max: 5
            weight: 25.0
          - type: ARROW
            amount-min: 16
            amount-max: 32
            weight: 20.0

      - name: rare
        min-rolls: 1
        max-rolls: 2
        weighted-items:
          - type: DIAMOND
            amount-min: 1
            amount-max: 3
            weight: 15.0
          - type: EMERALD
            amount-min: 3
            amount-max: 8
            weight: 20.0
          - type: GOLDEN_APPLE
            amount-min: 1
            amount-max: 1
            weight: 12.0

      - name: unique
        min-rolls: 0
        max-rolls: 1
        weighted-items:
          - type: ENCHANTED_GOLDEN_APPLE
            amount-min: 1
            amount-max: 1
            weight: 3.0
          - type: NETHERITE_INGOT
            amount-min: 1
            amount-max: 1
            weight: 2.0
```

The common pool always gives 2-3 items, the rare pool 1-2, the unique pool 0-1. Total 3-6 items with a controlled spread of rarity.

</details>

The maximum number of pools per table is set by `loot.max-pools-per-table` in `config.yml` (default 5). Pools past the limit are ignored at load.

***

## Vanilla & Datapack Loot Tables (passthrough)

_(Added in 1.5.7.)_ An entry with `type: VANILLA_TABLE` and a `table:` key defers to any loot table registered on the server, vanilla or from a datapack.

```yaml
weighted-items:
  - type: VANILLA_TABLE
    table: "minecraft:chests/trial_chambers/reward"
    weight: 30.0
  - type: DIAMOND
    amount-min: 1
    amount-max: 3
    weight: 70.0
```

When this entry is drawn (30% of draws here), the referenced table is run through the server's own loot engine and every item it produces is added. `amount-min` / `amount-max` do not apply; the table controls its own counts. The entry still counts as one draw of the pool.

| Table key | Contents |
| --- | --- |
| `minecraft:chests/trial_chambers/reward` | Normal vault loot |
| `minecraft:chests/trial_chambers/reward_ominous` | Ominous vault loot |
| `minecraft:chests/trial_chambers/supply` | Supply chest loot |

Datapack tables use their own namespace, e.g. `mypack:chambers/boss`. An unknown key logs a warning and yields nothing; the rest of the pool still drops.

***

## Economy Rewards

Pay money as loot two ways.

### Native `economy-rewards` (recommended, added in 1.5.12)

Pays through the Vault API, so it works with any economy provider (EssentialsX, CMI) with no hardcoded command. Add an `economy-rewards` list at the pool level (or table level in the single-pool format):

```yaml
loot-tables:
  rich-vault:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 10.0
    economy-rewards:
      - weight: 100.0          # % chance this payout fires, rolled on its own
        min: 250.0             # random amount between min and max
        max: 1500.0
        display-name: "Coins"  # optional label
      - weight: 5.0            # 5% jackpot
        amount: 10000.0        # fixed amount instead of a range
```

- `weight` is a 0 to 100 percentage, rolled on its own per entry.
- Use `amount` (fixed) or `min` / `max` (random range).
- The deposit runs on the main thread; the player gets a "You received" message.
- With no Vault economy provider installed the reward is skipped and other loot is unaffected.
- Economy rewards survive editing the table in the GUI.

### Or `command-rewards`

Run an economy command instead. Command rewards go in a separate `command-rewards` list, not in `weighted-items`. See [COMMAND Rewards](loot.yml.md#command-rewards-economy--permissions).

```yaml
command-rewards:
  - weight: 25.0
    commands:
      - "eco give {player} 1000"
    display-name: "&6+1000 Coins"
```

{% hint style="danger" %}
`type: COMMAND` inside `weighted-items` is not valid and logs an error. Use the `command-rewards` list.
{% endhint %}

***

## Custom Plugin Items

### Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible

Use `type: CUSTOM_ITEM` with `plugin:` and `item-id:`.

```yaml
weighted-items:
  - type: CUSTOM_ITEM
    plugin: Nexo
    item-id: "my_namespace:legendary_sword"
    weight: 2.0

  - type: CUSTOM_ITEM
    plugin: ItemsAdder
    item-id: "trial_chamber:legendary_sword"
    weight: 2.0

  - type: CUSTOM_ITEM
    plugin: Oraxen
    item-id: "mythic_helmet"
    weight: 3.0

  - type: CUSTOM_ITEM
    plugin: CraftEngine
    item-id: "my_pack:enchanted_blade"
    weight: 2.0

  - type: CUSTOM_ITEM
    plugin: MythicCrucible       # alias: Crucible
    item-id: "LegendarySword"
    weight: 1.0
```

| Plugin | `item-id` form |
| --- | --- |
| Nexo | namespaced id, e.g. `"namespace:item_name"` |
| ItemsAdder | namespaced id, e.g. `"namespace:item_name"` |
| Oraxen | plain id, e.g. `"item_name"` |
| CraftEngine | namespaced id from your pack, e.g. `"my_pack:item_name"`. A bare id defaults to the `minecraft` namespace, so always prefix custom items. |
| MythicCrucible | internal Mythic item name, e.g. `"LegendarySword"`. Requires MythicMobs installed. `plugin: Crucible` also works. |

The custom item plugin must be installed and enabled. You can add `name:`, `lore:`, and `enchantments:` on top of the resolved item.

{% hint style="warning" %}
If the plugin cannot find the id, the entry is skipped and nothing drops. Test your tables after adding custom items. If a custom item plugin ships a breaking API change the integration may pause until BTC is updated; the worst case is a skipped item and a console warning, never a crash.
{% endhint %}

***

## Give a chamber its own loot

1. Add a named table to `loot-tables` alongside `default` and `ominous-default`.
2. Assign it with `/trial loot set <chamber> <normal|ominous> <table>`.
3. Clear it with `/trial loot clear <chamber> [normal|ominous|all]`; the chamber then falls back to the default tables.

<details>

<summary><strong>Example</strong></summary>

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: DIAMOND
        weight: 10.0

  nether-chamber:
    min-rolls: 4
    max-rolls: 6
    weighted-items:
      - type: BLAZE_ROD
        amount-min: 3
        amount-max: 6
        weight: 25.0
      - type: FIRE_CHARGE
        amount-min: 5
        amount-max: 10
        weight: 30.0
      - type: NETHERITE_SCRAP
        amount-min: 1
        amount-max: 2
        weight: 10.0
      - type: ENCHANTED_BOOK
        weight: 15.0
        enchantments:
          - "FIRE_PROTECTION:4"

  ocean-chamber:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: PRISMARINE_CRYSTALS
        amount-min: 5
        amount-max: 15
        weight: 30.0
      - type: HEART_OF_THE_SEA
        amount-min: 1
        amount-max: 1
        weight: 5.0
      - type: TRIDENT
        weight: 3.0
        enchantments:
          - "RIPTIDE:3"
```

```
/trial loot set NetherChamber normal nether-chamber
/trial loot set OceanChamber normal ocean-chamber
```

</details>

### From the GUI

1. Run `/trial menu` and pick the chamber.
2. Open **Loot Table Overrides**.
3. Left/right-click **Normal** or **Ominous** to cycle through the tables. Shift-right-click clears the override.

> **Two different buttons.** **Loot Table Overrides** chooses *which* table the chamber uses. The **Normal Loot** / **Ominous Loot** buttons edit the *contents* of whichever table it currently uses. With an override set, those buttons edit the table you pointed at, so edits reach other chambers using it too. Leave the override on `(default)` and the chamber gets its own private `chamber-<name>` table that nothing else touches.

***

## Example tables

<details>

<summary><strong>Beginner-friendly server</strong></summary>

```yaml
loot-tables:
  default:
    min-rolls: 4
    max-rolls: 6
    guaranteed-items:
      - type: GOLDEN_APPLE
        amount-min: 1
        amount-max: 1
    weighted-items:
      - type: IRON_INGOT
        amount-min: 5
        amount-max: 10
        weight: 30.0
      - type: GOLD_INGOT
        amount-min: 3
        amount-max: 8
        weight: 25.0
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 15.0
      - type: EMERALD
        amount-min: 2
        amount-max: 5
        weight: 20.0
      - type: ENCHANTED_BOOK
        amount-min: 1
        amount-max: 1
        weight: 10.0
        enchantments:
          - "EFFICIENCY:4"
```

</details>

<details>

<summary><strong>Hardcore / competitive server</strong></summary>

```yaml
loot-tables:
  default:
    min-rolls: 1
    max-rolls: 2
    guaranteed-items: []
    weighted-items:
      - type: IRON_INGOT
        amount-min: 1
        amount-max: 3
        weight: 50.0
      - type: GOLD_INGOT
        amount-min: 1
        amount-max: 2
        weight: 30.0
      - type: DIAMOND
        amount-min: 1
        amount-max: 1
        weight: 5.0
      - type: EMERALD
        amount-min: 1
        amount-max: 2
        weight: 10.0
      - type: ENCHANTED_BOOK
        amount-min: 1
        amount-max: 1
        weight: 5.0
        enchantments:
          - "SHARPNESS:3"
```

</details>

<details>

<summary><strong>Economy-focused server</strong></summary>

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 2
        weight: 10.0
      - type: ENCHANTED_BOOK
        amount-min: 1
        amount-max: 1
        weight: 5.0
        enchantments:
          - "MENDING:1"
    economy-rewards:
      - weight: 40.0
        amount: 500.0
        display-name: "&a$500"
      - weight: 20.0
        amount: 2000.0
        display-name: "&a$2,000"
      - weight: 5.0
        amount: 10000.0
        display-name: "&6$10,000"
```

</details>

<details>

<summary><strong>Custom items plus vanilla</strong></summary>

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: CUSTOM_ITEM
        plugin: ItemsAdder
        item-id: "trial_chamber:trial_token"
        weight: 25.0
      - type: CUSTOM_ITEM
        plugin: ItemsAdder
        item-id: "trial_chamber:rare_gem"
        weight: 10.0
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 15.0
      - type: EMERALD_BLOCK
        amount-min: 1
        amount-max: 1
        weight: 8.0
    command-rewards:
      - weight: 5.0
        commands:
          - "lp user {player} permission set chamber.bonus true 3d"
        display-name: "&d3-Day Bonus Perk"
```

</details>

***

## Test a table

1. Edit `loot.yml`.
2. Run `/trial reload`.
3. Run `/trial reset <chamber>` to force an instant reset.
4. Open vaults and check the loot.
5. For real numbers, open 30-50 vaults and count what drops. A diamond meant for 10% per roll that shows up in 8 of 150 rolls (5%) means the weights need work.

***

## Common questions

**Vaults give no loot / "Loot table not found" with an empty list.** A TAB character in `loot.yml`. See the warning at the top of this page.

**Vaults give plain vanilla loot (crossbows, wind charges) and ignore my table.** Check `vaults.loot-mode` in `config.yml`. If it is `VANILLA`, BTC leaves vaults alone. Set it to `PER_PLAYER` and `/trial reload`. Confirm with `/trial info` (the **Vault Loot** line). See [config.yml loot-mode](config.yml.md#vault-settings).

**Can different players get different loot from the same vault?** Yes, with `vaults.loot-mode: PER_PLAYER` (the default). `SHARED` gives only the first player to reach the vault a reward.

**Can I use raw NBT for custom items?** Not directly. Use a custom item plugin, or add the item from the GUI (which stores it faithfully via `serialized-item`).

**What happens if I mistype a Material name?** The plugin logs a warning and skips that entry. Check the console after reloading.

**Can a loot table call another loot table?** Only vanilla and datapack tables, via `type: VANILLA_TABLE`.

**How do I remove an item from vanilla loot?** BTC replaces vault loot with your table, so just do not include the item.

**Why don't armour trims drop?** They are not in your table unless you add them. Add the trim smithing templates (e.g. `SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE`) to `weighted-items`, or add a `VANILLA_TABLE` entry for `minecraft:chests/trial_chambers/reward_ominous`. To stop them being farmed, set them to `once` or `per-chamber` (see [Limiting How Often a Player Can Win an Item](loot.yml.md#limiting-how-often-a-player-can-win-an-item-redeemable)).

***

## COMMAND Rewards (Economy & Permissions)

Command rewards run console commands when a player opens a vault, on a per-entry chance, alongside the item drops. Use them for money, permissions, experience, titles, or anything else.

{% hint style="info" %}
Command rewards go in a separate `command-rewards` list at the pool level (or table level in the single-pool format), **not** in `weighted-items`.
{% endhint %}

### Add a command reward

1. Add a `command-rewards:` list to the pool or table.
2. Give each entry a `weight` (0 to 100 percent chance, rolled on its own), a `commands` list, and a `display-name` for the player's message.
3. Save and `/trial reload`.

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 10.0
    command-rewards:
      - weight: 25.0
        commands:
          - "eco give {player} 1000"
        display-name: "&6+1000 Coins"
      - weight: 10.0
        commands:
          - "lp user {player} permission set special.vault.bonus true"
        display-name: "&5Special Permission Unlocked!"
```

### Placeholders

- `{player}` - the player's name
- `{uuid}` - the player's UUID

<details>

<summary><strong>Put commands in their own pool</strong></summary>

```yaml
loot-tables:
  premium-vault:
    pools:
      - name: items
        min-rolls: 3
        max-rolls: 5
        weighted-items:
          - type: DIAMOND
            amount-min: 5
            amount-max: 10
            weight: 10.0
          - type: NETHERITE_INGOT
            amount-min: 1
            amount-max: 3
            weight: 5.0

      - name: bonuses
        min-rolls: 1
        max-rolls: 1
        command-rewards:
          - weight: 50.0
            commands:
              - "eco give {player} 500"
            display-name: "&6+500 Coins"
          - weight: 30.0
            commands:
              - "eco give {player} 1000"
            display-name: "&6+1000 Coins"
          - weight: 15.0
            commands:
              - "eco give {player} 5000"
              - "give {player} nether_star 1"
            display-name: "&e&lJACKPOT! &6+5000 Coins"
          - weight: 5.0
            commands:
              - "lp user {player} parent add vip"
              - "eco give {player} 10000"
              - "give {player} elytra 1"
            display-name: "&5&lULTRA RARE! &dVIP Rank!"
```

</details>

<details>

<summary><strong>Economy (Vault)</strong></summary>

```yaml
command-rewards:
  - weight: 30.0
    commands:
      - "eco give {player} 1000"
    display-name: "&6+1000 Coins"
  - weight: 5.0
    commands:
      - "eco take {player} 500"
    display-name: "&c-500 Coins (Cursed Vault!)"
```

</details>

<details>

<summary><strong>Permissions (LuckPerms)</strong></summary>

```yaml
command-rewards:
  - weight: 15.0
    commands:
      - "lp user {player} permission set special.perk true"
    display-name: "&dSpecial Perk Unlocked!"
  - weight: 5.0
    commands:
      - "lp user {player} parent add vip"
    display-name: "&5&lVIP RANK UNLOCKED!"
  - weight: 20.0
    commands:
      - "lp user {player} permission settemp special.bonus.1h true 1h"
    display-name: "&a1-Hour Bonus Active!"
```

</details>

<details>

<summary><strong>Experience, items, titles</strong></summary>

```yaml
command-rewards:
  - weight: 25.0
    commands:
      - "xp add {player} 1000"
    display-name: "&a+1000 XP"
  - weight: 20.0
    commands:
      - "give {player} diamond 64"
    display-name: "&b+64 Diamonds"
  - weight: 5.0
    commands:
      - "title {player} title {\"text\":\"JACKPOT!\",\"color\":\"gold\",\"bold\":true}"
      - "playsound minecraft:ui.toast.challenge_complete master {player}"
    display-name: "&6&l* JACKPOT *"
```

</details>

### Notes

- Commands run as console (op permissions). Be careful what you allow.
- Multiple `commands` run in order.
- The player sees a message with the `display-name`.
- Works with any plugin driven by console commands: Vault plus an economy plugin for money, LuckPerms for permissions, vanilla `give` / `xp` / `title` with no plugin.

### Troubleshooting

**Commands are not running.** Check the console for errors when a vault opens, test the command manually in console, and make sure the required plugins are installed.

**"type: COMMAND is not valid" error.** Move the commands out of `weighted-items` into a `command-rewards` list.

**Player gets no message.** Set `display-name`, and raise the `weight` while testing.

***

## What's next?

{% content-ref url="messages.yml.md" %}
[messages.yml.md](messages.yml.md)
{% endcontent-ref %}

{% content-ref url="config.yml.md" %}
[config.yml.md](config.yml.md)
{% endcontent-ref %}

{% hint style="success" %}
**Loot tiers:** make tables `tier1`, `tier2`, `tier3` with better loot each step and assign them to chambers in different regions.
{% endhint %}

{% hint style="warning" %}
**Back up `loot.yml` before big changes.** A small YAML mistake is easy to make and easy to undo from a copy.
{% endhint %}
