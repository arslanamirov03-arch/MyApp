#!/usr/bin/env python3
"""Draws the launcher icon: a glowing flame on a dark ground.

Emits the legacy square icons plus the adaptive-icon foreground layer.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "app/src/main/res")

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

SS = 4  # supersampling


def flame_polygon(cx, cy, half_w, height, lean=0.0, wobble=0.0, seed=0.0):
    """A teardrop silhouette: wide low belly, tapering tip."""
    pts_r, pts_l = [], []
    steps = 160
    for i in range(steps + 1):
        t = i / steps                      # 0 at the base, 1 at the tip
        w = math.sin(math.pi * (t ** 0.62)) ** 1.25
        w *= (1.0 - 0.18 * t)
        w += wobble * math.sin(t * 9.0 + seed) * (1.0 - t) * 0.12
        w = max(w, 0.0) * half_w
        y = cy - t * height
        x = cx + lean * (t ** 2) * half_w
        pts_r.append((x + w, y))
        pts_l.append((x - w, y))
    return pts_r + pts_l[::-1]


def vertical_gradient(size, stops):
    """stops: list of (position 0..1 from bottom, (r,g,b))."""
    w, h = size
    ys = np.linspace(1.0, 0.0, h)          # 1 at the top row, 0 at the bottom
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], dtype=float)
    out = np.zeros((h, 3))
    for c in range(3):
        out[:, c] = np.interp(1.0 - ys, pos, cols[:, c])
    img = np.repeat(out[:, None, :], w, axis=1)
    return Image.fromarray(img.astype(np.uint8), "RGB")


def render_flame(size, scale=1.0):
    """Returns an RGBA flame sized to fit `size` px, transparent elsewhere."""
    n = size * SS
    cx = n * 0.5
    base = n * (0.5 + 0.36 * scale)
    height = n * 0.78 * scale
    half_w = n * 0.27 * scale

    outer = Image.new("L", (n, n), 0)
    ImageDraw.Draw(outer).polygon(
        flame_polygon(cx, base, half_w, height, lean=0.05, wobble=1.0, seed=0.7), fill=255)

    inner = Image.new("L", (n, n), 0)
    ImageDraw.Draw(inner).polygon(
        flame_polygon(cx, base - n * 0.02 * scale, half_w * 0.52, height * 0.52,
                      lean=0.02, wobble=0.8, seed=2.3), fill=255)

    outer_col = vertical_gradient((n, n), [
        (0.00, (120, 20, 6)),
        (0.18, (232, 74, 10)),
        (0.42, (255, 138, 26)),
        (0.72, (255, 96, 14)),
        (1.00, (196, 40, 8)),
    ])
    inner_col = vertical_gradient((n, n), [
        (0.00, (255, 190, 90)),
        (0.35, (255, 246, 210)),
        (0.75, (255, 214, 120)),
        (1.00, (255, 150, 40)),
    ])

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    img.paste(outer_col, (0, 0), outer)
    img.paste(inner_col, (0, 0), inner.filter(ImageFilter.GaussianBlur(n * 0.012)))

    # soft glow around the whole shape
    glow = outer.filter(ImageFilter.GaussianBlur(n * 0.055)).point(lambda v: int(v * 0.55))
    glow_img = Image.new("RGBA", (n, n), (255, 108, 20, 0))
    glow_img.putalpha(glow)
    img = Image.alpha_composite(glow_img, img)

    return img.resize((size, size), Image.LANCZOS)


def rounded_background(size, radius_frac=0.22):
    n = size * SS
    bg = vertical_gradient((n, n), [
        (0.00, (26, 12, 6)),
        (0.55, (14, 10, 12)),
        (1.00, (9, 8, 11)),
    ]).convert("RGBA")
    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1],
                                           radius=int(n * radius_frac), fill=255)
    bg.putalpha(mask)
    return bg.resize((size, size), Image.LANCZOS)


def main():
    for bucket, size in LEGACY.items():
        d = os.path.join(RES, "mipmap-" + bucket)
        os.makedirs(d, exist_ok=True)
        img = rounded_background(size)
        img = Image.alpha_composite(img, render_flame(size, scale=0.80))
        img.save(os.path.join(d, "ic_launcher.png"))
        print("wrote", os.path.join("mipmap-" + bucket, "ic_launcher.png"), size)

    for bucket, size in ADAPTIVE.items():
        d = os.path.join(RES, "mipmap-" + bucket)
        os.makedirs(d, exist_ok=True)
        # the adaptive foreground must keep its subject inside the middle two thirds
        fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        fg = Image.alpha_composite(fg, render_flame(size, scale=0.52))
        fg.save(os.path.join(d, "ic_launcher_foreground.png"))
        print("wrote", os.path.join("mipmap-" + bucket, "ic_launcher_foreground.png"), size)


if __name__ == "__main__":
    main()
