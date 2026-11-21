#!/usr/bin/env python3
"""
Builds every Better Trial Chambers brand asset from one description of the mark.

    python brand/build.py

Writes into brand/:
  btc-icon-512.svg                     static icon
  btc-icon-512-animated.svg            same icon, self-animating (browsers/GitHub)
  btc-banner-1200x600.svg              static banner, 2:1 (BuiltByBit, Spigot)
  btc-banner-1280x420.svg              static banner, wide (Modrinth, GitHub)
  btc-banner-1280x420-animated.svg     wide banner, self-animating
  frames/icon/f000.svg ...             icon frame sequence
  frames/banner/f000.svg ...           banner frame sequence, cycling tagline

Nothing here depends on a library. Rasterising the frames does — see README.md.
"""

import math
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- palette ---

BG      = "#12171C"   # deep slate, the chamber in shadow
TEAL    = "#43D9C4"   # oxidised copper — the accent
EMBER   = "#F5A23C"   # the vault's glow
SLATE   = "#5C6E7E"   # chamber stone
TEXT    = "#FFFFFF"
MUTED   = "#93A5B3"

WORD_FONT = "'Space Grotesk','Outfit','Segoe UI',system-ui,sans-serif"
BODY_FONT = "'Inter','Segoe UI',system-ui,sans-serif"

# ------------------------------------------------------------- the tagline ---

LEAD = "Turn one-time Trial Chambers into "

# The lead is pinned and never moves. Only the ending changes, and it runs off
# to the right as it gets longer — the line going off-centre is the point, it's
# what makes you look at it.
PHRASES = [
    "replayable multiplayer dungeons.",
    "loot every player gets a shot at.",
    "griefing-protected areas.",
    "player-based content.",
    "content that comes back.",
    "custom events.",
    "a dungeon worth running twice.",
    "your next adventure.",
]

# Text widths in pixels at font-size 25, Inter 500, measured in a browser with
# the real fonts installed. Static SVG has no way to ask how wide a string is,
# and pinning the lead on the left means something has to know. Scale linearly
# with font size. Re-measure with getComputedTextLength() if you edit a string.
LEAD_W25 = 387.4
PHRASE_W25 = {
    "replayable multiplayer dungeons.": 378.8,
    "loot every player gets a shot at.": 357.0,
    "griefing-protected areas.":        281.3,
    "player-based content.":            245.2,
    "content that comes back.":         286.0,
    "custom events.":                   169.4,
    "a dungeon worth running twice.":   364.7,
    "your next adventure.":             234.9,
}
WORDMARK_W74 = 705.0   # "Better Trial Chambers", Space Grotesk 700, tracking -2.2

# One beat, in seconds. The bar fills, then sits full for a moment so you can
# see it arrive, and only then does the ending change — the bar has to finish
# visibly ahead of the swap or it reads as lagging behind it.
FILL = 3.50            # bar travels the width of the ending
FULL = 0.40            # bar sits full, nothing else happening
FADE = 0.15            # the swap itself
BEAT = FILL + FULL + FADE

# ------------------------------------------------------------ mark geometry ---
# Drawn once at 512x512 with the centre at (256, 256), then scaled into place.

C = 256.0

RING_R, RING_W = 196.0, 16.0          # outer wall, broken into eight blocks
RING_SEGMENTS = 8
ARC_R, ARC_W = 140.0, 22.0            # the reset sweep
ARC_SWEEP = 285.0                     # degrees of the sweep, arrowhead at the end

_ring_circ = 2 * math.pi * RING_R
_seg = _ring_circ / RING_SEGMENTS
RING_DASH = f"{_seg * 0.70:.2f} {_seg * 0.30:.2f}"
RING_STEP = 360.0 / RING_SEGMENTS     # one segment: the counter-rotation loop

_arc_circ = 2 * math.pi * ARC_R
ARC_DASH = f"{_arc_circ * ARC_SWEEP / 360:.2f} {_arc_circ * (1 - ARC_SWEEP / 360):.2f}"


def _arrowhead():
    """Triangle sitting at the leading end of the sweep, pointing along it."""
    a = math.radians(-90 + ARC_SWEEP)
    ex, ey = C + ARC_R * math.cos(a), C + ARC_R * math.sin(a)
    tx, ty = math.cos(a + math.pi / 2), math.sin(a + math.pi / 2)   # clockwise tangent
    px, py = -ty, tx                                               # perpendicular
    tip  = (ex + 34 * tx, ey + 34 * ty)
    back = (ex - 3 * tx, ey - 3 * ty)
    l    = (back[0] + 21 * px, back[1] + 21 * py)
    r    = (back[0] - 21 * px, back[1] - 21 * py)
    return "M{:.1f},{:.1f} L{:.1f},{:.1f} L{:.1f},{:.1f} Z".format(*tip, *l, *r)


