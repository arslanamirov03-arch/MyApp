"""Generate launcher icons: warm cream rounded square with a coral compass starburst."""
import math
import os
from PIL import Image, ImageDraw

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

BG = (240, 238, 230, 255)      # #F0EEE6
CORAL = (217, 119, 87, 255)    # #D97757
INK = (20, 20, 19, 255)

def draw_icon(size):
    S = 8  # supersampling
    px = size * S
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = px * 0.22
    d.rounded_rectangle([0, 0, px - 1, px - 1], radius=r, fill=BG)

    cx = cy = px / 2
    # 8-ray starburst (Claude-like), long cardinal rays, short diagonal rays
    for i in range(8):
        ang = math.radians(i * 45)
        length = px * (0.335 if i % 2 == 0 else 0.22)
        halfw = px * 0.028
        tip = (cx + length * math.cos(ang), cy + length * math.sin(ang))
        left = (cx + halfw * math.cos(ang + math.pi / 2), cy + halfw * math.sin(ang + math.pi / 2))
        right = (cx + halfw * math.cos(ang - math.pi / 2), cy + halfw * math.sin(ang - math.pi / 2))
        d.polygon([left, tip, right], fill=CORAL)
    d.ellipse([cx - px * 0.055, cy - px * 0.055, cx + px * 0.055, cy + px * 0.055], fill=CORAL)

    return img.resize((size, size), Image.LANCZOS)

base = os.path.dirname(os.path.abspath(__file__))
for folder, size in SIZES.items():
    out_dir = os.path.join(base, "res", folder)
    os.makedirs(out_dir, exist_ok=True)
    draw_icon(size).save(os.path.join(out_dir, "ic_launcher.png"))
    print(folder, "done")
