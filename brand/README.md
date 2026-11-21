# Better Trial Chambers — brand assets

Everything needed to produce a Better Trial Chambers image, and the reasoning
behind the choices so nobody has to guess later.

Every file here is generated. Edit `build.py`, then:

```bash
python brand/build.py
```

## The mark

A glowing **vault keyhole**, wrapped in a **sweeping arrow**, ringed by a
**broken wall**.

Three parts, three claims:

- The **keyhole** is the vault — the one thing a Trial Chamber is really about,
  and the thing vanilla lets exactly one player open. It glows because it's
  live, not spent.
- The **arrow** is the reset. It doesn't close, because the chambers don't
  finish. This is the whole product in one shape.
- The **broken ring** is the chamber wall, drawn in stone grey and left in
  eight blocks. It reads as a map view of a chamber, and it gives the mark an
  outer silhouette that survives being shrunk.

The keyhole is **filled, never outlined**. At avatar size a solid shape keeps
its silhouette while an outline fills in and turns to mush.

### No text in the icon

The icon is pure geometry. On Modrinth, Spigot and Discord the project name is
always rendered right next to the icon by the site itself, so letters in the
icon are duplicated information that costs you legibility at 40px. Every large
plugin that reads as a *product* rather than a hobby upload does this.

## Colours

| Role | Hex | Notes |
|---|---|---|
| Accent | `#43D9C4` | Oxidised copper. The reset arrow, the tagline's ending. |
| Ember | `#F5A23C` | The vault's glow. Used only on the keyhole — it's the reward. |
| Stone | `#5C6E7E` | The chamber wall. Structure, never emphasis. |
| Background | `#12171C` | Deep slate; the chamber in shadow. |
| Wordmark | `#FFFFFF` | |
| Body text | `#93A5B3` | The fixed half of the tagline. |

The teal and the ember are the two colours a player actually sees standing in a
Trial Chamber — oxidised copper walls, and the orange light of an unopened
vault. The palette isn't decorative; it's the room.

**One accent gets the attention.** Ember appears in exactly one place, at the
centre, and everything else points at it. If ember starts turning up in text or
borders, the mark stops meaning anything.

### Contrast

Measured against our background `#12171C`:

| Colour | Ratio | |
|---|---|---|
| `#43D9C4` teal | 10.7:1 | fine |
| `#F5A23C` ember | 9.6:1 | fine |
| `#93A5B3` body text | 7.2:1 | fine |
| `#5C6E7E` stone | 3.1:1 | fine for shapes, **never for text** |
| `#FFFFFF` | 17.5:1 | fine |

Teal on Modrinth's light theme (`#FFFFFF`) is **1.6:1** — unusable. This never
comes up for the images, because they carry their own dark background. It only
matters if you ever set teal as a Discord role colour; use `#1E9E8C` for that
and keep `#43D9C4` everywhere else.

## Type

| | Font | Weight | Tracking |
|---|---|---|---|
| Wordmark | **Space Grotesk** | 700 | `-2.2` (about `-0.03em`) |
| Tagline | **Inter** | 500 | normal |

Both are free on Google Fonts, and both match the Community Chat assets — the
two products should look like they came from the same person, because they did.

**Never use a pixel or Minecraft-style font.** This plugin is infrastructure
that makes an admin's life easier, not a blocky novelty, and pixel type reads
as "hobby plugin" to exactly the server owners worth having.

## Tagline

```
Turn one-time Trial Chambers into replayable multiplayer dungeons.
```

The ending rotates. The full set lives in `PHRASES` in `build.py`:

1. replayable multiplayer dungeons.
2. loot every player gets a shot at.
3. griefing-protected areas.
4. player-based content.
5. content that comes back.
6. custom events.
7. a dungeon worth running twice.
8. your next adventure.

The fixed half is grey and the ending is teal, so the eye lands on the part
that changes.

