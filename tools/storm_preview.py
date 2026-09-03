#!/usr/bin/env python3
"""Headless preview of the lightning mode.

Runs the shipped storm shaders on desktop GL and mirrors Bolt.java's channel
generator, so the discharges can be judged and tuned without a phone.
Keep in sync with Bolt.java / StormState.java / StormRenderer.java.
"""
import argparse
import math
import os
import random

import moderngl
import numpy as np
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SH = os.path.join(ROOT, "app/src/main/assets/shaders")

STYLE_STREAK, STYLE_TREE, STYLE_FORK, STYLE_STAIR, STYLE_RIBBON, STYLE_CRAWLER = range(6)


def read(name):
    with open(os.path.join(SH, name), encoding="utf-8") as f:
        return f.read()


def setu(prog, name, value):
    try:
        prog[name].value = value
    except KeyError:
        pass


def mix(a, b, t):
    return a + (b - a) * t


def clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


# ---------------------------------------------------------------- bolt geometry
class Bolt:
    MAIN_LEVELS = 6

    def __init__(self):
        self.alive = False
        self.verts = []
        self.age = 0.0

    def spawn(self, rnd, tx, ty, w, h, density, power):
        self.rnd = rnd
        self.w, self.h = w, h
        self.density = density
        self.power = power
        self.x, self.y = tx, ty
        self.style = rnd.randrange(6)
        self.age = 0.0
        self.alive = True
        self.flicker = rnd.random() * 6.283

        self.strokes = 1 + rnd.randrange(4)
        self.st, self.sa = [], []
        t = 0.0
        for i in range(self.strokes):
            self.st.append(t)
            self.sa.append(1.0 if i == 0 else 0.42 + 0.58 * rnd.random())
            t += 0.035 + rnd.random() * 0.115
        self.life = t + 0.30
        self.next_stroke = 1

        spread = 0.85 if self.style == STYLE_CRAWLER else 0.34
        self.srcx = tx * w + (rnd.random() * 2 - 1) * w * spread
        self.srcy = h * 1.06
        self.build()

    def update(self, dt):
        self.age += dt
        if self.next_stroke < self.strokes and self.age >= self.st[self.next_stroke]:
            self.next_stroke += 1
            if self.rnd.random() < 0.6:
                self.build()
        if self.age >= self.life:
            self.alive = False
        return self.alive

    def brightness(self):
        b = 0.0
        for i in range(self.strokes):
            d = self.age - self.st[i]
            if d >= 0:
                b = max(b, self.sa[i] * math.exp(-d / 0.045))
        b = max(b, 0.11 * math.exp(-self.age / 0.17))
        b *= 0.82 + 0.18 * math.sin(self.age * 190.0 + self.flicker)
        tail = 1.0
        if self.age > self.life - 0.10:
            tail = max(0.0, (self.life - self.age) / 0.10)
        return max(b, 0.0) * tail * self.power

    def subdivide(self, pts, jag, levels):
        for _ in range(levels):
            out = []
            for i in range(len(pts) - 1):
                ax, ay = pts[i]
                bx, by = pts[i + 1]
                dx, dy = bx - ax, by - ay
                ln = math.hypot(dx, dy) + 1e-5
                nx, ny = -dy / ln, dx / ln
                off = ln * jag * (self.rnd.random() * 2 - 1)
                out.append((ax, ay))
                out.append(((ax + bx) * 0.5 + nx * off, (ay + by) * 0.5 + ny * off))
            out.append(pts[-1])
            pts = out
            jag *= 0.62
        return pts

    def emit(self, pts, core_w, bright, taper):
        n = len(pts)
        if n < 2:
            return
        half = core_w * 4.0
        sx, sy = 2.0 / self.w, 2.0 / self.h

        # mitred normals: neighbouring quads share an edge instead of overlapping
        norms = []
        for i in range(n):
            a = max(i - 1, 0)
            b = min(i, n - 2)
            ax, ay = pts[a + 1][0] - pts[a][0], pts[a + 1][1] - pts[a][1]
            la = math.hypot(ax, ay) + 1e-5
            cx, cy = pts[b + 1][0] - pts[b][0], pts[b + 1][1] - pts[b][1]
            lc = math.hypot(cx, cy) + 1e-5
            p1x, p1y = -ay / la, ax / la
            p2x, p2y = -cy / lc, cx / lc
            mx, my = p1x + p2x, p1y + p2y
            ml = math.hypot(mx, my)
            if ml < 1e-4:
                mx, my = p2x, p2y
            else:
                mx, my = mx / ml, my / ml
            scale = half / max(mx * p2x + my * p2y, 0.35)
            norms.append((mx * scale, my * scale))

        def put(x, y, side, b):
            self.verts.extend((x * sx - 1.0, y * sy - 1.0, side, b))

        for i in range(n - 1):
            t0, t1 = i / (n - 1), (i + 1) / (n - 1)
            b0 = bright * (1 - taper * t0)
            b1 = bright * (1 - taper * t1)
            ax, ay = pts[i]
            cx, cy = pts[i + 1]
            n0x, n0y = norms[i]
            n1x, n1y = norms[i + 1]
            put(ax + n0x, ay + n0y, 1.0, b0)
            put(ax - n0x, ay - n0y, -1.0, b0)
            put(cx + n1x, cy + n1y, 1.0, b1)
            put(cx + n1x, cy + n1y, 1.0, b1)
            put(ax - n0x, ay - n0y, -1.0, b0)
            put(cx - n1x, cy - n1y, -1.0, b1)

    def build(self):
        self.verts = []
        rnd = self.rnd
        levels = self.MAIN_LEVELS
        if self.style == STYLE_TREE:
            jag, core_w, bp, bd = 0.24, 2.6, 0.20, 2
        elif self.style == STYLE_FORK:
            jag, core_w, bp, bd = 0.18, 2.8, 0.08, 1
        elif self.style == STYLE_STAIR:
            jag, core_w, bp, bd, levels = 0.44, 3.1, 0.10, 1, 4
        elif self.style == STYLE_RIBBON:
            jag, core_w, bp, bd = 0.16, 2.2, 0.07, 1
        elif self.style == STYLE_CRAWLER:
            jag, core_w, bp, bd = 0.20, 2.4, 0.14, 1
        else:
            jag, core_w, bp, bd = 0.13, 2.9, 0.035, 1
        core_w = max(core_w * self.density, 1.8)

        main = self.subdivide([(self.srcx, self.srcy),
                               (self.x * self.w, self.y * self.h)], jag, levels)
        self.main = main
        self.emit(main, core_w, 1.0, 0.25)

        if self.style == STYLE_RIBBON:
            off = (6.0 + rnd.random() * 8.0) * self.density
            self.emit([(x + off, y + off * 0.25) for x, y in main], core_w * 0.72, 0.55, 0.3)

        n = len(main)
        if self.style == STYLE_FORK:
            at = n // 3 + rnd.randrange(max(n // 3, 1))
            self.branch(at, core_w * 0.85, 0.8, 0.55, 1)

        for i in range(4, n - 3):
            if rnd.random() < bp:
                self.branch(i, core_w * 0.55, 0.58, 0.45, bd)

    def branch(self, at, width, bright, len_frac, depth):
        rnd = self.rnd
        main = self.main
        n = len(main)
        ax, ay = main[at]
        rx0, ry0 = main[min(at + 2, n - 1)]
        dx, dy = rx0 - ax, ry0 - ay
        ln = math.hypot(dx, dy)
        if ln < 1e-3:
            return
        dx, dy = dx / ln, dy / ln
        ang = math.radians(22.0 + rnd.random() * 42.0) * (1 if rnd.random() < 0.5 else -1)
        ca, sa = math.cos(ang), math.sin(ang)
        rx, ry = dx * ca - dy * sa, dx * sa + dy * ca
        remaining = math.hypot(main[-1][0] - ax, main[-1][1] - ay)
        bl = max(remaining * len_frac * (0.4 + rnd.random() * 0.8), 40.0 * self.density)

        pts = self.subdivide([(ax, ay), (ax + rx * bl, ay + ry * bl)], 0.26, 4)
        self.emit(pts, width, bright, 0.85)

        if depth > 1 and rnd.random() < 0.45:
            m = len(pts)
            at2 = m // 2 + rnd.randrange(max(m // 3, 1))
            sx, sy = pts[at2]
            ndx, ndy = pts[min(at2 + 1, m - 1)]
            sdx, sdy = ndx - sx, ndy - sy
            sl = math.hypot(sdx, sdy)
            if sl > 1e-3:
                a2 = math.radians(25.0 + rnd.random() * 45.0) * (1 if rnd.random() < 0.5 else -1)
                c2, s2 = math.cos(a2), math.sin(a2)
                r2x = (sdx / sl) * c2 - (sdy / sl) * s2
                r2y = (sdx / sl) * s2 + (sdy / sl) * c2
                l2 = bl * (0.25 + rnd.random() * 0.35)
                p2 = self.subdivide([(sx, sy), (sx + r2x * l2, sy + r2y * l2)], 0.30, 3)
                self.emit(p2, width * 0.6, bright * 0.6, 0.9)


