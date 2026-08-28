#!/usr/bin/env python3
"""Synthesise the game's sound effects as 16-bit PCM WAVs.

No sample library is used: everything here is generated from filtered noise and
resonant sweeps, which keeps the repository free of third-party audio and gives
each sound a seed we can re-roll.

    python3 game/tools/gen_audio.py
"""

import os
import struct
import wave

import numpy as np

SR = 22050
OUT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "audio")
)
rng = np.random.default_rng(20240827)


def write_wav(name: str, samples: np.ndarray) -> None:
    os.makedirs(OUT, exist_ok=True)
    peak = float(np.max(np.abs(samples))) or 1.0
    data = np.clip(samples / peak * 0.92, -1.0, 1.0)
    pcm = (data * 32767.0).astype("<i2")
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    print(f"  {name:16s} {len(pcm)/SR:5.2f}s  {os.path.getsize(path)/1024:7.1f} KB")


def one_pole_lowpass(x: np.ndarray, cutoff: float) -> np.ndarray:
    """Cheap but effective single-pole filter; cutoff in Hz."""
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    out = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = a * acc + (1.0 - a) * v
        out[i] = acc
    return out


def highpass(x: np.ndarray, cutoff: float) -> np.ndarray:
    return x - one_pole_lowpass(x, cutoff)


def resonator(x: np.ndarray, freq: float, q: float) -> np.ndarray:
    """Two-pole resonant band-pass, used for wood and chitin tones."""
    w = 2.0 * np.pi * freq / SR
    r = np.exp(-w / (2.0 * q))
    a1 = 2.0 * r * np.cos(w)
    a2 = -(r * r)
    out = np.zeros_like(x)
    y1 = y2 = 0.0
    for i, v in enumerate(x):
        y = v + a1 * y1 + a2 * y2
        out[i] = y
        y2, y1 = y1, y
    return out


def ambient(seconds: float = 14.0) -> np.ndarray:
    n = int(SR * seconds)
    t = np.arange(n) / SR

    # deep room rumble
    rumble = one_pole_lowpass(rng.normal(0.0, 1.0, n), 55.0) * 3.5
    # wind pressing on the windows, swelling slowly
    wind = one_pole_lowpass(rng.normal(0.0, 1.0, n), 420.0)
    swell = 0.45 + 0.55 * (
        0.5 + 0.5 * np.sin(2 * np.pi * 0.045 * t + 1.1)
    ) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.017 * t))
    wind *= swell * 0.5
    # a distant hum, like a fridge two rooms away
    hum = 0.02 * np.sin(2 * np.pi * 49.7 * t) * (0.8 + 0.2 * np.sin(2 * np.pi * 0.3 * t))

    sig = rumble + wind + hum

    # crossfade the tail into the head so the loop does not click
    fade = int(SR * 1.2)
    ramp = np.linspace(0.0, 1.0, fade)
    sig[:fade] = sig[:fade] * ramp + sig[-fade:] * (1.0 - ramp)
    return sig[:-fade]


def footstep(seed: int) -> np.ndarray:
    r = np.random.default_rng(seed)
    n = int(SR * 0.13)
    t = np.arange(n) / SR
    # sharp transient: a claw tapping a hard floor
    click = r.normal(0.0, 1.0, n) * np.exp(-t * 190.0)
    body = resonator(click, r.uniform(1400.0, 2600.0), 9.0) * 0.6
    body += resonator(click, r.uniform(420.0, 700.0), 6.0) * 0.35
    tail = one_pole_lowpass(r.normal(0.0, 1.0, n), 900.0) * np.exp(-t * 55.0) * 0.18
    return highpass(body + tail, 120.0)


def creak(seed: int) -> np.ndarray:
    r = np.random.default_rng(seed)
    dur = r.uniform(0.9, 1.6)
    n = int(SR * dur)
    t = np.arange(n) / SR
    # slow stick-slip: amplitude stutters as the timber gives way
    stutter = 0.5 + 0.5 * np.sin(2 * np.pi * r.uniform(11.0, 26.0) * t + r.uniform(0, 6))
    stutter *= 0.4 + 0.6 * np.sin(2 * np.pi * r.uniform(2.0, 5.0) * t) ** 2
    src = r.normal(0.0, 1.0, n) * stutter
    f0 = r.uniform(180.0, 380.0)
    tone = resonator(src, f0, 26.0) * 0.7
    tone += resonator(src, f0 * r.uniform(1.9, 2.4), 20.0) * 0.35
    env = np.minimum(t * 6.0, 1.0) * np.exp(-t * r.uniform(1.4, 2.6))
    return highpass(tone * env, 90.0)


def main() -> None:
    print(f"Writing sounds to {OUT}")
    write_wav("ambient.wav", ambient())
    for i in range(1, 5):
        write_wav(f"step{i}.wav", footstep(100 + i))
    for i in range(1, 4):
        write_wav(f"creak{i}.wav", creak(200 + i))


if __name__ == "__main__":
    main()