**The lead is pinned and never moves or fades.** Only the ending is animated.
The lead sits at the x that makes the *first* sentence come out centred, and
every other ending runs out to the right from that same point — so the shorter
ones sit left of centre and the longer ones overhang. That asymmetry is
deliberate: a line that quietly re-centres itself on every swap is a line
nobody looks at twice.

A thin rule fills up underneath the ending as its turn runs out. It tells you
something is about to change before it changes, which is the difference between
an animation you watch and one you catch the tail of.

It is drawn **faint on purpose** — a hairline at 38% against an 8% track. It is
a hint, not a second thing to read; at full weight it competes with the words it
is supposed to be pointing at.

### Changing a phrase

Static SVG can't ask how wide a string is, and pinning the lead means something
has to know. So `PHRASE_W25` in `build.py` holds a measured width for every
string. **If you add or edit a phrase, re-measure it**, or its progress bar
will be the wrong length:

```js
// paste into a browser console on any page, with Inter installed
const s=document.createElementNS('http://www.w3.org/2000/svg','svg');document.body.append(s);
const t=document.createElementNS('http://www.w3.org/2000/svg','text');
t.setAttribute('font-family',"'Inter','Segoe UI',system-ui,sans-serif");
t.setAttribute('font-size',25);t.setAttribute('font-weight',500);
t.textContent="your new phrase here.";s.append(t);t.getComputedTextLength()
```

Keep the endings roughly this length or shorter. Much longer and the line
overhangs far enough to look broken rather than dynamic.

## Files

| File | Size | Used for |
|---|---|---|
| `btc-icon-512.svg` | 512×512 | Modrinth icon, Spigot icon, Discord avatar, favicon |
| `btc-icon-512-animated.svg` | 512×512 | anywhere that renders SVG live |
| `btc-icon-192-animated.webp` | 192×192 | **the animated icon to upload.** 135 KB |
| `btc-icon-384-animated.webp` | 384×384 | same, for anywhere it's shown large. 326 KB |
| `btc-banner-animated.webp` | 1280×420 | **the animated banner to upload.** 130 KB |
| `btc-banner-1280x420-nomark.svg` | 1280×420 | its static fallback |
| `btc-banner-1280x420.svg` | 1280×420 | same, with the mark — for anywhere the mark isn't already shown |
| `btc-banner-1200x600.svg` | 1200×600 | BuiltByBit and Spigot resource banners (2:1) |
| `btc-banner-1280x420-animated.svg` | 1280×420 | anywhere that renders SVG live |
| `btc-banner-1280x420-animated-withmark.svg` | 1280×420 | same, if you want the mark in it |
| `frames/icon/f000.svg …` | 72 frames | source for the animated icon |
| `frames/banner/f0000.svg …` | 648 frames | source for the animated banner |

## Animation — what works where

There are two different animated versions here because there are two different
kinds of place to put one, and neither works in the other's slot.

**`-animated.svg`** animates itself, no tooling needed. It works in a browser,
and it works in a GitHub README as long as you reference it as an image from a
`raw.githubusercontent.com` URL. It does **not** work on Modrinth, Spigot or
BuiltByBit — all three strip SVG for security reasons.

**`frames/`** is the answer for Modrinth. Turn the frames into an animated
`.webp` or `.gif`, which those sites do accept as an ordinary image. Details
below.

**The scroll-text library you found won't work on Modrinth.** It's JavaScript,
and Modrinth's description is sanitised Markdown — no scripts run there at all.
The animated WebP gets you the same effect and it can't be blocked, because as
far as the page is concerned it's just a picture. Use the JS library on your own
site (esmp.fun) if you want it there too.

### The animated icon

Already built — `btc-icon-192-animated.webp`. Upload that one. To rebuild it:

```bash
pip install resvg-py
python brand/make_webp.py
```

120 frames at 20 fps, a 6 second loop, the same pace as the SVG. Slow on
purpose: an avatar that spins hard is a distraction in a member list.

