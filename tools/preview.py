#!/usr/bin/env python3
"""Headless preview of the fire pipeline.

Runs the exact shader files the app ships, on a desktop GL context, so the
simulation constants can be tuned by looking at real frames instead of guessing.
Keep the parameter block in sync with FireRenderer.java.
"""
import argparse
import os

import moderngl
import numpy as np
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SH = os.path.join(ROOT, "app/src/main/assets/shaders")


def mix(a, b, t):
    return a + (b - a) * t


def read(name):
    with open(os.path.join(SH, name), encoding="utf-8") as f:
        return f.read()


def setu(prog, name, value):
    try:
        prog[name].value = value
    except KeyError:
        pass


class Fire:
    def __init__(self, ctx, screen_w, screen_h, sim_w, iterations=28):
        self.ctx = ctx
        self.W, self.H = screen_w, screen_h
        self.sw = sim_w
        self.sh = max(8, int(round(sim_w * screen_h / float(screen_w))))
        self.iterations = iterations
        self.aspect = (screen_w / float(screen_h), 1.0)
        self.texel = (1.0 / self.sw, 1.0 / self.sh)
        self.time = 0.0

        common = read("common.glsl")
        head = "#version 330 core\n#define LOWPREC 0\n" + common + "\n"
        self.head = head
        fsv = head + read("fullscreen.vert")

        def prog(vert, frag):
            return ctx.program(vertex_shader=vert, fragment_shader=head + read(frag))

        self.p_curl = prog(fsv, "curl.frag")
        self.p_vel = prog(fsv, "velocity.frag")
        self.p_div = prog(fsv, "divergence.frag")
        self.p_pres = prog(fsv, "pressure.frag")
        self.p_proj = prog(fsv, "project.frag")
        self.p_fields = prog(fsv, "fields.frag")
        self.p_advect = prog(fsv, "advect.frag")
        self.p_render = prog(fsv, "render_fire.frag")
        self.p_pupd = prog(fsv, "particles_update.frag")
        self.p_pdraw = ctx.program(vertex_shader=head + read("particles.vert"),
                                   fragment_shader=head + read("particles.frag"))
        self.p_pre = prog(fsv, "bloom_prefilter.frag")
        self.p_down = prog(fsv, "bloom_down.frag")
        self.p_up = prog(fsv, "bloom_up.frag")
        self.p_comp = prog(fsv, "composite.frag")

        self.quad = ctx.vertex_array(self.p_curl, [])
        self.vaos = {}

        # noise
        img = Image.open(os.path.join(ROOT, "app/src/main/assets/noise.png")).convert("RGBA")
        self.noise = ctx.texture(img.size, 4, img.tobytes())
        self.noise.filter = (moderngl.LINEAR, moderngl.LINEAR)
        self.noise.repeat_x = True
        self.noise.repeat_y = True

        def pair(w, h, comps=4):
            return [self.target(w, h, comps) for _ in range(2)]

        self.vel = pair(self.sw, self.sh)
        self.pres = pair(self.sw, self.sh)
        self.fld = pair(self.sw, self.sh)
        self.curl = self.target(self.sw, self.sh)
        self.div = self.target(self.sw, self.sh)
        self.fldA = self.target(self.sw, self.sh)
        self.fldB = self.target(self.sw, self.sh)

        self.PT = 64
        self.NPART = self.PT * self.PT
        self.pstate = pair(self.PT, self.PT)

        self.scene = self.target(self.W, self.H)
        self.mips = []
        w, h = self.W // 2, self.H // 2
        for _ in range(5):
            self.mips.append(self.target(max(w, 2), max(h, 2)))
            w, h = max(w // 2, 2), max(h // 2, 2)

        self.reset()

    def target(self, w, h, comps=4):
        t = self.ctx.texture((w, h), comps, dtype="f2")
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
        t.repeat_x = False
        t.repeat_y = False
        fb = self.ctx.framebuffer(color_attachments=[t])
        return (t, fb)

    def reset(self):
        for grp in (self.vel, self.pres, self.fld, self.pstate):
            for t, fb in grp:
                fb.clear(0.0, 0.0, 0.0, 0.0)
        for t, fb in (self.curl, self.div, self.scene, self.fldA, self.fldB):
            fb.clear(0.0, 0.0, 0.0, 0.0)
        # seed particle ids
        seeds = np.zeros((self.PT, self.PT, 4), dtype=np.float16)
        rng = np.random.default_rng(7)
        seeds[..., 3] = rng.random((self.PT, self.PT)).astype(np.float16)
        self.pstate[0][0].write(seeds.tobytes())
        self.pstate[1][0].write(seeds.tobytes())

    def draw(self, prog, fb):
        fb.use()
        if id(prog) not in self.vaos:
            self.vaos[id(prog)] = self.ctx.vertex_array(prog, [])
        self.vaos[id(prog)].render(mode=moderngl.TRIANGLES, vertices=3)

    def step(self, dt, P):
        self.time += dt
        t = self.time
        ctx = self.ctx
        ctx.disable(moderngl.BLEND)

        # ---- curl -----------------------------------------------------------
        self.vel[0][0].use(0)
        setu(self.p_curl, "uVelocity", 0)
        setu(self.p_curl, "uTexel", self.texel)
        self.draw(self.p_curl, self.curl[1])

        # ---- velocity: advect + forces --------------------------------------
        p = self.p_vel
        self.vel[0][0].use(0)
        self.curl[0].use(1)
        self.fld[0][0].use(2)
        self.noise.use(3)
        setu(p, "uVelocity", 0)
        setu(p, "uCurl", 1)
        setu(p, "uFields", 2)
        setu(p, "uNoise", 3)
        setu(p, "uTexel", self.texel)
        setu(p, "uAspect", self.aspect)
        setu(p, "uDt", dt)
        setu(p, "uTime", t)
        for k in ("uVorticity", "uBuoyancy", "uSootWeight", "uDamping",
                  "uNoiseAmp", "uNoiseScale", "uTouchRadius", "uTouchOn",
                  "uBlast", "uBlastRadius"):
            setu(p, k, P[k])
        setu(p, "uTouch", P["uTouch"])
        setu(p, "uTouchVel", P["uTouchVel"])
        setu(p, "uBlastPos", P["uBlastPos"])
        self.draw(p, self.vel[1][1])
        self.vel.reverse()

        # ---- divergence ------------------------------------------------------
        self.vel[0][0].use(0)
        setu(self.p_div, "uVelocity", 0)
        setu(self.p_div, "uTexel", self.texel)
        self.draw(self.p_div, self.div[1])

        # ---- pressure --------------------------------------------------------
        self.pres[0][1].clear(0.0, 0.0, 0.0, 0.0)
        p = self.p_pres
        setu(p, "uTexel", self.texel)
        for _ in range(self.iterations):
            self.pres[0][0].use(0)
            self.div[0].use(1)
            setu(p, "uPressure", 0)
            setu(p, "uDivergence", 1)
            self.draw(p, self.pres[1][1])
            self.pres.reverse()

        # ---- project ---------------------------------------------------------
        p = self.p_proj
        self.pres[0][0].use(0)
        self.vel[0][0].use(1)
        setu(p, "uPressure", 0)
        setu(p, "uVelocity", 1)
        setu(p, "uTexel", self.texel)
        self.draw(p, self.vel[1][1])
        self.vel.reverse()

        # ---- fields: MacCormack advection then chemistry ----------------------
        p = self.p_advect
        setu(p, "uTexel", self.texel)
        self.vel[0][0].use(0)
        self.fld[0][0].use(1)
        setu(p, "uVelocity", 0)
        setu(p, "uSource", 1)
        setu(p, "uDt", dt)
        self.draw(p, self.fldA[1])

        self.vel[0][0].use(0)
        self.fldA[0].use(1)
        setu(p, "uVelocity", 0)
        setu(p, "uSource", 1)
        setu(p, "uDt", -dt)
        self.draw(p, self.fldB[1])

        p = self.p_fields
        self.vel[0][0].use(0)
        self.fld[0][0].use(1)
        self.noise.use(2)
        self.fldA[0].use(4)
        self.fldB[0].use(5)
        setu(p, "uVelocity", 0)
        setu(p, "uFields", 1)
        setu(p, "uNoise", 2)
        setu(p, "uPhiHat", 4)
        setu(p, "uPhiTilde", 5)
        setu(p, "uTexel", self.texel)
        setu(p, "uAspect", self.aspect)
        setu(p, "uDt", dt)
        setu(p, "uTime", t)
        for k in ("uTempDiss", "uFuelDiss", "uSootDiss", "uCooling", "uBurnRate",
                  "uHeatRelease", "uSootYield", "uIgnition", "uTouchOn",
                  "uTouchRadius", "uInjectFuel", "uInjectHeat", "uBlastHeat",
                  "uBedFlat"):
            setu(p, k, P[k])
        setu(p, "uBlastRadius", P["uBlastHeatRadius"])
        setu(p, "uTouch", P["uTouch"])
        setu(p, "uBlastPos", P["uBlastPos"])
        self.draw(p, self.fld[1][1])
        self.fld.reverse()

        # ---- particles -------------------------------------------------------
        p = self.p_pupd
        self.pstate[0][0].use(0)
        self.vel[0][0].use(1)
        self.fld[0][0].use(2)
        setu(p, "uState", 0)
        setu(p, "uVelocity", 1)
        setu(p, "uFields", 2)
        setu(p, "uTexel", self.texel)
        setu(p, "uAspect", self.aspect)
        setu(p, "uDt", dt)
        setu(p, "uTime", t)
        setu(p, "uSpawn", P["uTouch"])
        setu(p, "uSpawnRadius", P["uSpawnRadius"])
        setu(p, "uSpawnRate", P["uSpawnRate"])
        setu(p, "uIntensity", P["uIntensity"])
        self.draw(p, self.pstate[1][1])
        self.pstate.reverse()

    def render(self, P):
        ctx = self.ctx
        ctx.disable(moderngl.BLEND)

        p = self.p_render
        self.fld[0][0].use(0)
        self.noise.use(1)
        setu(p, "uFields", 0)
        setu(p, "uNoise", 1)
        setu(p, "uAspect", self.aspect)
        setu(p, "uTime", self.time)
        for k in ("uDetail", "uEmissive", "uSmokeDensity", "uIntensity",
                  "uCoal", "uCoalRadius", "uCoalFlat"):
            setu(p, k, P[k])
        setu(p, "uTouch", P["uTouch"])
        self.draw(p, self.scene[1])

        # embers, additive
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = (moderngl.ONE, moderngl.ONE)
        ctx.enable(moderngl.PROGRAM_POINT_SIZE)
        self.scene[1].use()
        self.pstate[0][0].use(0)
        setu(self.p_pdraw, "uState", 0)
        setu(self.p_pdraw, "uTexSize", self.PT)
        setu(self.p_pdraw, "uPointScale", P["uPointScale"])
        setu(self.p_pdraw, "uIntensity", P["uIntensity"])
        if "pdraw" not in self.vaos:
            self.vaos["pdraw"] = ctx.vertex_array(self.p_pdraw, [])
        self.vaos["pdraw"].render(mode=moderngl.POINTS, vertices=self.NPART)
        ctx.disable(moderngl.BLEND)

        # bloom
        p = self.p_pre
        self.scene[0].use(0)
        setu(p, "uTex", 0)
        setu(p, "uThreshold", P["uThreshold"])
        setu(p, "uKnee", 0.6)
        self.draw(p, self.mips[0][1])

        for i in range(1, len(self.mips)):
            src = self.mips[i - 1]
            self.mips[i][1].clear(0.0, 0.0, 0.0, 0.0)
            src[0].use(0)
            setu(self.p_down, "uTex", 0)
            setu(self.p_down, "uTexel", (1.0 / src[0].width, 1.0 / src[0].height))
            self.draw(self.p_down, self.mips[i][1])

        ctx.enable(moderngl.BLEND)
        ctx.blend_func = (moderngl.ONE, moderngl.ONE)
        for i in range(len(self.mips) - 1, 0, -1):
            src = self.mips[i]
            src[0].use(0)
            setu(self.p_up, "uTex", 0)
            setu(self.p_up, "uTexel", (1.0 / src[0].width, 1.0 / src[0].height))
            setu(self.p_up, "uRadius", 1.0)
            self.draw(self.p_up, self.mips[i - 1][1])
        ctx.disable(moderngl.BLEND)

        # composite
        p = self.p_comp
        self.scene[0].use(0)
        self.mips[0][0].use(1)
        self.noise.use(2)
        setu(p, "uScene", 0)
        setu(p, "uBloom", 1)
        setu(p, "uNoise", 2)
        setu(p, "uResolution", (float(self.W), float(self.H)))
        setu(p, "uAspect", self.aspect)
        setu(p, "uTime", self.time)
        setu(p, "uShakeOffset", P["uShakeOffset"])
        for k in ("uShakeRot", "uZoom", "uFlash", "uIntensity", "uBloomAmount",
                  "uExposure", "uVignette", "uShockT", "uChroma"):
            setu(p, k, P[k])
        setu(p, "uShockPos", P["uShockPos"])
        setu(p, "uFlashColor", (1.0, 0.94, 0.84))
        out = self.ctx.simple_framebuffer((self.W, self.H))
        self.draw(p, out)
        data = out.read(components=3)
        img = Image.frombytes("RGB", (self.W, self.H), data).transpose(Image.FLIP_TOP_BOTTOM)
        out.release()
        return img


# ---------------------------------------------------------------------------
# Tunable parameter block -- mirrored in FireRenderer.java
# ---------------------------------------------------------------------------
def params(intensity, touch_on, touch, touch_vel, blast, shake, flash, shock,
           blast_pos=(0.5, 0.3), blast_heat=0.0, blast_radius=0.2, strike=0.0,
           blast_heat_radius=0.2, expl_t=-1.0):
    q = intensity
    q2 = q * q
    out = {
        "uIntensity": q,
        "uTouch": touch,
        "uTouchVel": touch_vel,
        "uTouchOn": 1.0 if touch_on else 0.0,
        "uTouchRadius": mix(0.028, 0.30, pow(q, 1.8)) * (1.0 + 1.1 * strike),
        "uBedFlat": mix(1.0, 2.4, q),
        "uCoal": min(1.0, max(0.0, (q - 0.03) / 0.22)),
        "uCoalRadius": mix(0.030, 0.26, pow(q, 1.4)),
        "uCoalFlat": 3.2,

        "uVorticity": mix(20.0, 36.0, q),
        "uBuoyancy": mix(700.0, 900.0, q),
        "uSootWeight": 90.0,
        "uDamping": mix(2.60, 1.10, q),
        "uNoiseAmp": mix(900.0, 3200.0, q),
        "uNoiseScale": mix(8.0, 2.6, q),

        "uTempDiss": mix(5.50, 1.30, q),
        "uFuelDiss": mix(1.20, 0.50, q),
        "uSootDiss": mix(2.00, 0.45, q),
        "uCooling": mix(2.20, 5.00, q),
        "uBurnRate": 3.0,
        "uHeatRelease": mix(1.60, 1.25, q),
        "uSootYield": mix(0.04, 0.75, pow(q, 1.4)),
        "uIgnition": 0.08,
        "uInjectFuel": mix(2.8, 3.8, q) * (1.0 + 3.0 * strike),
        "uInjectHeat": mix(1.4, 1.8, q) * (1.0 + 3.5 * strike),

        "uBlast": blast,
        "uBlastPos": blast_pos,
        "uBlastRadius": blast_radius,
        "uBlastHeatRadius": blast_heat_radius,
        "uBlastHeat": blast_heat,

        "uSpawnRadius": mix(0.02, 0.26, q),
        "uSpawnRate": mix(0.015, 0.50, q),
        "uPointScale": mix(2.0, 6.5, q),

        "uDetail": mix(0.020, 0.075, q),
        "uEmissive": mix(3.4, 5.2, q),
        "uSmokeDensity": 3.4,

        "uThreshold": 0.65,
        "uBloomAmount": mix(0.55, 1.00, q),
        "uExposure": mix(1.10, 1.18, q),
        "uVignette": 0.55,
        "uChroma": 0.0010 + 0.0055 * q + 0.020 * flash,
        "uShakeOffset": shake,
        "uShakeRot": 0.006 * q2,
        "uZoom": 0.02 * q2,
        "uFlash": flash,
        "uShockT": shock,
        "uShockPos": blast_pos,
    }
    if expl_t >= 0.0:
        # A fireball is not a sustained flame: it flashes, then collapses into
        # dark rolling smoke within a few tenths of a second.
        k = min(max((expl_t - 0.18) / 0.35, 0.0), 1.0)
        out["uTempDiss"] = mix(1.40, 4.50, k)
        out["uCooling"] = mix(6.00, 12.00, k)
        out["uBurnRate"] = 6.0
        out["uHeatRelease"] = 1.0
        out["uSootYield"] = 1.60
        out["uSootDiss"] = mix(0.25, 1.70, k)
        out["uSmokeDensity"] = 5.0
        out["uEmissive"] = mix(3.2, 2.6, k)
        out["uDetail"] = 0.085
    return out


import math


class Controller:
    """Mirrors FireRenderer's state machine so both can be tuned together."""

    RAMP = 9.0        # seconds of holding to reach a full-screen fire
    DECAY = 2.5       # seconds to fade out after letting go
    STRIKE = 0.28     # match-strike burst length
    EXPL_LEN = 2.6    # full explosion sequence, then everything resets

    def __init__(self):
        self.intensity = 0.0
        self.strike = 0.0
        self.exploding = False
        self.et = 0.0
        self.blast_pos = (0.5, 0.30)
        self.reset_requested = False

    def touch_down(self):
        if not self.exploding:
            self.strike = self.STRIKE

    def update(self, dt, touching, touch):
        self.reset_requested = False
        if self.exploding:
            self.et += dt
            if self.et < 0.25:
                pass
            else:
                self.intensity = max(0.0, self.intensity - dt / 1.5)
            if self.et >= self.EXPL_LEN:
                self.exploding = False
                self.et = 0.0
                self.intensity = 0.0
                self.reset_requested = True
            return

        self.strike = max(0.0, self.strike - dt)
        if touching:
            self.intensity = min(1.0, self.intensity + dt / self.RAMP)
            if self.intensity >= 1.0:
                self.exploding = True
                self.et = 0.0
                self.blast_pos = touch
        else:
            self.intensity = max(0.0, self.intensity - dt / self.DECAY)

    # ---- derived visual quantities ---------------------------------------
    def strike01(self):
        return self.strike / self.STRIKE if self.STRIKE > 0 else 0.0

    def blast(self):
        if not self.exploding:
            return 0.0
        return 42000.0 * math.exp(-self.et / 0.045) if self.et < 0.30 else 0.0

    def blast_radius(self):
        return 0.03 + self.et * 1.30

    def blast_heat(self):
        if not self.exploding:
            return 0.0
        return 30.0 * math.exp(-self.et / 0.055) if self.et < 0.35 else 0.0

    def blast_heat_radius(self):
        return 0.05 + self.et * 0.85

    def flash(self):
        if not self.exploding:
            return 0.0
        return 1.2 * math.exp(-self.et / 0.080)

    def shock(self):
        if not self.exploding or self.et > 0.85:
            return -1.0
        return self.et / 0.85

    def shake_amp(self):
        q = self.intensity
        base = 0.0075 * q * q * q
        if self.exploding:
            base += 0.055 * math.exp(-self.et / 0.32)
        return base

    def shake(self, t):
        a = self.shake_amp()
        ox = math.sin(t * 47.0) * 0.62 + math.sin(t * 31.3 + 1.7) * 0.38
        oy = math.sin(t * 53.7 + 2.3) * 0.62 + math.sin(t * 37.1 + 0.9) * 0.38
        rot = math.sin(t * 41.0 + 0.4) * a * 0.55
        return (ox * a, oy * a), rot


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--w", type=int, default=432)
    ap.add_argument("--h", type=int, default=936)
    ap.add_argument("--sim", type=int, default=160)
    ap.add_argument("--iters", type=int, default=28)
    ap.add_argument("--out", default=os.path.join(ROOT, "preview"))
    ap.add_argument("--ramp", type=float, default=9.0)
    ap.add_argument("--seconds", type=float, default=10.0)
    ap.add_argument("--shots", default="0.5,1.0,2.0,3.5,5.0,7.0,9.0")
    ap.add_argument("--tap", action="store_true", help="short tap instead of a hold")
    ap.add_argument("--drag", action="store_true", help="move the finger around")
    ap.add_argument("--fixed", type=float, default=-1.0,
                    help="hold intensity at this value instead of ramping")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    ctx = moderngl.create_context(standalone=True, backend="egl")
    fire = Fire(ctx, args.w, args.h, args.sim, args.iters)

    ctrl = Controller()
    ctrl.RAMP = args.ramp

    shots = sorted(float(s) for s in args.shots.split(",") if s.strip())
    dt = 1.0 / 60.0
    t = 0.0
    prev_on = False
    touch = (0.5, 0.26)
    prev_touch = touch
    n = 0
    shot_i = 0

    while t < args.seconds:
        if args.tap:
            on = 0.30 <= t < 0.45
        else:
            on = t >= 0.30

        if args.drag and on:
            touch = (0.5 + 0.22 * math.sin(t * 1.1), 0.26 + 0.10 * math.sin(t * 0.7))

        if on and not prev_on:
            ctrl.touch_down()
            prev_touch = touch
        prev_on = on

        ctrl.update(dt, on, touch)
        if args.fixed >= 0.0 and not ctrl.exploding:
            ctrl.intensity = args.fixed if on else 0.0
        if ctrl.reset_requested:
            fire.reset()

        tv = ((touch[0] - prev_touch[0]) / dt * 260.0,
              (touch[1] - prev_touch[1]) / dt * 260.0)
        prev_touch = touch

        shake, rot = ctrl.shake(t)
        P = params(ctrl.intensity, on, touch, tv,
                   ctrl.blast(), shake, ctrl.flash(), ctrl.shock(),
                   blast_pos=ctrl.blast_pos, blast_heat=ctrl.blast_heat(),
                   blast_radius=ctrl.blast_radius(), strike=ctrl.strike01(),
                   blast_heat_radius=ctrl.blast_heat_radius(),
                   expl_t=(ctrl.et if ctrl.exploding else -1.0))
        P["uShakeRot"] = rot
        P["uZoom"] = 0.02 * ctrl.intensity ** 2 + (0.05 * math.exp(-ctrl.et / 0.3)
                                                   if ctrl.exploding else 0.0)
        fire.step(dt, P)

        if shot_i < len(shots) and t >= shots[shot_i]:
            img = fire.render(P)
            name = os.path.join(args.out, "t%05.2f_i%.2f.png" % (t, ctrl.intensity))
            img.save(name)
            print("saved", name)
            shot_i += 1

        t += dt
        n += 1

    print("frames simulated:", n)


if __name__ == "__main__":
    main()