ARROW = _arrowhead()

# The vault, drawn as a keyhole: a disc with a flared stem. Solid, never
# outlined — at avatar size an outline closes up and turns to mush.
KEY_CIRCLE = (C, 236.0, 38.0)
KEY_STEM = "M241,250 L229,314 H283 L271,250 Z"


def mark(idp, arc_rot=0.0, ring_rot=0.0, glow=1.0, animated=False):
    """The mark itself, in its own 512-unit coordinate space."""
    ring_anim = arc_anim = key_anim = ""
    if animated:
        ring_anim = (
            f'<animateTransform attributeName="transform" type="rotate" '
            f'values="0 {C} {C};{-RING_STEP} {C} {C}" dur="9s" repeatCount="indefinite"/>'
        )
        arc_anim = (
            f'<animateTransform attributeName="transform" type="rotate" '
            f'values="0 {C} {C};360 {C} {C}" dur="6s" repeatCount="indefinite"/>'
        )
        key_anim = (
            '<animate attributeName="opacity" values="0.45;0.95;0.45" '
            'dur="3s" repeatCount="indefinite"/>'
        )
    kx, ky, kr = KEY_CIRCLE
    return f"""
  <circle cx="{C}" cy="{C}" r="{RING_R}" fill="none" stroke="{SLATE}"
          stroke-width="{RING_W}" stroke-dasharray="{RING_DASH}"
          transform="rotate({ring_rot:.2f} {C} {C})">{ring_anim}</circle>

  <g transform="rotate({arc_rot:.2f} {C} {C})">{arc_anim}
    <circle cx="{C}" cy="{C}" r="{ARC_R}" fill="none" stroke="{TEAL}"
            stroke-width="{ARC_W}" stroke-linecap="butt"
            stroke-dasharray="{ARC_DASH}" transform="rotate(-90 {C} {C})"/>
    <path d="{ARROW}" fill="{TEAL}"/>
  </g>

  <g fill="{EMBER}" filter="url(#{idp}soft)" opacity="{glow:.3f}">
    <circle cx="{kx}" cy="{ky}" r="{kr + 6}"/>
    <path d="{KEY_STEM}" transform="translate(0 3) scale(1)"/>
    {key_anim}
  </g>
  <g fill="{EMBER}">
    <circle cx="{kx}" cy="{ky}" r="{kr}"/>
    <path d="{KEY_STEM}"/>
  </g>"""


def defs(idp):
    return f"""
  <defs>
    <radialGradient id="{idp}glow" cx="50%" cy="50%" r="58%">
      <stop offset="0%"   stop-color="{TEAL}" stop-opacity="0.10"/>
      <stop offset="55%"  stop-color="{TEAL}" stop-opacity="0.03"/>
      <stop offset="100%" stop-color="{TEAL}" stop-opacity="0"/>
    </radialGradient>
    <filter id="{idp}soft" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="16"/>
    </filter>
  </defs>"""


# ------------------------------------------------------------------ assets ---

