#!/usr/bin/env python3
"""Turn the raw Higgsfield generations into the game's asset set.

Inputs (raw generations, not committed):
  milkyway.png  2752x1536  bg_milkyway
  planets.png   2048x2048  sheet_planets  (4x4 grid on #00FF00 key)
  ship2.png     1024x1024  sprite_ship    (on #00FF00 key)
  ship.png      1024x1024  first ship pass - circular medallion, reused as the icon

Outputs -> ../assets  (wired by design/assets.csv)
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAW = sys.argv[1] if len(sys.argv) > 1 else "."
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.join(OUT, "planets"), exist_ok=True)

WORLD_W, WORLD_H = 2400, 1200          # matches GRID 100x50 @ 24 px cells


def key_out_green(rgb):
    """Soft chroma-key of the #00FF00 background + green-spill suppression.

    Works on alpha, not on connectivity, so enclosed key regions (Saturn's ring
    gaps) are cleared too - the classic 'pink donut hole' failure.
    """
    a = rgb.astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    greenness = g - np.maximum(r, b)
    alpha = np.clip((60.0 - greenness) / 50.0, 0.0, 1.0)
    lit = alpha > 0.0
    g_capped = np.minimum(g, np.maximum(r, b) + 6.0)
    g = np.where(lit, g_capped, g)
    out = np.stack([r, g, b, alpha * 255.0], axis=-1)
    return np.clip(out, 0, 255).astype(np.uint8)


def content_bbox(alpha, thr=90):
    ys, xs = np.where(alpha > thr)
    if len(xs) == 0:
        return None
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


# ---------------------------------------------------------------- background
def build_milkyway():
    im = Image.open(os.path.join(RAW, "milkyway.png")).convert("RGB")
    a = np.asarray(im).astype(np.float32)

    # The model painted a small starship into the background despite the
    # "no characters" suffix. Patch it out with a feathered copy of clean
    # nebula from the right so the revealed territory has no second ship.
    x0, y0, x1, y1 = 1585, 725, 1885, 1125
    w, h = x1 - x0, y1 - y0
    src = a[y0:y1, x0 + 340:x0 + 340 + w].copy()
    src *= (a[y0:y1, x0:x1].mean() / max(src.mean(), 1e-3))  # match local exposure
    fy = np.minimum(np.arange(h), h - 1 - np.arange(h))[:, None]
    fx = np.minimum(np.arange(w), w - 1 - np.arange(w))[None, :]
    mask = np.clip(np.minimum(fy, fx) / 70.0, 0.0, 1.0)[..., None] ** 0.75
    a[y0:y1, x0:x1] = a[y0:y1, x0:x1] * (1.0 - mask) + src * mask

    im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    # crop to the world's 2:1 aspect around the galactic band, then fit exactly
    top = (im.height - im.width // 2) // 2
    im = im.crop((0, top, im.width, top + im.width // 2))
    im = im.resize((WORLD_W, WORLD_H), Image.LANCZOS)
    im.save(os.path.join(OUT, "milkyway.jpg"), quality=86, optimize=True)
    print("milkyway.jpg", im.size)
    return im


# ------------------------------------------------------------------ planets
# row-major over the 4x4 sheet; None = near-duplicate of an earlier body, dropped
SHEET = [
    "sun", "mercury", "venus", "earth",
    "moon", "mars", "jupiter", "saturn",
    "uranus", "neptune", "lava", "aurora",
    None, "ocean", "dune", None,
]
SPRITE = 200


def build_planets():
    im = Image.open(os.path.join(RAW, "planets.png")).convert("RGB")
    rgba = key_out_green(np.asarray(im))
    alpha = rgba[..., 3]
    cell = rgba.shape[0] // 4
    made = []
    for idx, name in enumerate(SHEET):
        if name is None:
            continue
        cy, cx = divmod(idx, 4)
        sub = alpha[cy * cell:(cy + 1) * cell, cx * cell:(cx + 1) * cell]
        bb = content_bbox(sub)
        if bb is None:
            print("  !! empty cell", idx)
            continue
        bx0, by0, bx1, by1 = bb
        # square crop centred on the body, taken from the full sheet so a wide
        # ring set is never clipped by the cell edge
        gx0, gy0 = cx * cell + bx0, cy * cell + by0
        gx1, gy1 = cx * cell + bx1, cy * cell + by1
        ccx, ccy = (gx0 + gx1) / 2.0, (gy0 + gy1) / 2.0
        side = max(gx1 - gx0, gy1 - gy0) * 1.06
        half = side / 2.0
        crop = Image.fromarray(rgba, "RGBA").crop(
            (int(round(ccx - half)), int(round(ccy - half)),
             int(round(ccx + half)), int(round(ccy + half))))
        crop = crop.resize((SPRITE, SPRITE), Image.LANCZOS)
        crop.save(os.path.join(OUT, "planets", name + ".png"), optimize=True)
        made.append(name)
    print("planets:", ", ".join(made))
    return made


# --------------------------------------------------------------------- ship
def build_ship():
    im = Image.open(os.path.join(RAW, "ship2.png")).convert("RGB")
    rgba = key_out_green(np.asarray(im))
    bb = content_bbox(rgba[..., 3])
    x0, y0, x1, y1 = bb
    ccx, ccy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    half = max(x1 - x0, y1 - y0) / 2.0 * 1.06
    ship = Image.fromarray(rgba, "RGBA").crop(
        (int(ccx - half), int(ccy - half), int(ccx + half), int(ccy + half)))
    ship = ship.resize((256, 256), Image.LANCZOS)
    ship.save(os.path.join(OUT, "ship.png"), optimize=True)
    print("ship.png", ship.size)
    return ship


# ------------------------------------------------------------ icon + cover
def build_icon():
    """First ship pass came back as a circular cosmic medallion - exactly the
    shape an icon wants, so it is reused instead of burning another generation."""
    im = Image.open(os.path.join(RAW, "ship.png")).convert("RGB")
    rgba = key_out_green(np.asarray(im))
    bb = content_bbox(rgba[..., 3])
    x0, y0, x1, y1 = bb
    icon = Image.fromarray(rgba, "RGBA").crop((x0, y0, x1, y1)).resize((512, 512), Image.LANCZOS)
    # hard circular alpha so the key fringe never shows as a green rim
    m = Image.new("L", (2048, 2048), 0)
    ImageDraw.Draw(m).ellipse((6, 6, 2042, 2042), fill=255)
    m = m.resize((512, 512), Image.LANCZOS)
    icon.putalpha(Image.fromarray(
        np.minimum(np.asarray(icon.split()[3]), np.asarray(m))))
    icon.save(os.path.join(OUT, "icon.png"), optimize=True)
    print("icon.png", icon.size)
    return icon


def glow_text(base, xy, text, font, fill, glow, radius=14, passes=3):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text(xy, text, font=font, fill=glow, anchor="mm")
    for _ in range(passes):
        base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))
    d2 = ImageDraw.Draw(base)
    d2.text(xy, text, font=font, fill=fill, anchor="mm")


def build_cover(milkyway, ship):
    W, H = 1280, 720
    cover = milkyway.resize((int(W * 1.25), int(H * 1.25)), Image.LANCZOS)
    cover = cover.crop((120, 60, 120 + W, 60 + H)).convert("RGBA")

    # darken the lower half so the title reads at catalog thumbnail size
    shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    for i in range(H):
        sd.line([(0, i), (W, i)], fill=(4, 6, 18, int(150 * (i / H) ** 1.6)))
    cover.alpha_composite(shade)

    for name, size, pos in (("jupiter", 300, (150, 190)),
                            ("saturn", 210, (1080, 200)),
                            ("earth", 130, (980, 520))):
        p = Image.open(os.path.join(OUT, "planets", name + ".png")).resize(
            (size, size), Image.LANCZOS)
        cover.alpha_composite(p, (pos[0] - size // 2, pos[1] - size // 2))

    s = ship.resize((330, 330), Image.LANCZOS)
    cover.alpha_composite(s, (W // 2 - 165, 150))

    fbold = "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
    glow_text(cover, (W // 2, 560), "COSMIC AIRXONIX",
              ImageFont.truetype(fbold, 92), (255, 255, 255, 255), (90, 220, 255, 170))
    glow_text(cover, (W // 2, 640), "CLAIM THE GALAXY",
              ImageFont.truetype(fbold, 38), (150, 235, 255, 255), (60, 180, 255, 120), radius=8, passes=2)
    cover.convert("RGB").save(os.path.join(OUT, "cover.jpg"), quality=90, optimize=True)
    print("cover.jpg", (W, H))


if __name__ == "__main__":
    mw = build_milkyway()
    build_planets()
    sh = build_ship()
    build_icon()
    build_cover(mw, sh)
    total = 0
    for root, _, files in os.walk(OUT):
        for f in files:
            total += os.path.getsize(os.path.join(root, f))
    print("assets total: %.2f MB" % (total / 1e6))
