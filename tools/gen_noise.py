#!/usr/bin/env python3
"""Generates the tiling fBm noise texture shipped as an app asset.

  R : 4-octave value fBm  (base 4)  -> curl-noise potential for the turbulence
  G : 5-octave value fBm  (base 6)  -> flame filament detail
  B : 4-octave value fBm  (base 8)  -> nozzle flicker / fine detail
  A : white noise                   -> film grain

Quintic interpolation keeps the first derivative continuous, which matters
because the shader differentiates R to build a divergence-free force field.
"""
import os

import numpy as np
from PIL import Image

SIZE = 256


def fade(t):
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)


def value_noise(size, freq, rng):
    """Periodic value noise: a freq x freq lattice smoothly interpolated."""
    lattice = rng.random((freq, freq)).astype(np.float64)

    coord = np.arange(size, dtype=np.float64) * freq / size
    i0 = np.floor(coord).astype(np.int64) % freq
    i1 = (i0 + 1) % freq
    frac = fade(coord - np.floor(coord))

    # rows first, then columns
    a = lattice[i0][:, i0]      # (size, size)
    b = lattice[i1][:, i0]
    c = lattice[i0][:, i1]
    d = lattice[i1][:, i1]

    fy = frac[:, None]
    fx = frac[None, :]
    top = a + (b - a) * fy
    bot = c + (d - c) * fy
    return top + (bot - top) * fx


def fbm(size, base_freq, octaves, rng):
    total = np.zeros((size, size), dtype=np.float64)
    amp = 1.0
    norm = 0.0
    freq = base_freq
    for _ in range(octaves):
        total += value_noise(size, freq, rng) * amp
        norm += amp
        amp *= 0.5
        freq *= 2
    out = total / norm
    out -= out.min()
    out /= max(out.max(), 1e-9)
    return out


def main():
    rng = np.random.default_rng(20260903)
    r = fbm(SIZE, 4, 4, rng)
    g = fbm(SIZE, 6, 5, rng)
    b = fbm(SIZE, 8, 4, rng)
    a = rng.random((SIZE, SIZE))

    rgba = np.stack([r, g, b, a], axis=-1)
    img = Image.fromarray(np.clip(rgba * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGBA")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                       "app/src/main/assets/noise.png")
    out = os.path.normpath(out)
    img.save(out)
    print("wrote", out, img.size)


if __name__ == "__main__":
    main()
