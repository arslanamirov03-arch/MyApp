#!/usr/bin/env python3
"""Headless preview of the bomb mode.

Reuses the fluid pipeline from preview.py and mirrors BombState/BombRenderer, so
the charge grades and -- the part that cannot be judged from the code alone --
whether the nuke really rolls itself into a mushroom can be checked without a
phone. Keep in sync with BombState.java / BombRenderer.java.
"""
import argparse
import math
import os
import random
import sys

import moderngl

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview import Fire, mix  # noqa: E402

ARM_TIME = 18.0
NUKE_LEN = 26.0

TYPE_HE, TYPE_DEEP, TYPE_FLASH, TYPE_THUD, TYPE_PLASMA = range(5)

GRADE = [
    # r     g     b     kBase  kSpan  soot  flash rgb
    (1.00, 0.76, 0.42,  900.0, 2100.0, 0.90, (1.00, 0.90, 0.72)),
    (1.00, 0.58, 0.24,  800.0, 1700.0, 1.70, (1.00, 0.72, 0.45)),
    (0.90, 0.95, 1.00, 2600.0, 3600.0, 1.90, (0.95, 0.97, 1.00)),
    (1.00, 0.70, 0.38,  850.0, 1800.0, 1.30, (1.00, 0.85, 0.62)),
    (0.40, 0.68, 1.00, 3200.0, 4800.0, 0.40, (0.55, 0.78, 1.00)),
]

BLAST_PUSH = [16000.0, 11000.0, 18000.0, 9500.0, 17000.0]
BLAST_HEAT = [40.0, 34.0, 55.0, 30.0, 45.0]
BLAST_RAD = [0.075, 0.105, 0.060, 0.090, 0.065]
BLAST_SPEED = [1.0, 0.72, 1.5, 0.8, 1.35]


def clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


class BombState:
    def __init__(self, kind=None, rnd=None):
        self.rnd = rnd or random.Random(3)
        self.forced = kind
        self.type = kind if kind is not None else 0
        self.blast_t = -1.0
        self.arm_t = 0.0
        self.nuke_t = -1.0
        self.arming = False
        self.just_fired = False
        self.just_nuked = False
        self.reset_requested = False

    def touch_down(self):
        if self.nuke_t >= 0:
            return
        self.type = self.forced if self.forced is not None else self.rnd.randrange(5)
        self.blast_t = 0.0
        self.arm_t = 0.0
        self.arming = True
        self.just_fired = True

    def update(self, dt, touching):
        self.just_fired = False
        self.just_nuked = False
        self.reset_requested = False
        if self.nuke_t >= 0:
            self.nuke_t += dt
            if self.nuke_t >= NUKE_LEN:
                self.nuke_t = -1.0
                self.blast_t = -1.0
                self.arm_t = 0.0
                self.arming = False
                self.reset_requested = True
            return
        if self.blast_t >= 0:
            self.blast_t += dt
            if self.blast_t > 9.0:
                self.blast_t = -1.0
        if touching and self.arming:
            self.arm_t += dt
            if self.arm_t >= ARM_TIME:
                self.nuke_t = 0.0
                self.arm_t = 0.0
                self.arming = False
                self.just_nuked = True
        elif not touching:
            self.arming = False
            self.arm_t = max(0.0, self.arm_t - dt * 2.5)

    def nuking(self):
        return self.nuke_t >= 0

    def arm01(self):
        return min(self.arm_t / ARM_TIME, 1.0)

    def push(self):
        if self.blast_t < 0:
            return 0.0
        sp = BLAST_SPEED[self.type]
        if self.blast_t >= 0.30 / sp:
            return 0.0
        return BLAST_PUSH[self.type] * math.exp(-self.blast_t * sp / 0.05)

    def push_radius(self):
        return 0.03 + max(self.blast_t, 0.0) * 0.55 * BLAST_SPEED[self.type]

    def heat(self):
        if self.blast_t < 0:
            return 0.0
        sp = BLAST_SPEED[self.type]
        if self.blast_t >= 0.36 / sp:
            return 0.0
        return BLAST_HEAT[self.type] * math.exp(-self.blast_t * sp / 0.06)

    def heat_radius(self):
        return BLAST_RAD[self.type] + max(self.blast_t, 0.0) * 0.30

    def nuke_push(self):
        if self.nuke_t < 0 or self.nuke_t >= 0.40:
            return 0.0
        return 600.0 * math.exp(-self.nuke_t / 0.09)

    def nuke_push_radius(self):
        return 0.03 + self.nuke_t * 0.22

    def nuke_heat(self):
        if self.nuke_t < 0 or self.nuke_t >= 0.7:
            return 0.0
        return 70.0 * math.exp(-self.nuke_t / 0.13)

    def nuke_heat_radius(self):
        return 0.035 + min(self.nuke_t, 1.2) * 0.05

    def mushroom01(self):
        if self.nuke_t < 0:
            return 0.0
        return clamp((self.nuke_t - 0.5) / 1.2, 0.0, 1.0)

    def flash(self):
        if self.nuke_t >= 0:
            return 3.0 * math.exp(-self.nuke_t / 0.30)
        if self.blast_t < 0:
            return 0.0
        peak = 3.6 if self.type == TYPE_FLASH else (3.0 if self.type == TYPE_PLASMA else 2.6)
        return peak * math.exp(-self.blast_t / 0.045)

    def shock(self):
        if self.nuke_t >= 0:
            return -1.0 if self.nuke_t > 1.6 else self.nuke_t / 1.6
        if self.blast_t < 0 or self.blast_t > 0.8:
            return -1.0
        return self.blast_t / 0.8

    def shake(self):
        a = 0.0
        if self.blast_t >= 0:
            a += 0.050 * math.exp(-self.blast_t / 0.30)
        if self.nuke_t >= 0:
            a += 0.090 * math.exp(-self.nuke_t / 0.9)
            a += 0.012 * math.exp(-self.nuke_t / 6.0)
        a += 0.0045 * self.arm01() ** 2.5
        return a

    def zoom(self):
        z = 0.0
        if self.blast_t >= 0:
            z += 0.045 * math.exp(-self.blast_t / 0.28)
        if self.nuke_t >= 0:
            z += 0.075 * math.exp(-self.nuke_t / 0.7)
        return z

    def danger_pulse(self, t):
        a = self.arm01()
        f = 0.9 + 5.5 * a * a
        return 0.25 + 0.75 * (0.5 + 0.5 * math.sin(t * 6.2831853 * f))


