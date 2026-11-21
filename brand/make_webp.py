#!/usr/bin/env python3
"""
Turns the icon into an animated .webp — the format Modrinth, Spigot and
BuiltByBit actually accept.

    pip install resvg-py
    python brand/make_webp.py            # needs ffmpeg on PATH

Two things matter for a small, smooth file, and they pull against each other:

* Smoothness is degrees of turn per frame, not frames per second. At 72 frames
  the arrow jumps 5 degrees a frame and you can see it stepping. 120 frames is
  3 degrees, which reads as continuous.
* Size is how many pixels change between frames. WebP stores each frame as the
  difference from the last one, so anything that repaints a large area every
  frame costs more than the rest of the animation put together. Two things did:
  the glow pulsing (a blur, so every pixel changes) and the outer wall
  counter-rotating (a second wide moving band). Both are held still here. The
  arrow alone carries the motion, and the file went from 700 KB to about 120.

The self-animating `btc-icon-512-animated.svg` keeps the pulse and the drifting
wall. It's vector, so those cost nothing there.
"""

import os
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))

FRAMES = 120      # 3 degrees of turn per frame
FPS = 20          # 6 second loop, same pace as the animated SVG
GLOW = 0.62       # held steady, so only the arrow differs frame to frame

# Rendered at 2x the size it is shown at, so it stays crisp on a high-DPI
# screen. Modrinth shows a project icon at about 96px.
# Quality 52 is not a compromise here: above it the file grows fast and the
# picture stops improving, because the art is flat colour on flat background
# and there is nothing left for the extra bytes to describe.
SIZES = [(192, "btc-icon-192-animated.webp", 52),
         (384, "btc-icon-384-animated.webp", 48)]


def main():
    try:
        import resvg_py
    except ImportError:
        sys.exit("pip install resvg-py")
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg is not on PATH")

    tmp = tempfile.mkdtemp(prefix="btc-webp-")
    try:
        for f in range(FRAMES):
            t = f / FRAMES
            svg = build.icon(arc_rot=360 * t, ring_rot=0, glow=GLOW)
            png = resvg_py.svg_to_bytes(svg_string=svg)
            with open(os.path.join(tmp, f"f{f:03d}.png"), "wb") as fh:
                fh.write(bytes(png))

        for px, name, q in SIZES:
            out = os.path.join(HERE, name)
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error",
                 "-framerate", str(FPS), "-i", os.path.join(tmp, "f%03d.png"),
                 "-vf", f"scale={px}:{px}:flags=lanczos",
                 "-c:v", "libwebp_anim", "-loop", "0",
                 "-q:v", str(q), "-compression_level", "5",
                 out],
                check=True)
            print(f"{name}  {px}x{px}  {FRAMES}f @ {FPS}fps  "
                  f"{os.path.getsize(out) / 1024:.0f} KB")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
