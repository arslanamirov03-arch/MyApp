#!/usr/bin/env python3
"""Synthesises the app's sound effects into 16-bit mono WAV assets.

  fire_loop.wav   seamless flame bed: low roar + hiss + random crackles
  rumble_loop.wav seamless sub-bass rumble, faded up as the fire grows
  match.wav       one-shot strike + ignition puff
  boom.wav        one-shot detonation: crack, punch, sub sweep, rumble tail
  thunder_0..2    near lightning reports: crack, tearing channel, rolling tail
  thunder_far     distant roll, used while the storm dies down
  crack.wav       short report for when strikes come several times a second
  storm_loop.wav  seamless storm bed

The noise beds are built in the frequency domain with random phase, which makes
them exactly periodic and therefore gapless when looped.
"""
import os
import struct
import wave

import numpy as np

SR = 44100
OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                    "..", "app/src/main/assets/audio"))


def write_wav(name, data, sr=SR):
    data = np.clip(data, -1.0, 1.0)
    pcm = (data * 32767.0).astype("<i2")
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())
    print("wrote %-16s %6.2fs %7d bytes" % (name, len(data) / sr, os.path.getsize(path)))


def periodic_noise(n, rng, lo, hi, slope=0.0):
    """Band-limited noise that is exactly periodic over n samples."""
    spec = np.zeros(n // 2 + 1, dtype=complex)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    band = (freqs >= lo) & (freqs <= hi)
    mag = np.zeros_like(freqs)
    f = np.maximum(freqs, 1.0)
    mag[band] = (f[band] / max(lo, 1.0)) ** slope
    # gentle roll-off at both edges so the band has no hard walls
    edge = np.clip((freqs - lo) / max(lo * 0.5, 1.0), 0, 1) * \
           np.clip((hi - freqs) / max(hi * 0.25, 1.0), 0, 1)
    mag *= edge
    phase = rng.random(len(freqs)) * 2 * np.pi
    spec = mag * np.exp(1j * phase)
    out = np.fft.irfft(spec, n)
    m = np.max(np.abs(out))
    return out / m if m > 0 else out


def periodic_env(n, rng, rate, depth):
    """Slow periodic amplitude wobble, so the loop breathes."""
    env = np.zeros(n)
    t = np.arange(n) / SR
    period = n / SR
    for _ in range(5):
        k = max(1, int(round(rng.uniform(1, max(2, rate * period)))))
        env += np.sin(2 * np.pi * k * t / period + rng.random() * 6.283) / k
    env /= np.max(np.abs(env)) + 1e-9
    return 1.0 + depth * env


def crackles(n, rng, per_sec, gain, lo, hi, decay):
    """Sharp resonant pops, kept clear of the loop seam."""
    out = np.zeros(n)
    count = int(per_sec * n / SR)
    guard = int(0.12 * SR)
    for _ in range(count):
        start = rng.integers(0, max(n - guard, 1))
        dur = int(rng.uniform(0.004, 0.030) * SR)
        t = np.arange(dur) / SR
        env = np.exp(-t / (decay * rng.uniform(0.5, 1.8)))
        freq = rng.uniform(lo, hi)
        # a struck resonance rather than plain noise: reads as wood popping
        body = np.sin(2 * np.pi * freq * t + rng.random() * 6.283)
        body += 0.6 * rng.normal(0, 1, dur)
        out[start:start + dur] += body * env * gain * rng.uniform(0.25, 1.0)
    return out


def fire_loop(seconds=6.0):
    rng = np.random.default_rng(101)
    n = int(seconds * SR)
    roar = periodic_noise(n, rng, 30, 420, -0.9) * periodic_env(n, rng, 3, 0.45)
    body = periodic_noise(n, rng, 300, 1800, -0.5) * periodic_env(n, rng, 6, 0.55)
    hiss = periodic_noise(n, rng, 1800, 9000, -0.7) * periodic_env(n, rng, 9, 0.65)
    pops = crackles(n, rng, 18.0, 0.75, 700, 5600, 0.010)
    mix = 0.40 * roar + 0.60 * body + 0.34 * hiss + pops
    mix /= np.max(np.abs(mix)) + 1e-9
    return np.tanh(mix * 1.4) * 0.82


def rumble_loop(seconds=5.0):
    rng = np.random.default_rng(202)
    n = int(seconds * SR)
    sub = periodic_noise(n, rng, 22, 95, -1.2) * periodic_env(n, rng, 2, 0.55)
    low = periodic_noise(n, rng, 60, 260, -1.0) * periodic_env(n, rng, 4, 0.4)
    mix = sub + 0.35 * low
    mix /= np.max(np.abs(mix)) + 1e-9
    return mix * 0.9


def match(seconds=1.1):
    rng = np.random.default_rng(303)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)

    # 1. the scrape: rough noise, ~70 ms, rising
    sc = int(0.075 * SR)
    ts = np.arange(sc) / SR
    scrape = rng.normal(0, 1, sc)
    scrape = np.convolve(scrape, np.ones(6) / 6, mode="same")   # dull it slightly
    scrape *= (ts / ts[-1]) ** 1.4 * np.exp(-ts / 0.05)
    # grainy amplitude, like grit catching
    scrape *= 0.6 + 0.4 * (rng.random(sc) > 0.35)
    out[:sc] += scrape * 0.85

    # 2. ignition puff: a burst that blooms then settles
    ig = int(0.55 * SR)
    ti = np.arange(ig) / SR
    puff = rng.normal(0, 1, ig)
    k = np.exp(-np.arange(48) / 12.0)
    puff = np.convolve(puff, k / k.sum(), mode="same")
    puff *= np.exp(-ti / 0.16) * (1 - np.exp(-ti / 0.008))
    start = int(0.055 * SR)
    out[start:start + ig] += puff * 1.1

    # 3. a couple of small crackles as it settles
    out += crackles(n, rng, 6.0, 0.30, 900, 4500, 0.012)

    out /= np.max(np.abs(out)) + 1e-9
    return np.tanh(out * 1.5) * 0.9