def icon(arc_rot=0.0, ring_rot=0.0, glow=0.7, animated=False):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" role="img" aria-labelledby="t d">
  <title id="t">Better Trial Chambers icon</title>
  <desc id="d">A glowing vault keyhole at the centre of a sweeping reset arrow, ringed by broken chamber walls.</desc>
{defs('i')}
  <rect width="512" height="512" fill="{BG}"/>
  <rect width="512" height="512" fill="url(#iglow)"/>
{mark('i', arc_rot, ring_rot, glow, animated)}
</svg>
"""


def _layout(w, h, mark_on):
    """Where everything sits. Returns a dict so both callers agree."""
    cx = w / 2
    if not mark_on:
        # No mark: the wordmark takes the space, and the whole block sits high
        # enough that the tagline and its progress bar have air beneath them.
        return dict(cx=cx, mark_on=False, scale=0, mark_top=0,
                    word_y=h * 0.505, word_size=w * 0.0755,
                    tag_y=h * 0.690, tag_size=w * 0.0219)
    if h >= 600:
        return dict(cx=cx, mark_on=True, scale=0.42, mark_top=132,
                    word_y=434, word_size=88, tag_y=488, tag_size=27)
    return dict(cx=cx, mark_on=True, scale=0.38, mark_top=40,
                word_y=308, word_size=74, tag_y=356, tag_size=25)


def _tagline_x(L, phrase_idx=0):
    """
    The lead is pinned: it sits at the same x for every phrase, chosen so that
    the *first* phrase comes out centred. Every other ending is longer or
    shorter and runs out to the right from the same point, which is what pulls
    the eye — a line that quietly re-centres itself doesn't.
    """
    k = L["tag_size"] / 25.0
    lead_w = LEAD_W25 * k
    first_w = PHRASE_W25[PHRASES[0]] * k
    x0 = L["cx"] - (lead_w + first_w) / 2
    return x0, x0 + lead_w, PHRASE_W25[PHRASES[phrase_idx]] * k


def _progress(L, x, width, frac, idp=""):
    """A thin rule under the ending, filling up as its turn runs out."""
    # Deliberately faint. It's a hint that something is about to change, not a
    # second thing to read — at full weight it pulls the eye off the words.
    y = L["tag_y"] + L["tag_size"] * 0.52
    hgt = max(1.0, L["tag_size"] * 0.055)
    return (
        f'<rect x="{x:.1f}" y="{y:.2f}" width="{width:.1f}" height="{hgt:.2f}" '
        f'rx="{hgt / 2:.2f}" fill="{TEAL}" opacity="0.08"/>'
        f'<rect x="{x:.1f}" y="{y:.2f}" width="{width * frac:.1f}" height="{hgt:.2f}" '
        f'rx="{hgt / 2:.2f}" fill="{TEAL}" opacity="0.38">{idp}</rect>'
    )


def banner(w, h, phrase_idx=0, phrase_op=1.0, frac=0.0,
           arc_rot=0.0, ring_rot=0.0, glow=0.7, animated=False, mark_on=True):
    """Wordmark over a tagline whose ending cycles. Optionally over the mark."""
    L = _layout(w, h, mark_on)
    cx, tag_size = L["cx"], L["tag_size"]
    lead_x, phrase_x, phrase_w = _tagline_x(L, phrase_idx)

    # The lead never animates — it is drawn once, and that is the whole point.
    lead = (f'<text x="{lead_x:.1f}" y="{L["tag_y"]:.1f}" font-family="{BODY_FONT}" '
            f'font-size="{tag_size:.1f}" font-weight="500" fill="{MUTED}" '
            f'text-anchor="start">{LEAD.rstrip()}</text>')

    if animated:
        n = len(PHRASES)
        total = n * BEAT
        parts = []
        for i, p in enumerate(PHRASES):
            _, px, pw = _tagline_x(L, i)
            bar_t, bar_v = _bar_anim(i, n, total, round(pw, 1))
            op_t, op_v = _phrase_anim(i, n, total)
            parts.append(
                f'<g opacity="{1 if i == 0 else 0}">'
                f'<text x="{px:.1f}" y="{L["tag_y"]:.1f}" font-family="{BODY_FONT}" '
                f'font-size="{tag_size:.1f}" font-weight="500" fill="{TEAL}" '
                f'text-anchor="start">{p}</text>'
                + _progress(L, px, pw, 0.0,
                            f'<animate attributeName="width" dur="{total}s" '
                            f'repeatCount="indefinite" values="{bar_v}" '
                            f'keyTimes="{bar_t}"/>')
                + f'<animate attributeName="opacity" dur="{total}s" '
                  f'repeatCount="indefinite" values="{op_v}" '
                  f'keyTimes="{op_t}"/></g>'
            )
        cycling = "".join(parts)
    else:
        cycling = (
            f'<text x="{phrase_x:.1f}" y="{L["tag_y"]:.1f}" font-family="{BODY_FONT}" '
            f'font-size="{tag_size:.1f}" font-weight="500" fill="{TEAL}" '
            f'text-anchor="start" opacity="{phrase_op:.3f}">{PHRASES[phrase_idx]}</text>'
            + _progress(L, phrase_x, phrase_w, frac)
        )

    if mark_on:
        mark_h = 512 * L["scale"]
        mark_block = (
            f'<g transform="translate({cx - mark_h / 2:.1f} {L["mark_top"]}) '
            f'scale({L["scale"]})">{mark("b", arc_rot, ring_rot, glow, animated)}</g>'
        )
        desc = ("A vault keyhole inside a reset arrow, above the Better Trial "
                "Chambers wordmark and its tagline.")
    else:
        mark_block = ""
        desc = "The Better Trial Chambers wordmark above its tagline."

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" role="img" aria-labelledby="t d">
  <title id="t">Better Trial Chambers</title>
  <desc id="d">{desc}</desc>
{defs('b')}
  <rect width="{w}" height="{h}" fill="{BG}"/>
  <rect width="{w}" height="{h}" fill="url(#bglow)"/>
  {mark_block}
  <text x="{cx}" y="{L['word_y']:.1f}" font-family="{WORD_FONT}"
        font-size="{L['word_size']:.1f}" font-weight="700"
        letter-spacing="{-2.2 * L['word_size'] / 74:.2f}" fill="{TEXT}"
        text-anchor="middle">Better Trial Chambers</text>

  {lead}
  {cycling}
</svg>
"""


