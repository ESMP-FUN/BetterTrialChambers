# Give a chamber a name

Each chamber has a name players see, in the reset warnings and in the vault
messages. The plugin picks one for you when it finds a chamber. This page
changes it.

## Step 1 - Rename it from the menu

Open the chamber's page from the [admin menu](admin-menu.md) and click
**Rename**. The menu closes and chat asks for the new name:

<figure><img src="../.gitbook/assets/rename-prompt.png" alt="Chat line 'Type a new display name for auto_world_89_206 in chat (or 'cancel').'"><figcaption></figcaption></figure>

Type the name and press Enter. Emerald SMP calls its first chamber
**Emerald Depths**:

<figure><img src="../.gitbook/assets/rename-done.png" alt="Chat line 'Chamber display name set to Emerald Depths.'"><figcaption></figcaption></figure>

The chamber's page opens again behind it. Type `cancel` instead to keep the
old name. Spaces are fine.

## Step 2 - Or rename it with a command

The same, in one line:

```
/trial rename auto_world_89_206 Emerald Depths
```

To go back to no name at all, use `none` as the name. Players then see the
long name from [Your first chamber is found](../getting-started/first-chamber.md).

## Where the name shows

Players see the new name straight away, the next time they get a message
about that chamber:

```
[BTC] You have already opened this Normal Vault! It opens again when Emerald Depths resets in 1d 23h 54m.
[BTC] Warning: Emerald Depths will reset in 5 minutes!
```

Commands still use the long name, so nothing you typed before stops working.

## Pick the names it chooses from

New chambers get a name from a list in `config.yml`. Put your own names at
the top, or replace the list:

```yaml
naming:
  auto-assign: true
  make-up-names: true
  name-pool:
    - Emerald Depths
    - The Copper Mines
    - Bastion
```

* **auto-assign**, give every new chamber a name from the list. Set it to
  `false` to leave new chambers without one until you rename them.
* **make-up-names**, once every name in the list is taken, make up new ones
  like Copperhold or Galewatch.
* **name-pool**, the list itself. A name another chamber already has is
  skipped.

Save the file and run `/trial reload` after changing it.

Next: [Resets and the exit point](resets.md).