def boom(seconds=4.0):
    rng = np.random.default_rng(404)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)

    # 1. crack: very short high transient
    cr = int(0.020 * SR)
    tc = np.arange(cr) / SR
    out[:cr] += rng.normal(0, 1, cr) * np.exp(-tc / 0.0035) * 0.95

    # 2. punch: dull, loud, fast
    pu = int(0.35 * SR)
    tp = np.arange(pu) / SR
    punch = rng.normal(0, 1, pu)
    k = np.exp(-np.arange(64) / 18.0)
    punch = np.convolve(punch, k / k.sum(), mode="same")
    out[:pu] += punch * np.exp(-tp / 0.055) * 1.6

    # 3. sub sweep: two detuned sines dropping in pitch
    for f0, f1, amp, tau in ((95.0, 26.0, 1.0, 0.75), (48.0, 17.0, 0.7, 1.15)):
        f = f1 + (f0 - f1) * np.exp(-t / (tau * 0.45))
        phase = 2 * np.pi * np.cumsum(f) / SR
        out += np.sin(phase) * np.exp(-t / tau) * amp

    # 4. rumble tail: noise through a slowly closing low-pass
    tail = rng.normal(0, 1, n)
    acc = np.zeros(n)
    y = 0.0
    cutoff = 900.0 * np.exp(-t / 1.3) + 45.0
    a = 1.0 - np.exp(-2 * np.pi * cutoff / SR)
    for i in range(n):
        y += a[i] * (tail[i] - y)
        acc[i] = y
    acc /= np.max(np.abs(acc)) + 1e-9
    out += acc * np.exp(-t / 1.25) * 1.5

    # 5. debris
    out += crackles(n, rng, 9.0, 0.22, 400, 3500, 0.020)

    out /= np.max(np.abs(out)) + 1e-9
    return np.tanh(out * 1.9) * 0.97


def onepole_sweep(x, cutoff):
    """One-pole low-pass whose cutoff moves sample by sample."""
    a = 1.0 - np.exp(-2 * np.pi * np.maximum(cutoff, 5.0) / SR)
    out = np.empty_like(x)
    y = 0.0
    for i in range(len(x)):
        y += a[i] * (x[i] - y)
        out[i] = y
    return out


def band(x, lo, hi):
    """Zero-phase band-pass via the spectrum; fine for one-shot design."""
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    resp = np.clip((f - lo) / max(lo, 1.0), 0, 1) * np.clip((hi - f) / max(hi * 0.4, 1.0), 0, 1)
    return np.fft.irfft(spec * resp, n)


