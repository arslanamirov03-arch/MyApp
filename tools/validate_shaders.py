#!/usr/bin/env python3
"""Assembles each shader exactly the way GLUtil.java does and runs glslangValidator."""
import os
import subprocess
import sys
import tempfile

SH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                  "app/src/main/assets/shaders")
SH = os.path.normpath(SH)

PROGRAMS = [
    ("fullscreen.vert", "curl.frag"),
    ("fullscreen.vert", "velocity.frag"),
    ("fullscreen.vert", "divergence.frag"),
    ("fullscreen.vert", "pressure.frag"),
    ("fullscreen.vert", "project.frag"),
    ("fullscreen.vert", "advect.frag"),
    ("fullscreen.vert", "fields.frag"),
    ("fullscreen.vert", "render_fire.frag"),
    ("fullscreen.vert", "particles_update.frag"),
    ("particles.vert", "particles.frag"),
    ("fullscreen.vert", "bloom_prefilter.frag"),
    ("fullscreen.vert", "bloom_down.frag"),
    ("fullscreen.vert", "bloom_up.frag"),
    ("fullscreen.vert", "composite.frag"),
    ("fullscreen.vert", "storm_sky.frag"),
    ("bolt.vert", "bolt.frag"),
]


def read(name):
    with open(os.path.join(SH, name), encoding="utf-8") as f:
        return f.read()


def main():
    common = read("common.glsl")
    failures = 0
    for lowprec in (0, 1):
        head = "#version 300 es\n#define LOWPREC %d\n" % lowprec + common + "\n"
        seen = set()
        for vert, frag in PROGRAMS:
            for name in (vert, frag):
                key = (name, lowprec)
                if key in seen:
                    continue
                seen.add(key)
                stage = "vert" if name.endswith(".vert") else "frag"
                src = head + read(name)
                with tempfile.NamedTemporaryFile("w", suffix="." + stage,
                                                 delete=False, encoding="utf-8") as tf:
                    tf.write(src)
                    path = tf.name
                r = subprocess.run(["glslangValidator", path],
                                   capture_output=True, text=True)
                os.unlink(path)
                tag = "%s [LOWPREC=%d]" % (name, lowprec)
                if r.returncode != 0:
                    failures += 1
                    print("FAIL %s\n%s%s" % (tag, r.stdout, r.stderr))
                else:
                    print("ok   %s" % tag)
    print("\n%d failure(s)" % failures)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
