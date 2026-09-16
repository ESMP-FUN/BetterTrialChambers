---
description: >-
  The fastest way to get BetterTrialChambers running, and for most servers, the
  only setup you need.
layout:
  width: default
  title:
    visible: true
  description:
    visible: true
  tableOfContents:
    visible: true
  outline:
    visible: true
  pagination:
    visible: true
  metadata:
    visible: true
  tags:
    visible: true
  actions:
    visible: true
---

# Quick Start

Set up BetterTrialChambers so it finds and manages every natural Trial Chamber on your server by itself. This is the only setup most servers need. No WorldEdit, no per-chamber commands.

## 1. Run the setup tour

In-game as an operator, run:

```
/trial setup
```

Or click the green text in chat:

<figure><img src="../.gitbook/assets/Screenshot 2026-06-27 153421.png" alt=""><figcaption></figcaption></figure>

The tour walks you through BTC's main settings one at a time, in plain English, with **Enable / Skip / Disable** for each. Nothing is forced. You can stop at any point and re-run it later. On Paper 1.21.7+ it is a dialog window; on older servers it is the same content as clickable chat.

<figure><img src="../.gitbook/assets/Screenshot 2026-06-27 153509.png" alt=""><figcaption></figcaption></figure>

## 2. Enable these two settings in the tour

* **Auto-discover Trial Chambers.** BTC finds naturally-generated chambers as players explore and registers each one for you.
* **Snapshot discovered chambers.** Saves a backup of each chamber the moment it is found. A chamber needs a snapshot before it can reset, so this makes it reset-ready right away.

{% hint style="success" %}
That is the whole setup. With those two on, every Trial Chamber a player walks into is registered, backed up, and put on an automatic reset schedule.
{% endhint %}

## 3. Done

From here on:

* Chambers are found and backed up automatically as players explore.
* Each chamber resets on a schedule (default every 2 days). Change it in the tour or in [Basic Configuration](basic-configuration.md).
* Every player gets their own loot with their own cooldown, so the second player into a chamber does not find empty vaults.

To adjust loot, reset timing, or protection, see [Basic Configuration](basic-configuration.md). You do not have to change anything to have a working setup.

## Good to know

{% hint style="info" %}
**Very old worlds:** Auto-discovery looks for the blocks a Trial Chamber is built from (tuff bricks, copper, vaults, trial spawners). On worlds from before 1.21, a decorative player build made of those blocks could be mistaken for a chamber. This does not happen on fresh or normal worlds. If a wrong one is ever registered, remove it with `/trial delete <name>`.
{% endhint %}

{% hint style="info" %}
**Re-run anytime:** `/trial setup` is always available. Every setting it covers is also in `config.yml` if you prefer to edit it directly.
{% endhint %}

## No natural chambers on your server?

Some servers have no naturally-generated Trial Chambers: superflat and one-block worlds, custom world generation, or `generate-structures` turned off. If that is you, auto-discovery has nothing to find, and you register chambers by hand instead:

{% content-ref url="your-first-chamber.md" %}
[your-first-chamber.md](your-first-chamber.md)
{% endcontent-ref %}

For everyone else, you're already finished.