def thunder(seconds, rng, near=True, seed_shift=0.0):
    """A lightning report: leading crack, the tearing channel, then the roll.

    Distance is what separates a near strike from a far one -- air soaks up the
    high frequencies, so a far strike keeps only the low roll and loses the crack.
    """
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)

    if near:
        # 1. the crack: a few milliseconds of full-band energy
        cr = int(0.012 * SR)
        tc = np.arange(cr) / SR
        out[:cr] += band(rng.normal(0, 1, cr), 800, 12000) * np.exp(-tc / 0.0022) * 3.0

        # 2. the tear: the channel itself, chopped into fast irregular grains
        rip = int(rng.uniform(0.22, 0.45) * SR)
        tr = np.arange(rip) / SR
        grain = rng.normal(0, 1, rip)
        chop = np.ones(rip)
        i = 0
        while i < rip:
            seg = int(rng.uniform(0.004, 0.022) * SR)
            chop[i:i + seg] *= rng.uniform(0.15, 1.0)
            i += seg
        tear = band(grain * chop, 300, 6500) * np.exp(-tr / 0.14)
        out[:rip] += tear * 2.0

    # 3. the roll: noise through a cutoff that closes as the sound rolls away
    tail = rng.normal(0, 1, n)
    cutoff = (2200.0 if near else 420.0) * np.exp(-t / (0.9 if near else 1.6)) \
             + (70.0 if near else 38.0)
    roll = onepole_sweep(tail, cutoff)
    roll /= np.max(np.abs(roll)) + 1e-9
    # the roll is not smooth: it swells and dips as reflections arrive
    swell = np.ones(n)
    for _ in range(7):
        c = rng.uniform(0.1, 0.85) * seconds
        w = rng.uniform(0.10, 0.45)
        swell += rng.uniform(0.3, 1.1) * np.exp(-((t - c) / w) ** 2)
    decay = np.exp(-t / (1.15 if near else 1.9))
    out += roll * swell * decay * (1.7 if near else 2.6)

    # 4. sub thump, only if the strike is close
    if near:
        f = 38.0 + 55.0 * np.exp(-t / 0.30)
        out += np.sin(2 * np.pi * np.cumsum(f) / SR + seed_shift) * np.exp(-t / 0.55) * 1.1

    out /= np.max(np.abs(out)) + 1e-9
    return np.tanh(out * (1.7 if near else 1.3)) * (0.97 if near else 0.72)


def crack_short(seconds=0.7):
    """Used once the storm is striking many times a second."""
    rng = np.random.default_rng(808)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    cr = int(0.010 * SR)
    tc = np.arange(cr) / SR
    out[:cr] += band(rng.normal(0, 1, cr), 900, 13000) * np.exp(-tc / 0.0020) * 3.0
    rip = int(0.13 * SR)
    tr = np.arange(rip) / SR
    out[:rip] += band(rng.normal(0, 1, rip), 400, 7000) * np.exp(-tr / 0.045) * 1.8
    out += onepole_sweep(rng.normal(0, 1, n), 900 * np.exp(-t / 0.25) + 60) * \
        np.exp(-t / 0.30) * 1.4
    out /= np.max(np.abs(out)) + 1e-9
    return np.tanh(out * 1.9) * 0.95


def storm_loop(seconds=7.0):
    """Seamless bed: the storm as a whole, under everything else."""
    rng = np.random.default_rng(909)
    n = int(seconds * SR)
    sub = periodic_noise(n, rng, 24, 110, -1.1) * periodic_env(n, rng, 2, 0.60)
    low = periodic_noise(n, rng, 90, 500, -0.9) * periodic_env(n, rng, 4, 0.65)
    air = periodic_noise(n, rng, 500, 2600, -1.0) * periodic_env(n, rng, 7, 0.55)
    mix = sub + 0.55 * low + 0.16 * air
    mix /= np.max(np.abs(mix)) + 1e-9
    return np.tanh(mix * 1.3) * 0.85


def main():
    os.makedirs(OUT, exist_ok=True)
    write_wav("fire_loop.wav", fire_loop())
    write_wav("rumble_loop.wav", rumble_loop())
    write_wav("match.wav", match())
    write_wav("boom.wav", boom())

    for i, sec in enumerate((3.6, 4.2, 3.0)):
        rng = np.random.default_rng(500 + i * 37)
        write_wav("thunder_%d.wav" % i, thunder(sec, rng, near=True, seed_shift=i * 1.3))
    write_wav("thunder_far.wav", thunder(4.5, np.random.default_rng(700), near=False))
    write_wav("crack.wav", crack_short())
    write_wav("storm_loop.wav", storm_loop())


if __name__ == "__main__":
    main()