def params(st, bx, by, t):
    g = GRADE[st.type]
    mush = st.mushroom01()
    nuking = st.nuking()

    if nuking:
        vort = mix(26.0, 55.0, mush)
        buoy = mix(300.0, 120.0, mush)
        sootw = mix(55.0, 10.0, mush)
        damp = mix(1.40, 0.85, mush)
        namp = mix(500.0, 220.0, mush)
        nscale = mix(3.0, 2.2, mush)
        tdiss = mix(0.50, 0.10, mush)
        cool = mix(2.00, 0.20, mush)
        sdiss = mix(0.20, 0.035, mush)
        syield = 2.0
        blast, brad = st.nuke_push(), st.nuke_push_radius()
        bheat, bhrad = st.nuke_heat(), st.nuke_heat_radius()
        spawn_r, spawn_rate = 0.30, (1.6 if st.nuke_t < 1.4 else 0.05)
        inten = clamp(1.0 - st.nuke_t / 18.0, 0.0, 1.0)
        tint = (mix(1.00, 1.00, mush), mix(0.92, 0.66, mush), mix(0.78, 0.34, mush))
        kbase, kspan = mix(2400.0, 800.0, mush), mix(3200.0, 1700.0, mush)
        flash_col = (1.00, 0.97, 0.90)
        detail, emis, smoke = mix(0.085, 0.055, mush), mix(3.0, 2.4, mush), mix(4.5, 6.5, mush)
        bloom_amt, thresh, aniso = 0.72, 0.75, mix(0.90, 0.72, mush)
        smoke_glow = mix(1.4, 7.0, mush)
    else:
        b = max(st.blast_t, 0.0)
        age = clamp((b - 0.15) / 0.55, 0.0, 1.0)
        vort, buoy, sootw, damp = 34.0, 620.0, 55.0, 1.30
        namp, nscale = 2600.0, 3.2
        tdiss = mix(1.20, 5.00, age)
        cool = mix(5.00, 14.00, age)
        sdiss = mix(0.20, 1.10, clamp(b / 3.0, 0.0, 1.0))
        syield = g[5]
        blast, brad = st.push(), st.push_radius()
        bheat, bhrad = st.heat(), st.heat_radius()
        spawn_r = 0.14
        spawn_rate = 1.2 if (b < 0.4 and st.blast_t >= 0) else 0.0
        inten = clamp(1.0 - b / 4.0, 0.0, 1.0) if st.blast_t >= 0 else 0.0
        tint, kbase, kspan = (g[0], g[1], g[2]), g[3], g[4]
        flash_col = g[6]
        detail, emis, smoke = 0.080, 3.2, 4.6
        bloom_amt, thresh, aniso = 0.55, 0.85, 0.85
        smoke_glow = 1.6

    amp = st.shake()
    flash = st.flash()
    return {
        "uIntensity": inten,
        "uTouch": (bx, by), "uTouchVel": (0.0, 0.0),
        "uTouchOn": 1.0 if (nuking and 0.45 < st.nuke_t < 1.9) else 0.0,
        "uTouchRadius": 0.032, "uBedFlat": 0.30,
        "uInjectFuel": 0.40, "uInjectHeat": 0.25,
        "uVorticity": vort, "uBuoyancy": buoy, "uSootWeight": sootw, "uDamping": damp,
        "uNoiseAmp": namp, "uNoiseScale": nscale,
        "uTempDiss": tdiss, "uFuelDiss": 0.6, "uSootDiss": sdiss, "uCooling": cool,
        "uBurnRate": 6.0, "uHeatRelease": 1.0, "uSootYield": syield, "uIgnition": 0.06,
        "uBlast": blast, "uBlastPos": (bx, by), "uBlastRadius": brad,
        "uBlastHeat": bheat, "uBlastHeatRadius": bhrad,
        "uSpawnRadius": spawn_r, "uSpawnRate": spawn_rate,
        "uPointScale": 5.5 if nuking else 4.0,
        "uDetail": detail, "uEmissive": emis, "uSmokeDensity": smoke,
        "uTint": tint, "uKelvinBase": kbase, "uKelvinSpan": kspan,
        "uCoal": 0.0, "uCoalRadius": 0.05, "uCoalFlat": 3.0,
        "uAniso": aniso, "uSmokeGlow": smoke_glow,
        "uThreshold": thresh, "uBloomAmount": bloom_amt, "uExposure": 1.12,
        "uVignette": 0.50,
        "uChroma": 0.0012 + 0.010 * min(flash, 1.0),
        "uShakeOffset": ((math.sin(t * 61.0) * 0.6 + math.sin(t * 37.3 + 1.3) * 0.4) * amp,
                         (math.sin(t * 49.7 + 2.1) * 0.6 + math.sin(t * 71.1 + 0.7) * 0.4) * amp),
        "uShakeRot": math.sin(t * 43.0 + 1.1) * amp * 0.5,
        "uZoom": st.zoom(),
        "uFlash": flash, "uFlashColor": flash_col,
        "uShockT": st.shock(), "uShockPos": (bx, by),
        "uDanger": st.arm01() if (st.arming or st.arm_t > 0.01) else 0.0,
        "uDangerPulse": st.danger_pulse(t),
    }


