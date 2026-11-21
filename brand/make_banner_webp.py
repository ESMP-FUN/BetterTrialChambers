#!/usr/bin/env python3
"""
Turns the banner frames into an animated .webp — the format Modrinth accepts.

    pip install resvg-py
    python brand/make_banner_webp.py     # needs ffmpeg on PATH

**The fonts must be installed** (Space Grotesk, Inter) or the text renders in
Segoe UI. Unlike the icon, this one has live type in it.

A banner is six times the area of the icon and this runs for 17.6 seconds, so
by rights it should be enormous. It isn't, because nothing moves except the
last few words and the rule underneath them. WebP stores each frame as the
difference from the one before, and for most of a beat that difference is a
progress bar growing by nine pixels.
"""

import glob
import os
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "btc-banner-animated.webp")


def main():
    try:
        import resvg_py
    except ImportError:
        sys.exit("pip install resvg-py")
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg is not on PATH")

    svgs = sorted(glob.glob(os.path.join(HERE, "frames", "banner", "f*.svg")))
    if not svgs:
        sys.exit("no frames — run build.py first")

    tmp = tempfile.mkdtemp(prefix="btc-banner-")
    try:
        for i, f in enumerate(svgs):
            with open(f, encoding="utf-8") as fh:
                png = resvg_py.svg_to_bytes(svg_string=fh.read())
            with open(os.path.join(tmp, f"f{i:04d}.png"), "wb") as fh:
                fh.write(bytes(png))

        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error",
             "-framerate", str(build.FPS), "-i", os.path.join(tmp, "f%04d.png"),
             "-c:v", "libwebp_anim", "-loop", "0",
             "-q:v", "62", "-compression_level", "5", OUT],
            check=True)
        print(f"btc-banner-animated.webp  {len(svgs)} frames @ {build.FPS}fps  "
              f"{os.path.getsize(OUT) / 1024:.0f} KB")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
