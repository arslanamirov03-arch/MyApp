# Spider House

A giant spider loose in a dark house, for Android. Walk, run, jump and climb
every wall and ceiling in an eleven-room house at dusk.

Built with **Godot 4.4.1** (Forward+ renderer, Jolt physics), GDScript only —
no native extensions, so the APK is repacked from the stock export template and
needs no NDK.

## What is in here

| | |
|---|---|
| House | 11 rooms over two floors plus an attic, generated procedurally: walls with door and window openings, stairs, panelling, a brick fireplace, kitchen counters, a bathroom |
| Spider | Procedurally built body and eight IK legs. No animation clips exist anywhere in the project |
| Lighting | Evening: cold moonlight raking through the windows, warm practical lamps, a flickering fire, volumetric fog, SSAO/SSIL, real-time shadows |
| Controls | Floating left thumbstick, right-side drag to look, RUN / JUMP / BITE buttons; keyboard and mouse also work for desktop testing |
| Physics | Jolt. Crates, bottles, vases and suitcases are rigid bodies the spider can knock over |

## How the spider moves

There is no climb button and no climb animation. The body is a *surface walker*:
it carries its own up vector, which is the normal of whatever it is standing on.
Walking into a wall rolls that vector onto the wall, so floors, walls and
ceilings are all just surfaces.

Each of the eight legs is an independent two-bone IK chain with its own step
state machine. A leg is told where its foot ought to be standing; it decides for
itself when that is far enough away to be worth a step, then swings the foot
along an arc. Steps alternate in the tetrapod pattern real spiders use (legs
L1/R2/L3/R4 together, then the other four), with a per-leg timing jitter so the
sets never land in perfect unison. The body then rides on the plane through the
eight feet, which is where the pitch and roll over stairs and door sills comes
from, and the abdomen trails on a spring. Because the joint angles are solved
rather than authored, the legs bend correctly on a staircase, a wall and a
ceiling without any extra cases.

## Building

```bash
python3 tools/fetch_assets.py    # ~264 MB of CC0 assets from polyhaven.com
python3 tools/gen_audio.py       # synthesises the sound effects
python3 tools/gen_icon.py        # draws the app icon
godot --headless --editor --quit --path .          # import
godot --headless --path . --export-release "Android" ../build/SpiderHouse.apk
```

`tools/build.sh` runs all of it. Exporting needs the Android SDK
(build-tools for `apksigner`) and a keystore configured in
`export_presets.cfg`; no NDK and no Gradle build are required.

## Testing

`tools/probe.tscn` is a headless physics probe. It drives the spider through the
real input path — HUD to game script to spider — and asserts on what actually
happened: that it settles at ride height, walks, rolls onto a wall, ends up
hanging under the ceiling, runs faster than it walks, jumps and lands, climbs the
staircase, and never over-extends a leg.

```bash
godot --headless --path . tools/probe.tscn   # exit code 0 = all checks passed
```

It is worth running after any change to movement: every one of those checks
exists because it caught a real bug (the spider spawning inside a coffee table's
collision mesh, stair treads passing under the obstacle probe, the ride-height
spring cancelling jumps on the following frame, `Vector3.slerp` building a
degenerate rotation axis on flat ground).

`tools/shots.tscn` renders fixed viewpoints to PNG for eyeballing the lighting,
and `tools/trace.tscn` prints the movement state frame by frame.

## Assets

Textures and models are CC0 from [Poly Haven](https://polyhaven.com) and are not
committed — `tools/fetch_assets.py` downloads them. Sound effects and the icon
are generated from the scripts in `tools/`, so nothing third-party is vendored
into the repository.
