#!/usr/bin/env python3
"""Draw the app icon: a spider silhouette lit from behind.

    python3 game/tools/gen_icon.py
"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "ui")
)
SIZE = 1024


def draw_spider(size: int, with_background: bool = True) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2.0
    s = size / 1024.0

    if with_background:
        # warm glow behind the spider, fading to near-black at the edges
        glow = Image.new("RGBA", (size, size), (6, 5, 8, 255))
        gd = ImageDraw.Draw(glow)
        for i in range(70, 0, -1):
            r = size * 0.52 * (i / 70.0)
            a = int(120 * (1.0 - i / 70.0) ** 1.7)
            gd.ellipse([c - r, c - r * 0.92, c + r, c + r * 0.92],
                       fill=(150 + a // 3, 60 + a // 6, 20, 255) if a > 0 else None)
        glow = glow.filter(ImageFilter.GaussianBlur(size * 0.06))
        img.alpha_composite(glow)

    body = (7, 6, 8, 255)

    # legs: two joints each, arching above the body then down to the floor
    for side in (-1, 1):
        for i, (spread, lift, reach) in enumerate([
            (0.30, 0.46, 0.94), (0.16, 0.40, 0.80),
            (-0.02, 0.38, 0.78), (-0.20, 0.42, 0.92),
        ]):
            hip = (c + side * 52 * s, c + spread * 150 * s)
            knee = (c + side * reach * 330 * s, c + spread * 150 * s - lift * 300 * s)
            foot = (c + side * (reach * 430 + 30) * s, c + spread * 210 * s + 250 * s)
            w = int((26 - i * 2) * s)
            d.line([hip, knee], fill=body, width=w, joint="curve")
            d.line([knee, foot], fill=body, width=max(int(w * 0.7), 2), joint="curve")
            d.ellipse([knee[0] - w * 0.6, knee[1] - w * 0.6,
                       knee[0] + w * 0.6, knee[1] + w * 0.6], fill=body)

    # abdomen and cephalothorax
    d.ellipse([c - 132 * s, c - 40 * s, c + 132 * s, c + 300 * s], fill=body)
    d.ellipse([c - 96 * s, c - 168 * s, c + 96 * s, c + 40 * s], fill=body)

    # eyes
    for ex, ey, er in [(-38, -120, 17), (38, -120, 17), (-66, -96, 11),
                       (66, -96, 11), (-20, -142, 9), (20, -142, 9)]:
        d.ellipse([c + (ex - er) * s, c + (ey - er) * s,
                   c + (ex + er) * s, c + (ey + er) * s], fill=(196, 34, 22, 255))

    # fangs
    d.polygon([(c - 46 * s, c + 20 * s), (c - 20 * s, c + 26 * s), (c - 34 * s, c + 96 * s)],
              fill=(20, 16, 16, 255))
    d.polygon([(c + 46 * s, c + 20 * s), (c + 20 * s, c + 26 * s), (c + 34 * s, c + 96 * s)],
              fill=(20, 16, 16, 255))
    return img


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    master = draw_spider(SIZE)
    for name, px in [("icon.png", 512), ("icon_192.png", 192)]:
        master.resize((px, px), Image.LANCZOS).save(os.path.join(OUT, name))
        print(f"  {name} ({px}px)")
    # adaptive icons need a transparent foreground and a flat background
    fg = draw_spider(SIZE, with_background=False)
    fg.resize((432, 432), Image.LANCZOS).save(os.path.join(OUT, "icon_fg_432.png"))
    Image.new("RGBA", (432, 432), (14, 10, 12, 255)).save(
        os.path.join(OUT, "icon_bg_432.png"))
    print("  icon_fg_432.png / icon_bg_432.png")


if __name__ == "__main__":
    main()
