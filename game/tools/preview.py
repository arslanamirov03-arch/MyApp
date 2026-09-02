#!/usr/bin/env python3
"""Parses the pixel-string sprites out of Art.java, validates that every row of
every sprite has the same width, and renders a contact sheet PNG so the artwork
can be eyeballed without installing the APK."""
import os
import re
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "bromobile", "game", "Art.java")


def load_palette(src):
    pk = re.search(r'String PK\s*=\s*"([^"]*)"', src, re.S).group(1)
    block = re.search(r'int\[\] PC\s*=\s*\{(.*?)\n\s*\};', src, re.S).group(1)
    cols = [int(v, 16) for v in re.findall(r'0x([0-9A-Fa-f]{8})', block)]
    pal = {}
    for i, ch in enumerate(pk):
        if ch != ' ' and i < len(cols):
            argb = cols[i]
            a = (argb >> 24) & 255
            pal[ch] = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, a)
    pal['.'] = (0, 0, 0, 0)
    return pal


def load_sprites(src):
    out = []
    for m in re.finditer(r'String\[\]\s+(\w+)\s*=\s*\{(.*?)\n\s*\};', src, re.S):
        name, body = m.group(1), m.group(2)
        rows = re.findall(r'"([^"]*)"', body)
        if rows:
            out.append((name, rows))
    return out


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
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", comp) + chunk(b"IEND", b""))


def main():
    src = open(ART, encoding="utf-8").read()
    pal = load_palette(src)
    sprites = load_sprites(src)

    bad = 0
    for name, rows in sprites:
        widths = {len(r) for r in rows}
        if len(widths) != 1:
            bad += 1
            print("RAGGED %-16s widths=%s rows=%d" % (name, sorted(widths), len(rows)))
            for i, r in enumerate(rows):
                if len(r) != max(widths):
                    print("    row %2d len %2d  %s" % (i, len(r), r))
        unknown = {c for r in rows for c in r if c not in pal}
        if unknown:
            bad += 1
            print("BAD CHARS %-16s %s" % (name, sorted(unknown)))

    if bad:
        print("\n%d sprite(s) need fixing." % bad)
        return 1

    # Contact sheet: 5 per row, scaled 4x, padded.
    SC, PAD, COLS = 4, 6, 5
    cells = []
    for name, rows in sprites:
        cells.append((name, rows, len(rows[0]) * SC, len(rows) * SC))
    rowsets = [cells[i:i + COLS] for i in range(0, len(cells), COLS)]
    cw = max(c[2] for c in cells) + PAD * 2
    rh = [max(c[3] for c in rs) + PAD * 2 + 10 for rs in rowsets]
    W = cw * COLS
    H = sum(rh)
    px = [[(24, 22, 30, 255)] * W for _ in range(H)]

    yoff = 0
    for ri, rs in enumerate(rowsets):
        for ci, (name, rows, w, h) in enumerate(rs):
            ox = ci * cw + PAD
            oy = yoff + PAD + 10
            for y, row in enumerate(rows):
                for x, ch in enumerate(row):
                    col = pal.get(ch, (255, 0, 255, 255))
                    if col[3] == 0:
                        col = (44, 40, 52, 255) if (x // 2 + y // 2) % 2 else (34, 31, 42, 255)
                    for sy in range(SC):
                        for sx in range(SC):
                            yy, xx = oy + y * SC + sy, ox + x * SC + sx
                            if 0 <= yy < H and 0 <= xx < W:
                                px[yy][xx] = col
        yoff += rh[ri]

    out = os.path.join(HERE, "sprites.png")
    write_png(out, px, W, H)
    print("OK: %d sprites, all rows consistent -> %s (%dx%d)" % (len(sprites), out, W, H))
    return 0


if __name__ == "__main__":
    sys.exit(main())