def _keys(pairs, total):
    """
    Build a matching (keyTimes, values) pair from (seconds, value) points.
    Times are clamped into the loop and forced non-decreasing, which is what
    SMIL requires and what browsers quietly refuse to animate without.
    """
    times, vals, last = [], [], 0.0
    for t, v in pairs:
        t = min(1.0, max(0.0, t / total))
        t = max(t, last)
        last = t
        times.append(f"{t:.5f}")
        vals.append(f"{v:g}" if isinstance(v, float) else str(v))
    return ";".join(times), ";".join(vals)


def _phrase_anim(i, n, total):
    """
    Phrase i is faded in over the swap that ends phrase i-1, holds for the
    whole of its own beat, and is faded out by the swap that ends it.
    """
    s, e = i * BEAT, (i + 1) * BEAT
    if i == 0:
        # Visible from the first instant; its fade-in is the loop's last swap.
        pts = [(0, 1), (e - FADE, 1), (e, 0),
               (n * BEAT - FADE, 0), (n * BEAT, 1)]
    else:
        pts = [(0, 0), (s - FADE, 0), (s, 1), (e - FADE, 1), (e, 0),
               (n * BEAT, 0)]
    return _keys(pts, total)


def _bar_anim(i, n, total, w):
    """
    The bar starts empty when its phrase lands, reaches the end of the ending
    after FILL, and then stays full through the pause and the swap.
    """
    s, e = i * BEAT, (i + 1) * BEAT
    pts = [(0, 0), (s, 0), (s + FILL, w), (e, w), (e + 0.0001, 0),
           (n * BEAT, 0)]
    return _keys(pts, total)


# ------------------------------------------------------------------ frames ---

FPS = 20
# The mark is left out of the animated banner. The wordmark and the cycling
# ending are already carrying it, and a second thing moving splits the
# attention — the mark has its own icon to live in.
BANNER_MARK = False


def ease(t):
    return t * t * (3 - 2 * t)


def build_frames():
    """Icon: a steady loop. Banner: a sweep-and-swap on every tagline."""
    icon_dir = os.path.join(HERE, "frames", "icon")
    ban_dir = os.path.join(HERE, "frames", "banner")
    for d in (icon_dir, ban_dir):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)

    n_icon = 72   # 3.6s at 20fps
    for f in range(n_icon):
        t = f / n_icon
        write(os.path.join(icon_dir, f"f{f:03d}.svg"),
              icon(arc_rot=360 * t,
                   ring_rot=-RING_STEP * t,
                   glow=0.55 + 0.30 * (0.5 - 0.5 * math.cos(2 * math.pi * t))))

    # Everything except the ending and its progress bar holds perfectly still.
    # That is what the animation is about, and it is also why the exported
    # file stays small: WebP only pays for what changes between frames.
    per_beat = round(BEAT * FPS)          # 81 frames: 70 filling, 8 full, 3 swap
    fill_f = round(FILL * FPS)
    fade_f = max(1, round(FADE * FPS))
    f = 0
    for i in range(len(PHRASES)):
        for k in range(per_beat):
            # The bar reaches the end with FULL seconds still to run, so it is
            # visibly finished before the ending changes.
            frac = min(1.0, k / fill_f)
            if k < fade_f:
                op = ease((k + 1) / (fade_f + 1))
            elif k >= per_beat - fade_f:
                op = ease((per_beat - k) / (fade_f + 1))
            else:
                op = 1.0
            write(os.path.join(ban_dir, f"f{f:04d}.svg"),
                  banner(1280, 420, i, phrase_op=op, frac=frac,
                         arc_rot=0, ring_rot=0, glow=0.58, mark_on=BANNER_MARK))
            f += 1
    return n_icon, f


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def main():
    write(os.path.join(HERE, "btc-icon-512.svg"), icon())
    write(os.path.join(HERE, "btc-icon-512-animated.svg"), icon(animated=True))
    write(os.path.join(HERE, "btc-banner-1200x600.svg"), banner(1200, 600))
    write(os.path.join(HERE, "btc-banner-1280x420.svg"), banner(1280, 420))
    write(os.path.join(HERE, "btc-banner-1280x420-nomark.svg"),
          banner(1280, 420, mark_on=False))
    write(os.path.join(HERE, "btc-banner-1280x420-animated.svg"),
          banner(1280, 420, animated=True, mark_on=BANNER_MARK))
    write(os.path.join(HERE, "btc-banner-1280x420-animated-withmark.svg"),
          banner(1280, 420, animated=True, mark_on=True))
    ni, nb = build_frames()
    print(f"icon: {ni} frames, banner: {nb} frames, {FPS} fps")


if __name__ == "__main__":
    main()