def main():
    global ARM_TIME
    ap = argparse.ArgumentParser()
    ap.add_argument("--w", type=int, default=300)
    ap.add_argument("--h", type=int, default=650)
    ap.add_argument("--sim", type=int, default=130)
    ap.add_argument("--iters", type=int, default=18)
    ap.add_argument("--out", default="/tmp/bomb")
    ap.add_argument("--seconds", type=float, default=6.0)
    ap.add_argument("--shots", default="0.35,0.5,1.0,2.5")
    ap.add_argument("--type", type=int, default=None, help="force a charge type 0..4")
    ap.add_argument("--nuke", action="store_true", help="hold long enough to detonate")
    ap.add_argument("--stats", action="store_true", help="print soot mass and spread")
    ap.add_argument("--arm", type=float, default=ARM_TIME,
                    help="shorten the countdown so a preview run is affordable")
    args = ap.parse_args()
    ARM_TIME = args.arm

    os.makedirs(args.out, exist_ok=True)
    ctx = moderngl.create_context(standalone=True, backend="egl")
    fire = Fire(ctx, args.w, args.h, args.sim, args.iters)

    st = BombState(args.type)
    shots = sorted(float(s) for s in args.shots.split(",") if s.strip())
    dt = 1.0 / 60.0
    t = 0.0
    prev_on = False
    shot_i = 0
    bx, by = 0.5, 0.20

    while t < args.seconds:
        on = t >= 0.30 if args.nuke else (0.30 <= t < 0.45)
        down = on and not prev_on
        prev_on = on
        if down:
            st.touch_down()
        st.update(dt, on)
        if st.reset_requested:
            fire.reset()

        P = params(st, bx, by, t)
        fire.step(dt, P)

        if args.stats and shot_i < len(shots) and t >= shots[shot_i]:
            import numpy as np
            raw = np.frombuffer(fire.fld[0][0].read(), dtype=np.float16)
            raw = raw.reshape(fire.sh, fire.sw, 4).astype(np.float32)
            temp, soot = raw[..., 0], raw[..., 2]
            ys = np.linspace(0, 1, fire.sh)[:, None]
            xs = np.linspace(0, 1, fire.sw)[None, :]
            m = soot.sum() + 1e-9
            cy = float((soot * ys).sum() / m)
            cx = float((soot * xs).sum() / m)
            ry = float(np.sqrt((soot * (ys - cy) ** 2).sum() / m))
            rx = float(np.sqrt((soot * (xs - cx) ** 2).sum() / m))
            print("   stats t=%5.2f soot=%7.1f centre=(%.2f,%.2f) spread=(%.3f,%.3f) "
                  "Tmax=%.2f Smax=%.2f" % (st.nuke_t, m, cx, cy, rx, ry,
                                           float(temp.max()), float(soot.max())))

        if shot_i < len(shots) and t >= shots[shot_i]:
            img = fire.render(P)
            tag = "nuke%.1f" % st.nuke_t if st.nuking() else "b%.1f" % max(st.blast_t, 0)
            img.save(os.path.join(args.out, "t%06.2f_%s.png" % (t, tag)))
            print("saved t=%.2f %s" % (t, tag))
            shot_i += 1
        t += dt

    print("done")


if __name__ == "__main__":
    main()
