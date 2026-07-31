#!/usr/bin/env python3
"""Иконка приложения: золотая звезда в лучах на бордовом поле.
Растровые файлы нужны для Android 7-7.1, а начиная с Android 8
используется адаптивная иконка из XML."""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(ROOT, 'android/app/src/main/res')

MAROON_DEEP = (62, 10, 13)
MAROON = (110, 18, 22)
GOLD = (232, 206, 122)
GOLD_DARK = (140, 109, 31)

SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def star_points(cx, cy, outer, inner, count=5):
    points = []
    for i in range(count * 2):
        radius = outer if i % 2 == 0 else inner
        angle = -math.pi / 2 + i * math.pi / count
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


def draw_icon(size, supersample=8):
    s = size * supersample
    image = Image.new('RGBA', (s, s), MAROON_DEEP + (255,))
    draw = ImageDraw.Draw(image)

    # Лучи, расходящиеся от центра
    center = s / 2
    for i in range(24):
        angle = i * (2 * math.pi / 24)
        spread = 2 * math.pi / 48
        p1 = (center + s * math.cos(angle - spread / 2), center + s * math.sin(angle - spread / 2))
        p2 = (center + s * math.cos(angle + spread / 2), center + s * math.sin(angle + spread / 2))
        draw.polygon([(center, center), p1, p2], fill=MAROON + (255,))

    # Круглый кант
    inset = s * 0.07
    draw.ellipse([inset, inset, s - inset, s - inset], outline=GOLD_DARK + (255,), width=int(s * 0.022))

    # Звезда
    points = star_points(center, center, s * 0.34, s * 0.135)
    draw.polygon(points, fill=GOLD + (255,), outline=GOLD_DARK + (255,))

    return image.resize((size, size), Image.LANCZOS)


def main():
    for suffix, size in SIZES.items():
        folder = os.path.join(RES, 'mipmap-' + suffix)
        os.makedirs(folder, exist_ok=True)
        icon = draw_icon(size)
        icon.save(os.path.join(folder, 'ic_launcher.png'))
        icon.save(os.path.join(folder, 'ic_launcher_round.png'))
        print('mipmap-%s/ic_launcher.png (%dx%d)' % (suffix, size, size))


if __name__ == '__main__':
    main()
