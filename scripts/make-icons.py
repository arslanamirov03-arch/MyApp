#!/usr/bin/env python3
"""Иконка приложения: терракотовый круг со стеклянным бликом и кремовой
монограммой. Тёплый светлый значок терялся бы среди других на экране,
а этот держит цвет приложения и хорошо виден.

Кроме обычных иконок готовится слой для адаптивной иконки Android 8+:
там система сама обрезает картинку под форму, принятую на устройстве."""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(ROOT, 'android/app/src/main/res')

BODY_TOP = (206, 110, 72)
BODY_BOTTOM = (150, 62, 35)
INK = (253, 244, 232)
INK_SHADOW = (110, 42, 20)
RIM = (255, 255, 255)

SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
GLYPH = 'L'

SERIF = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'


def vertical_gradient(size, top, bottom):
    """Мягкая заливка сверху вниз — она даёт иконке объём."""
    gradient = Image.new('RGB', (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        gradient.putpixel((0, y), tuple(
            round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)
        ))
    return gradient.resize((size, size), Image.BICUBIC)


def draw_glyph(canvas, size, color, offset=(0, 0)):
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(SERIF, int(size * 0.5))
    box = draw.textbbox((0, 0), GLYPH, font=font)
    x = (size - (box[2] - box[0])) / 2 - box[0] + offset[0]
    y = (size - (box[3] - box[1])) / 2 - box[1] + offset[1]
    draw.text((x, y), GLYPH, font=font, fill=color)


def draw_icon(size, supersample=6, circle=True):
    s = size * supersample
    image = Image.new('RGBA', (s, s), (0, 0, 0, 0))

    body = vertical_gradient(s, BODY_TOP, BODY_BOTTOM).convert('RGBA')
    if circle:
        mask = Image.new('L', (s, s), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, s - 1, s - 1], fill=255)
        image.paste(body, (0, 0), mask)
    else:
        image = body

    # Блик по верхней кромке — «толщина» стекла, как в самом приложении
    sheen = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(sheen).ellipse(
        [s * 0.1, -s * 0.16, s * 0.9, s * 0.42], fill=RIM + (85,)
    )
    sheen = sheen.filter(ImageFilter.GaussianBlur(s * 0.05))
    if circle:
        clip = Image.new('L', (s, s), 0)
        ImageDraw.Draw(clip).ellipse([0, 0, s - 1, s - 1], fill=255)
        sheen.putalpha(Image.composite(sheen.getchannel('A'), Image.new('L', (s, s), 0), clip))
    image.alpha_composite(sheen)

    # Тонкая светлая кромка
    if circle:
        ImageDraw.Draw(image).ellipse(
            [s * 0.01, s * 0.01, s * 0.99, s * 0.99],
            outline=RIM + (150,), width=max(1, int(s * 0.014))
        )

    # Монограмма с лёгкой тенью — она читается даже на мелком значке
    shadow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw_glyph(shadow, s, INK_SHADOW + (90,), offset=(0, s * 0.012))
    image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(s * 0.012)))
    draw_glyph(image, s, INK + (255,))

    return image.resize((size, size), Image.LANCZOS)


def draw_adaptive_foreground(size, supersample=4):
    """Слой для адаптивной иконки: знакзанимает только безопасную середину,
    остальное система обрежет по своей форме."""
    s = size * supersample
    layer = Image.new('RGBA', (s, s), (0, 0, 0, 0))

    shadow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw_glyph(shadow, s, INK_SHADOW + (70,), offset=(0, s * 0.008))
    layer.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(s * 0.01)))

    glyph = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw_glyph(glyph, s, INK + (255,))
    # Знак занимает ~60% кадра: адаптивная иконка обрезает края
    glyph = glyph.resize((int(s * 0.62), int(s * 0.62)), Image.LANCZOS)
    layer.alpha_composite(glyph, (int(s * 0.19), int(s * 0.19)))

    return layer.resize((size, size), Image.LANCZOS)


def main():
    for suffix, size in SIZES.items():
        folder = os.path.join(RES, 'mipmap-' + suffix)
        os.makedirs(folder, exist_ok=True)

        icon = draw_icon(size)
        icon.save(os.path.join(folder, 'ic_launcher.png'))
        icon.save(os.path.join(folder, 'ic_launcher_round.png'))

        # Слой адаптивной иконки крупнее: система обрезает его по маске
        foreground = draw_adaptive_foreground(int(size * 108 / 48))
        foreground.save(os.path.join(folder, 'ic_launcher_foreground.png'))

        print('mipmap-%s: иконка %dx%d, слой %dx%d'
              % (suffix, size, size, foreground.width, foreground.height))


if __name__ == '__main__':
    main()
