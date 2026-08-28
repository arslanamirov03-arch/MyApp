# Spider House

A giant spider loose in a palace, for Android. Walk, run, jump and climb every
wall, column, roof and lamp post — inside a twelve-room palace at dusk and the
formal garden behind it.

Built with **Godot 4.4.1** (Forward+ renderer, Jolt physics), GDScript only —
no native extensions, so the APK is repacked from the stock export template and
needs no NDK.

## What is in here

| | |
|---|---|
| Palace | 60 x 40 m, twelve rooms over two storeys with a walkable roof terrace and a tower. Ballroom, double-height grand hall, throne room, gallery, library, banqueting hall, kitchen; state bedroom, music room, upper gallery, study, guest hall. 7 m ceilings, colonnades, a grand staircase, arched openings 4.2 m wide — and no door leaves anywhere |
| Garden | A 72 x 46 m parterre behind the palace: central axis, fountain, hedge parterres, trees, lit lanterns, statuary, benches and a gated wall. Repeated planting is drawn through MultiMesh, so hundreds of hedge blocks and flowers cost one draw call each |
| Spider | Procedurally built body and eight IK legs. No animation clips exist anywhere in the project |
| Lighting | Daylight under a real 4K sky panorama — blue with cloud — which also lights the whole scene, plus practical lamps indoors. **Nothing casts a shadow anywhere**: shadow maps were the largest cost on a phone, and SSAO does the grounding instead |
| Ground | A continuous 204 x 204 m field under everything, closed in by a treeline, so there is no direction you can walk in and find a hole |
| Controls | Floating left thumbstick, right-side drag to look, FAST / RUN / JUMP buttons. Three gaits: a slow default made for looking around, a fast walk, and a run |
| Physics | Jolt. Crates, bottles, vases and suitcases are rigid bodies the spider can knock over |

## How the spider moves

There is no climb button and no climb animation. The body is a *surface walker*:
it carries its own up vector, which is the normal of whatever it is standing on.
Walking into a wall rolls that vector onto the wall, so floors, walls and
ceilings are all just surfaces.

Anything in the way is either something to step onto or something to climb: the
step probe is asked first, and whatever is too tall to step onto gets climbed
instead. With nothing underfoot at all it reaches out instead of dropping and
catches any surface within 1.5 m, which is how it crosses from one wall to the
one facing it and goes out through a window onto the outside. And if it ever
does end up on its back, it tells that apart from hanging under a ceiling by
what is underneath in world terms, and pushes itself back over. That one rule is what makes lamp posts, plinths, columns, tree trunks,
statues, the outside of the building and the tower all climbable with no special
cases.

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
