#!/usr/bin/env python3
"""Generates the launcher icon PNGs (pure stdlib, no PIL).

The icon is authored as a 24x24 pixel-art bro face and then nearest-neighbour
upscaled into every mipmap density bucket so it stays crisp.
"""
import os
import struct
import zlib

PAL = {
    '.': (0, 0, 0, 0),
    'K': (16, 12, 18, 255),      # outline
    'B': (34, 26, 34, 255),      # backdrop dark
    'b': (58, 40, 42, 255),      # backdrop light
    'R': (196, 46, 38, 255),     # bandana
    'r': (140, 28, 26, 255),     # bandana shade
    'S': (222, 168, 122, 255),   # skin
    's': (176, 118, 82, 255),    # skin shade
    'H': (74, 48, 30, 255),      # hair
    'E': (28, 22, 26, 255),      # eye
    'W': (250, 238, 214, 255),   # highlight
    'O': (240, 152, 40, 255),    # fire / accent
    'Y': (252, 214, 96, 255),    # fire bright
}

ART = [
    "bbbbbbbbbbbbbbbbbbbbbbbb",
    "bbbbbbbbbbbbbbbbbbbbbbbb",
    "bbbbbbbKKKKKKKKKKbbbbbbb",
    "bbbbbKKHHHHHHHHHHKKbbbbb",
    "bbbbKHHHHHHHHHHHHHHKbbbb",
    "bbbKRRRRRRRRRRRRRRRRKbbb",
    "bbKRRrRRRRRRRRRRRrRRRKbb",
    "bbKRRRRRRRRRRRRRRRRRRKbR",
    "bbKrrrrrrrrrrrrrrrrrrKRR",
    "bbKSSSSSSSSSSSSSSSSSSKrR",
    "bbKSSSSSSSSSSSSSSSSSSK.r",
    "bbKSEEWSSSSSSSSSSEEWSK..",
    "bbKSEEESSSSSSSSSSEEESK..",
    "bbKSSSSSSSSSSSSSSSSSSK..",
    "bbKSSSSSSSSsssSSSSSSSK..",
    "bbKsSSSSSSSSSSSSSSSSsK..",
    "bbKsSSSSKKKKKKKKSSSSsK..",
    "bbKssSSSSSSSSSSSSSSssK..",
    "bbbKsssSSSSSSSSSSsssKbbb",
    "bbbbKKssssssssssssKKbbbb",
    "bbbbbbKKKKKKKKKKKKbbbbbb",
    "bbbbbOObbbbbbbbbbOObbbbb",
    "bbbbOYYObbbbbbbbOYYObbbb",
    "bbbbbOObbbbbbbbbbOObbbbb",
]


def build_base():
    w = h = 24
    px = [[(0, 0, 0, 255)] * w for _ in range(h)]
    for y in range(h):
        row = ART[y]
        for x in range(w):
            c = row[x] if x < len(row) else 'b'
            if c == 'b':
                # vertical gradient backdrop
                t = y / (h - 1.0)
                col = (int(30 + 26 * (1 - t)), int(24 + 14 * (1 - t)), int(34 + 12 * (1 - t)), 255)
            else:
                col = PAL.get(c, (0, 0, 0, 255))
                if col[3] == 0:
                    t = y / (h - 1.0)
                    col = (int(30 + 26 * (1 - t)), int(24 + 14 * (1 - t)), int(34 + 12 * (1 - t)), 255)
            px[y][x] = col
    return px, w, h


def scale(px, w, h, factor):
    out = []
    for y in range(h * factor):
        row = []
        for x in range(w * factor):
            row.append(px[y // factor][x // factor])
        out.append(row)
    return out, w * factor, h * factor


def write_png(path, px, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = px[y][x]
            raw += bytes((r, g, b, a))
    comp = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", comp)
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                        "app", "src", "main", "res")
    base, w, h = build_base()
    for bucket, factor in (("mdpi", 2), ("hdpi", 3), ("xhdpi", 4),
                           ("xxhdpi", 6), ("xxxhdpi", 8)):
        d = os.path.join(root, "mipmap-" + bucket)
        os.makedirs(d, exist_ok=True)
        px, sw, sh = scale(base, w, h, factor)
        write_png(os.path.join(d, "ic_launcher.png"), px, sw, sh)
        print("wrote", bucket, sw, "x", sh)


if __name__ == "__main__":
    main()