# ------------------------------------------------------------------ state
class StormState:
    RAMP, FALL, AFTER = 9.0, 1.10, 6.50

    def __init__(self):
        self.intensity = 0.0
        self.afterglow = 0.0
        self.strike_timer = 0.0
        self.jolt = 0.0
        self.flash = 0.0

    def update(self, dt, touching):
        if touching:
            self.intensity = min(1.0, self.intensity + dt / self.RAMP)
            self.afterglow = 1.0
        else:
            self.intensity = max(0.0, self.intensity - dt / self.FALL)
            self.afterglow = max(0.0, self.afterglow - dt / self.AFTER)
        self.jolt = max(0.0, self.jolt - dt / 0.42)
        self.flash = max(0.0, self.flash - dt * 3.4)
        self.strike_timer -= dt

    def interval(self):
        return mix(0.85, 0.045, self.intensity ** 1.30)

    def spread(self):
        return mix(0.015, 0.62, self.intensity ** 1.50)

    def burst(self):
        return 3 if self.intensity > 0.90 else (2 if self.intensity > 0.72 else 1)

    def register(self, power):
        self.jolt = min(1.0, self.jolt + power * 0.85)
        self.flash = min(1.6, self.flash + power * 0.55)

    def shake(self):
        q = self.intensity
        return 0.0035 * q * q + 0.020 * self.jolt * (0.35 + 0.65 * q)

    def ambient(self):
        return 0.015 + 0.24 * self.intensity + 0.07 * self.afterglow * (1 - self.intensity)

    def cloud_base(self):
        return mix(0.80, 0.58, self.intensity)