The WebP is a slightly simpler animation than the SVG, and deliberately so.
Two things had to go:

- **The pulsing glow.** It's a blur, so it repaints every pixel on every frame.
- **The counter-rotating outer wall.** A second wide band of movement.

WebP stores each frame as the difference from the one before it, so a large
area that changes every frame costs more than everything else put together.
Dropping both took the file from about 700 KB to 135 KB and let the frame
count go *up* — from 72 frames to 120, which is what actually fixed the
stepping. Smoothness is degrees of turn per frame, not frames per second:
72 frames is 5° a frame and you can see it jump, 120 is 3° and you can't.

The SVG keeps the pulse and the drifting wall. It's vector, so they're free
there.

### The animated banner

Already built — `btc-banner-animated.webp`, 648 frames at 20 fps: eight endings,
4.05 seconds each, 32.4 seconds round the loop. Both loops are seamless.

The beat is built from three numbers in `build.py`, and the order matters more
than the total:

| | |
|---|---|
| `FILL` | 3.50s — the bar travels the width of the ending |
| `FULL` | 0.40s — the bar sits finished, nothing else moving |
| `FADE` | 0.15s — the ending changes |

The 0.4s pause is the part that's easy to leave out and wrong to. If the swap
starts while the bar is still travelling, the bar reads as *reacting to* the
change rather than counting down to it, and the whole point of having it is
lost. It has to arrive first, visibly, and then the words change.

**The mark is not in it.** The wordmark and the cycling ending already carry the
banner, and a second moving thing splits the attention — the mark has its own
icon to live in. `btc-banner-1280x420-animated-withmark.svg` exists if you
disagree.

Nothing moves except the last few words and the rule under them. That's the
design, and it's also why a banner six times the icon's area, running nine times
as long, comes out at **130 KB** — smaller than the icon. For most of a beat the
difference between one frame and the next is a progress bar four pixels wider,
and during the 0.4s pause there is no difference at all, so the encoder stores
those stretches as one long frame instead of eight.

To rebuild:

```bash
python brand/build.py && python brand/make_banner_webp.py
```

Unlike the icon, this one has live type in it, so **the fonts must be installed**
or it renders in Segoe UI. See below.

### If you'd rather build it by hand

`frames/icon/` and `frames/banner/` are numbered SVG sequences, so any tool that
imports an image sequence will take them — Photoshop, GIMP, ezgif.com. That's
what the numbering is for.

If a site rejects WebP, GIF works everywhere, at the cost of size and colour:

```bash
ffmpeg -framerate 20 -i frames/png/f%04d.png -vf "split[a][b];[a]palettegen[p];[b][p]paletteuse" -loop 0 btc-banner-animated.gif
```

**Keep a static export too.** Modrinth shows the icon in places that don't
always animate, and a resource page should never depend on animation to make
sense. `btc-icon-512.svg` and `btc-banner-1280x420.svg` are the fallbacks.

### Fonts before you export

**The icon needs nothing.** It is pure geometry with no text, so it renders
identically everywhere, on any machine, forever.

**The banners contain live text**, so before any banner SVG is rasterised or
leaves this folder:

1. Install [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk)
   and [Inter](https://fonts.google.com/specimen/Inter). Without them the SVG
   silently falls back to Segoe UI and loses its character.
2. For a file you're handing to someone else, convert text to outlines —
   Inkscape `Path → Object to Path`, Illustrator `Type → Create Outlines`.
   After this the file no longer depends on any font being installed.

## Safe areas

Discord crops the app icon to a **circle**, and Modrinth rounds the icon's
corners. Everything in `btc-icon-512` sits inside that circle with room to
spare — the furthest point is the outer wall's edge at 204px from centre,
against a 256px radius. If you edit the icon, keep artwork within about 210px
of centre and it will survive the crop.
