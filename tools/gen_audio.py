#!/usr/bin/env python3
"""Synthesises the app's sound effects into 16-bit mono WAV assets.

  fire_loop.wav   seamless flame bed: low roar + hiss + random crackles
  rumble_loop.wav seamless sub-bass rumble, faded up as the fire grows
  match.wav       one-shot strike + ignition puff
  boom.wav        one-shot detonation: crack, punch, sub sweep, rumble tail
  thunder_0..2    near strike with one clear echo -- what a single tap plays
  thunder_roll_*  near strike with a long rolling tail -- used during the storm
  thunder_far     distant roll, used while the storm dies down
  crack.wav       short report for when strikes come several times a second
  storm_loop.wav  seamless storm bed
  bomb_*          four conventional charges, differing in crack/punch/roar
  alarm_loop/beep warning siren and the pip that speeds up while arming
  nuke.wav        the big one; nuke_tail_loop keeps roaring afterwards

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


def make_loopable(x, fade_s=0.012):
    """Crossfades the head over the tail so any signal can loop without a click."""
    m = int(fade_s * SR)
    if m * 2 >= len(x):
        return x
    head = x[:m].copy()
    tail = x[-m:].copy()
    w = np.linspace(0.0, 1.0, m)
    out = x[:-m].copy()
    out[:m] = tail * (1.0 - w) + head * w
    return out


def shape_for_speaker(x, tilt=None, hp=38.0):
    """Tilts the spectrum towards what a phone can actually reproduce.

    A physically faithful thunder puts most of its energy below 120 Hz, which a
    phone speaker simply cannot move air at -- the result is a loud file that
    sounds like nothing. This keeps the low end for headphones and anything with
    a real driver, but lifts the band the crack lives in so the sound survives on
    the speaker most people will use.
    """
    if tilt is None:
        tilt = [(0, 0.0), (35, 0.25), (80, 0.75), (160, 1.0),
                (400, 1.55), (1200, 2.10), (4000, 1.85), (9000, 1.10), (20000, 0.7)]
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    fs = np.array([p[0] for p in tilt])
    gs = np.array([p[1] for p in tilt])
    gain = np.interp(f, fs, gs)
    gain *= np.clip((f - hp * 0.4) / hp, 0.0, 1.0)
    out = np.fft.irfft(spec * gain, n)
    m = np.max(np.abs(out))
    return out / m if m > 0 else out


def nwave(dur_s, n):
    """Classic sonic-boom N-wave: pressure jumps up, ramps down, jumps back."""
    m = max(int(dur_s * SR), 3)
    m = min(m, n)
    return np.linspace(1.0, -1.0, m)


def air_bands(arrivals, amps, taus, n, rng, turbulence=0.35):
    """Sums channel contributions grouped into distance bands.

    Thunder is not one bang: every part of the channel radiates at once, but the
    sound from each part reaches you at a different time and, having travelled
    further, with more of its top end absorbed by the air. Grouping the segments
    into a handful of bands lets each band be filtered once instead of per
    segment, which is what makes this cheap enough to generate at build time.
    """
    bands = 6
    out = np.zeros(n)
    order = np.argsort(taus)
    chunks = np.array_split(order, bands)
    for bi, idx in enumerate(chunks):
        if len(idx) == 0:
            continue
        buf = np.zeros(n)
        for i in idx:
            start = int(arrivals[i] * SR)
            if start >= n:
                continue
            w = nwave(taus[i], n - start)
            seg = w * amps[i]
            # the channel is turbulent, so each contribution is roughened
            seg = seg + rng.normal(0, turbulence, len(seg)) * amps[i] * 0.8
            buf[start:start + len(seg)] += seg
        # air absorption for this band's mean distance
        tau = float(np.mean(taus[idx]))
        cutoff = np.full(n, 20.0 + 1.6 / max(tau, 1e-4))
        out += onepole_sweep(buf, np.clip(cutoff, 60.0, 14000.0))
    return out


def reflections(sig, rng, taps, spread, gain, damp):
    """Adds delayed, progressively duller copies: the rolling part of thunder."""
    n = len(sig)
    out = sig.copy()
    for i in range(taps):
        delay = int(rng.uniform(0.12, spread) * SR)
        if delay >= n:
            continue
        g = gain * (0.55 ** (i * 0.7)) * rng.uniform(0.55, 1.25)
        tail = onepole_sweep(sig[:n - delay], np.full(n - delay, damp / (1 + i * 0.8)))
        out[delay:] += tail * g
    return out


def thunder(seconds, rng, near=True, echo="single"):
    """A lightning report.

    near=True puts the closest part of the channel a few hundred metres away, so
    the leading edge is a crack; far away, the air has already eaten everything
    above a few hundred hertz and only the roll survives.

    echo="single" leaves one clear slap-back, the way a single strike sounds
    across open ground. echo="roll" layers many reflections into the continuous
    rumble you get in a running storm.
    """
    n = int(seconds * SR)
    segs = 90

    d0 = rng.uniform(0.28, 0.55) if near else rng.uniform(3.5, 6.0)
    length = rng.uniform(0.8, 1.6)                       # km of channel
    # a tortuous channel: distance grows along it, but not smoothly
    steps = np.abs(rng.normal(1.0, 0.7, segs))
    dist = d0 + np.cumsum(steps) / steps.sum() * length

    arrivals = (dist - d0) / 0.343                        # km / (km per second)
    # spherical spreading, plus an uneven energy release along the channel
    amps = (d0 / dist) ** 1.35
    amps *= 0.35 + 1.65 * np.abs(rng.normal(0, 1, segs)) ** 0.7
    amps /= amps.max()
    # shock rise time stretches with distance, which is what dulls the far parts
    taus = 0.00035 + 0.0042 * (dist / 1.5) ** 1.2

    out = air_bands(arrivals, amps, taus, n, rng)

    if near:
        out /= np.max(np.abs(out)) + 1e-9
        # the very first metres arrive as an almost instantaneous full-band crack
        cr = int(0.014 * SR)
        tc = np.arange(cr) / SR
        out[:cr] += band(rng.normal(0, 1, cr), 600, 14000) * np.exp(-tc / 0.0024) * 3.4
        # then the channel tearing: mid-band, chopped, a couple of tenths long
        rip = int(rng.uniform(0.18, 0.34) * SR)
        tr = np.arange(rip) / SR
        chop = np.ones(rip)
        i = 0
        while i < rip:
            seg = int(rng.uniform(0.004, 0.020) * SR)
            chop[i:i + seg] *= rng.uniform(0.2, 1.0)
            i += seg
        out[:rip] += band(rng.normal(0, 1, rip) * chop, 350, 7500) * np.exp(-tr / 0.085) * 1.9
        f = 34.0 + 52.0 * np.exp(-np.arange(n) / SR / 0.28)
        out += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-np.arange(n) / SR / 0.5) * 0.6

    if echo == "single":
        # one distinct return off distant ground, then quiet
        out = reflections(out, rng, taps=2, spread=0.95, gain=0.30, damp=900.0)
    else:
        out = reflections(out, rng, taps=11, spread=2.4, gain=0.42, damp=1400.0)

    t = np.arange(n) / SR
    out *= np.exp(-t / (seconds * 0.55))
    out = shape_for_speaker(out)
    return np.tanh(out * (1.9 if near else 1.5)) * (0.97 if near else 0.76)


def crack_short(seconds=0.8):
    """Used once the storm is striking many times a second."""
    rng = np.random.default_rng(808)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    cr = int(0.009 * SR)
    tc = np.arange(cr) / SR
    out[:cr] += band(rng.normal(0, 1, cr), 900, 14000) * np.exp(-tc / 0.0018) * 3.0
    rip = int(0.14 * SR)
    tr = np.arange(rip) / SR
    out[:rip] += band(rng.normal(0, 1, rip), 400, 7000) * np.exp(-tr / 0.040) * 1.7
    out += onepole_sweep(rng.normal(0, 1, n), 1000 * np.exp(-t / 0.22) + 55) * \
        np.exp(-t / 0.26) * 1.3
    out = reflections(out, rng, taps=3, spread=0.55, gain=0.26, damp=800.0)
    out = shape_for_speaker(out)
    return np.tanh(out * 2.0) * 0.95


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


def explosion(seconds, rng, sr=SR, crack=1.0, punch=1.0, sub=(95.0, 26.0),
              sub_amp=1.0, tail=1.0, bright=1.0, roar=0.0, grit=1.0):
    """A generic detonation, shaped by how much of each stage is present.

    Real charges differ mostly in the balance of four things: the leading crack,
    the pressure punch behind it, how deep the sub sweeps, and how long the
    reverberant roar runs on afterwards.
    """
    n = int(seconds * sr)
    t = np.arange(n) / sr
    out = np.zeros(n)

    if crack > 0:
        cr = int(0.016 * sr)
        tc = np.arange(cr) / sr
        hi = min(14000.0, sr * 0.45)
        out[:cr] += band(rng.normal(0, 1, cr), 700 / bright, hi) * \
            np.exp(-tc / 0.0026) * 3.2 * crack

    if punch > 0:
        pu = int(0.45 * sr)
        tp = np.arange(pu) / sr
        p = rng.normal(0, 1, pu)
        k = np.exp(-np.arange(72) / (16.0 / max(bright, 0.2)))
        p = np.convolve(p, k / k.sum(), mode="same")
        out[:pu] += p * np.exp(-tp / (0.070 * (1 + 0.8 * punch))) * 1.9 * punch

    f0, f1 = sub
    for mult, amp, tau in ((1.0, 1.0, 0.80), (0.52, 0.72, 1.25)):
        f = f1 * mult + (f0 * mult - f1 * mult) * np.exp(-t / (tau * 0.45))
        out += np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-t / tau) * amp * sub_amp

    if roar > 0:
        cutoff = 2600.0 * np.exp(-t / (0.9 * tail)) + 90.0
        r = onepole_sweep(rng.normal(0, 1, n), np.minimum(cutoff, sr * 0.45))
        r /= np.max(np.abs(r)) + 1e-9
        swell = np.ones(n)
        for _ in range(6):
            c = rng.uniform(0.05, 0.8) * seconds
            w = rng.uniform(0.08, 0.4) * seconds
            swell += rng.uniform(0.3, 1.0) * np.exp(-((t - c) / w) ** 2)
        out += r * swell * np.exp(-t / (1.2 * tail)) * 1.7 * roar

    debris = int(9 * grit)
    if debris > 0:
        out += crackles(n, rng, debris, 0.20 * grit, 350, 4000, 0.020)

    out = shape_for_speaker(out)
    return np.tanh(out * 2.0) * 0.97


def alarm_loop(seconds=2.4):
    """Seamless air-raid style warning: an integer number of sweeps per loop."""
    rng = np.random.default_rng(1111)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    # exactly one sweep across the loop keeps the ends continuous
    f = 470.0 + 210.0 * np.sin(2 * np.pi * t / seconds - np.pi / 2)
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.45 * np.sin(2 * phase) + 0.22 * np.sin(3 * phase)
    tone = np.tanh(tone * 1.4)
    # a slow tremolo, again an integer number of cycles
    trem = 0.72 + 0.28 * np.sin(2 * np.pi * 4.0 * t / seconds)
    sub = np.sin(2 * np.pi * np.round(52 * seconds) * t / seconds) * 0.35
    mix = tone * trem * 0.8 + sub
    mix = shape_for_speaker(mix, hp=60.0)
    return make_loopable(np.tanh(mix * 1.2) * 0.72)


def alarm_beep(seconds=0.22):
    """A single warning pip; fired faster and faster as the timer runs down."""
    rng = np.random.default_rng(1212)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    env = np.minimum(t / 0.006, 1.0) * np.exp(-t / 0.075)
    tone = np.sin(2 * np.pi * 1180 * t) + 0.5 * np.sin(2 * np.pi * 2360 * t)
    out = np.tanh(tone * 1.3) * env
    out = shape_for_speaker(out, hp=120.0)
    return out * 0.85


def nuke(seconds=9.0, sr=32000):
    """The big one: flash-crack, a slow pressure wall, then a very long roar."""
    rng = np.random.default_rng(1313)
    n = int(seconds * sr)
    t = np.arange(n) / sr
    out = np.zeros(n)

    # 1. the flash arrives before the sound does anything else
    cr = int(0.030 * sr)
    tc = np.arange(cr) / sr
    out[:cr] += band(rng.normal(0, 1, cr), 500, sr * 0.45) * np.exp(-tc / 0.0055) * 3.2

    # 2. the pressure wall: slower to build than a conventional charge
    pu = int(1.6 * sr)
    tp = np.arange(pu) / sr
    p = rng.normal(0, 1, pu)
    k = np.exp(-np.arange(110) / 26.0)
    p = np.convolve(p, k / k.sum(), mode="same")
    out[:pu] += p * (1 - np.exp(-tp / 0.035)) * np.exp(-tp / 0.42) * 2.6

    # 3. sub sweeps, deeper and longer than anything else in the app
    for f0, f1, amp, tau in ((70.0, 15.0, 1.0, 2.2), (38.0, 11.0, 0.8, 3.4),
                             (120.0, 24.0, 0.5, 1.1)):
        f = f1 + (f0 - f1) * np.exp(-t / (tau * 0.4))
        out += np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-t / tau) * amp

    # 4. the roar: a closing filter over many seconds, with swells
    cutoff = 3200.0 * np.exp(-t / 2.4) + 70.0
    r = onepole_sweep(rng.normal(0, 1, n), np.minimum(cutoff, sr * 0.45))
    r /= np.max(np.abs(r)) + 1e-9
    swell = np.ones(n)
    for _ in range(11):
        c = rng.uniform(0.03, 0.85) * seconds
        w = rng.uniform(0.10, 0.55)
        swell += rng.uniform(0.4, 1.3) * np.exp(-((t - c) / w) ** 2)
    out += r * swell * np.exp(-t / 3.2) * 2.4

    # 5. distant returns rolling back in
    out = reflections(out, rng, taps=9, spread=3.0, gain=0.34, damp=900.0)

    out *= np.minimum(1.0, np.exp(-(t - seconds * 0.72) / (seconds * 0.16)))
    out = shape_for_speaker(out)
    return np.tanh(out * 2.3) * 0.99


def nuke_tail_loop(seconds=6.0, sr=32000):
    """Seamless low roar that keeps going long after the blast."""
    global SR
    old = SR
    SR = sr
    try:
        rng = np.random.default_rng(1414)
        n = int(seconds * sr)
        sub = periodic_noise(n, rng, 20, 90, -1.2) * periodic_env(n, rng, 2, 0.6)
        low = periodic_noise(n, rng, 80, 420, -1.0) * periodic_env(n, rng, 3, 0.55)
        air = periodic_noise(n, rng, 400, 2000, -1.1) * periodic_env(n, rng, 6, 0.5)
        mix = sub + 0.5 * low + 0.13 * air
        mix /= np.max(np.abs(mix)) + 1e-9
        mix = shape_for_speaker(mix)
        return np.tanh(mix * 1.25) * 0.8
    finally:
        SR = old


def main():
    os.makedirs(OUT, exist_ok=True)
    write_wav("fire_loop.wav", fire_loop())
    write_wav("rumble_loop.wav", rumble_loop())
    write_wav("match.wav", match())
    write_wav("boom.wav", boom())

    for i, sec in enumerate((3.8, 4.4, 3.2)):
        write_wav("thunder_%d.wav" % i,
                  thunder(sec, np.random.default_rng(500 + i * 37), near=True, echo="single"))
    for i, sec in enumerate((5.2, 5.8, 4.8)):
        write_wav("thunder_roll_%d.wav" % i,
                  thunder(sec, np.random.default_rng(600 + i * 41), near=True, echo="roll"))
    write_wav("thunder_far.wav",
              thunder(5.0, np.random.default_rng(700), near=False, echo="roll"))
    write_wav("crack.wav", crack_short())
    write_wav("storm_loop.wav", storm_loop())

    # --- bomb mode --------------------------------------------------------
    write_wav("bomb_he.wav", explosion(
        2.8, np.random.default_rng(2001), crack=1.2, punch=1.0,
        sub=(110.0, 30.0), tail=0.9, bright=1.3, roar=0.8, grit=1.2))
    write_wav("bomb_deep.wav", explosion(
        4.0, np.random.default_rng(2002), crack=0.45, punch=1.4,
        sub=(72.0, 18.0), sub_amp=1.35, tail=1.5, bright=0.7, roar=1.1, grit=0.7))
    write_wav("bomb_flash.wav", explosion(
        2.0, np.random.default_rng(2003), crack=1.8, punch=0.8,
        sub=(150.0, 48.0), sub_amp=0.6, tail=0.5, bright=1.8, roar=0.4, grit=1.5))
    write_wav("bomb_thud.wav", explosion(
        3.4, np.random.default_rng(2004), crack=0.25, punch=1.2,
        sub=(88.0, 22.0), sub_amp=1.2, tail=1.2, bright=0.5, roar=0.9, grit=0.5))
    write_wav("alarm_loop.wav", alarm_loop())
    write_wav("alarm_beep.wav", alarm_beep())
    write_wav("nuke.wav", nuke(), sr=32000)
    write_wav("nuke_tail_loop.wav", nuke_tail_loop(), sr=32000)


if __name__ == "__main__":
    main()