# ------------------------------------------------------------------ renderer
class Storm:
    MAX_BOLTS = 16

    def __init__(self, ctx, w, h):
        self.ctx = ctx
        self.W, self.H = w, h
        self.aspect = (w / float(h), 1.0)
        self.time = 0.0
        self.density = max(w / 1080.0, 0.55)

        head = "#version 330 core\n#define LOWPREC 0\n" + read("common.glsl") + "\n"
        fsv = head + read("fullscreen.vert")

        def prog(frag, vert=None):
            return ctx.program(vertex_shader=vert or fsv,
                               fragment_shader=head + read(frag))

        self.p_sky = prog("storm_sky.frag")
        self.p_bolt = ctx.program(vertex_shader=head + read("bolt.vert"),
                                  fragment_shader=head + read("bolt.frag"))
        self.p_pre = prog("bloom_prefilter.frag")
        self.p_down = prog("bloom_down.frag")
        self.p_up = prog("bloom_up.frag")
        self.p_comp = prog("composite.frag")

        img = Image.open(os.path.join(ROOT, "app/src/main/assets/noise.png")).convert("RGBA")
        self.noise = ctx.texture(img.size, 4, img.tobytes())
        self.noise.filter = (moderngl.LINEAR, moderngl.LINEAR)
        self.noise.repeat_x = self.noise.repeat_y = True

        self.scene = self.target(w, h)
        self.mips = []
        mw, mh = w // 2, h // 2
        for _ in range(5):
            self.mips.append(self.target(max(mw, 2), max(mh, 2)))
            mw, mh = max(mw // 2, 2), max(mh // 2, 2)

        self.vbo = ctx.buffer(reserve=4 * 4 * 60000)
        self.bolt_vao = ctx.vertex_array(self.p_bolt, [(self.vbo, "2f 2f", "aPos", "aSideBright")])
        self.vaos = {}

        self.bolts = [Bolt() for _ in range(self.MAX_BOLTS)]
        self.ranges = [(0, 0)] * self.MAX_BOLTS
        self.state = StormState()

    def target(self, w, h):
        t = self.ctx.texture((w, h), 4, dtype="f2")
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
        t.repeat_x = t.repeat_y = False
        return (t, self.ctx.framebuffer(color_attachments=[t]))

    def draw(self, prog, fb):
        fb.use()
        if id(prog) not in self.vaos:
            self.vaos[id(prog)] = self.ctx.vertex_array(prog, [])
        self.vaos[id(prog)].render(mode=moderngl.TRIANGLES, vertices=3)

    def fire(self, rnd, x, y, power):
        for b in self.bolts:
            if b.alive:
                continue
            b.spawn(rnd, x, y, self.W, self.H, self.density, power)
            self.state.register(power)
            return

    def step(self, dt, rnd, touching, tx, ty, down):
        self.time += dt
        st = self.state
        st.update(dt, touching)
        if down:
            self.fire(rnd, tx, ty, 1.0)
            st.strike_timer = st.interval()
        if touching and st.strike_timer <= 0.0:
            for _ in range(st.burst()):
                sp = st.spread()
                bx = clamp(tx + (rnd.random() * 2 - 1) * sp, 0.04, 0.96)
                by = clamp(ty + (rnd.random() * 2 - 1) * sp * 0.75, 0.03, 0.88)
                self.fire(rnd, bx, by, 0.75 + 0.25 * rnd.random())
            st.strike_timer = st.interval()
        for b in self.bolts:
            if b.alive:
                b.update(dt)

    def render(self):
        ctx = self.ctx
        st = self.state
        ctx.disable(moderngl.BLEND)

        # upload bolt geometry
        data = []
        cursor = 0
        peak = 0.0
        for i, b in enumerate(self.bolts):
            if not b.alive or not b.verts:
                self.ranges[i] = (0, 0)
                continue
            nv = len(b.verts) // 4
            self.ranges[i] = (cursor, nv)
            data.extend(b.verts)
            cursor += nv
            peak = max(peak, b.brightness())
        if data:
            arr = np.array(data, dtype="f4")
            self.vbo.write(arr.tobytes())

        # lights
        lights = sorted(((b.brightness(), b.x, b.y) for b in self.bolts if b.alive),
                        reverse=True)[:6]
        flat = []
        for i in range(6):
            if i < len(lights):
                flat.extend((lights[i][1], lights[i][2], lights[i][0]))
            else:
                flat.extend((0.0, 0.0, 0.0))
        try:
            self.p_sky["uLights"].value = [tuple(flat[i * 3:i * 3 + 3]) for i in range(6)]
        except KeyError:
            pass

        self.noise.use(0)
        setu(self.p_sky, "uNoise", 0)
        setu(self.p_sky, "uAspect", self.aspect)
        setu(self.p_sky, "uTime", self.time)
        setu(self.p_sky, "uIntensity", st.intensity)
        setu(self.p_sky, "uAmbient", st.ambient() + st.flash * 0.22)
        setu(self.p_sky, "uCloudBase", st.cloud_base())
        self.draw(self.p_sky, self.scene[1])

        if data:
            ctx.enable(moderngl.BLEND)
            ctx.blend_func = (moderngl.ONE, moderngl.ONE)
            self.scene[1].use()
            setu(self.p_bolt, "uCoreFrac", 0.28)
            setu(self.p_bolt, "uCoreColor", (0.88, 0.94, 1.00))
            setu(self.p_bolt, "uHaloColor", (0.24, 0.46, 1.00))
            for i, b in enumerate(self.bolts):
                first, count = self.ranges[i]
                if count == 0:
                    continue
                setu(self.p_bolt, "uBright", b.brightness())
                self.bolt_vao.render(mode=moderngl.TRIANGLES, vertices=count, first=first)
            ctx.disable(moderngl.BLEND)

        # bloom
        self.scene[0].use(0)
        setu(self.p_pre, "uTex", 0)
        setu(self.p_pre, "uThreshold", 0.42)
        setu(self.p_pre, "uKnee", 0.6)
        self.draw(self.p_pre, self.mips[0][1])
        for i in range(1, len(self.mips)):
            src = self.mips[i - 1]
            src[0].use(0)
            setu(self.p_down, "uTex", 0)
            setu(self.p_down, "uTexel", (1 / src[0].width, 1 / src[0].height))
            self.draw(self.p_down, self.mips[i][1])
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = (moderngl.ONE, moderngl.ONE)
        for i in range(len(self.mips) - 1, 0, -1):
            src = self.mips[i]
            src[0].use(0)
            setu(self.p_up, "uTex", 0)
            setu(self.p_up, "uTexel", (1 / src[0].width, 1 / src[0].height))
            setu(self.p_up, "uRadius", 1.0)
            self.draw(self.p_up, self.mips[i - 1][1])
        ctx.disable(moderngl.BLEND)

        q = st.intensity
        amp = st.shake()
        t = self.time
        self.scene[0].use(0)
        self.mips[0][0].use(1)
        self.noise.use(2)
        p = self.p_comp
        setu(p, "uScene", 0)
        setu(p, "uBloom", 1)
        setu(p, "uNoise", 2)
        setu(p, "uResolution", (float(self.W), float(self.H)))
        setu(p, "uAspect", self.aspect)
        setu(p, "uTime", t)
        setu(p, "uShakeOffset",
             ((math.sin(t * 51.0) * 0.6 + math.sin(t * 33.7 + 1.1) * 0.4) * amp,
              (math.sin(t * 44.3 + 2.7) * 0.6 + math.sin(t * 61.1 + 0.5) * 0.4) * amp))
        setu(p, "uShakeRot", math.sin(t * 39.0 + 1.9) * amp * 0.5)
        setu(p, "uZoom", 0.012 * q * q + 0.02 * st.jolt)
        setu(p, "uFlash", min(0.42, peak * 0.085 + st.flash * 0.10))
        setu(p, "uFlashColor", (0.72, 0.83, 1.00))
        setu(p, "uIntensity", q)
        setu(p, "uBloomAmount", mix(0.85, 1.25, q))
        setu(p, "uExposure", mix(1.05, 1.15, q))
        setu(p, "uVignette", 0.45)
        setu(p, "uChroma", 0.0005 + 0.0009 * q)
        setu(p, "uShockT", -1.0)
        setu(p, "uShockPos", (0.5, 0.5))

        out = self.ctx.simple_framebuffer((self.W, self.H))
        self.draw(p, out)
        img = Image.frombytes("RGB", (self.W, self.H),
                              out.read(components=3)).transpose(Image.FLIP_TOP_BOTTOM)
        out.release()
        return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--w", type=int, default=300)
    ap.add_argument("--h", type=int, default=650)
    ap.add_argument("--out", default="/tmp/storm")
    ap.add_argument("--seconds", type=float, default=10.0)
    ap.add_argument("--shots", default="0.35,0.45,2.0,5.0,8.0,9.6")
    ap.add_argument("--tap", action="store_true")
    ap.add_argument("--seed", type=int, default=5)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    ctx = moderngl.create_context(standalone=True, backend="egl")
    storm = Storm(ctx, args.w, args.h)
    rnd = random.Random(args.seed)

    shots = sorted(float(s) for s in args.shots.split(",") if s.strip())
    dt = 1.0 / 60.0
    t = 0.0
    prev_on = False
    shot_i = 0
    touch = (0.5, 0.30)

    while t < args.seconds:
        on = (0.30 <= t < 0.42) if args.tap else (t >= 0.30)
        down = on and not prev_on
        prev_on = on
        storm.step(dt, rnd, on, touch[0], touch[1], down)

        if shot_i < len(shots) and t >= shots[shot_i]:
            img = storm.render()
            name = os.path.join(args.out, "t%05.2f_i%.2f.png" % (t, storm.state.intensity))
            img.save(name)
            print("saved", name)
            shot_i += 1
        t += dt

    print("done")


if __name__ == "__main__":
    main()
