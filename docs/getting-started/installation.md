# Install it

Put the plugin on your server and check that it started.

## Step 1 - Download the jar

Get `BetterTrialChambers-<version>.jar` from
[Modrinth](https://modrinth.com/plugin/trialchamberpro) or
[GitHub Releases](https://github.com/ESMP-FUN/BetterTrialChambers/releases).

There is one jar for every Minecraft version from 1.21.1 to 26.3. It picks
the right parts for your server when it starts.

{% hint style="warning" %}
**Used a jar ending in `-mc26` or `-mc263` before?** Delete it from
`plugins/` and use this one instead. Your chambers, settings and player data
stay as they are.
{% endhint %}

## Step 2 - Put it on your server

Stop the server, move the jar into its `plugins/` folder, and start it again.
When the console shows this line, the plugin is ready:

```
[BTC] BetterTrialChambers is fully initialized and ready!
```

The first start makes a folder with everything in it:

```
plugins/BetterTrialChambers/
  config.yml            the settings
  loot.yml              what vaults hand out
  messages.yml          every line of text players see
  spawner_presets.yml   ready-made Trial Spawners you can give out
  database.db           chambers, cooldowns and player stats
```

Leave them alone for now. The setup tour in the next step changes the
settings for you, from inside the game.

{% hint style="info" %}
**The console shows errors instead?** Check you are on Paper (or Purpur,
Pufferfish or Folia) and on Java 21 or newer. Spigot is not supported. See
[Troubleshooting](../troubleshooting.md) for the rest.
{% endhint %}

## Step 3 - Join as an operator

Join the server with an operator account. As you join, the plugin offers you
the setup tour in chat:

<figure><img src="../.gitbook/assets/setup-reminder.png" alt="Chat line 'New to BetterTrialChambers? Take a quick, friendly tour...' with the green [Start /trial setup] button"><figcaption></figcaption></figure>

You can click the green text, or type the command yourself. The next step
does that.

{% hint style="success" %}
**Updating later?** Stop the server, make a copy of the
`plugins/BetterTrialChambers/` folder, swap the jar and start it. The plugin
brings your saved data up to date by itself.
{% endhint %}

Next: [Run the setup tour](quick-start.md).
