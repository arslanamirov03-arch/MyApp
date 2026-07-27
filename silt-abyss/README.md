# SILTBOUND — The Abyss

A monochrome underwater puzzle-descent for Android: the possession mechanic of
*SILT* wearing the silhouette-and-haze atmosphere of *LIMBO*. Thirty
hand-authored levels across six chapters, three boss fights, and a hint system
for when a room refuses to open.

This is an original homage, not a port or a clone of either game. No assets from
either are used — there are no assets at all.

## Install

Download **[Siltbound.apk](Siltbound.apk)** on your phone and open it. Android
will ask you to allow installs from your browser or file manager; that is normal
for an APK that did not come from the Play Store.

Requires Android 7.0 (API 24) or newer. Landscape. No permissions, no network,
no ads, no in-app purchases.

## How it plays

You are a diver at the bottom of the sea, too slow and too soft to do anything
useful. What you *can* do is leave your body and wear something else.

| Control | Action |
| --- | --- |
| Left thumb, anywhere | Floating stick — drag to swim |
| Tap a creature | Send your spirit into it (range is limited, so hop creature to creature) |
| **BODY** | Recall your spirit — works from any distance |
| Action button | The ability of whatever you are currently wearing |
| **?** | Appears when you have been stuck a while — up to three escalating hints |

Your **body** has to reach the exit, not the creature you are wearing. That is
the whole design: send your spirit off to open the way, bring it home, then swim
through what you opened.

### The seven bodies

| | Ability |
| --- | --- |
| **Biter** | Cuts kelp, trips shell levers |
| **Rammer** | Dash — shatters brittle rock, stuns hunters, the only thing that hurts a boss |
| **Glower** | Wide lantern; a flare lights the room and scares hunters off |
| **Spark** | Discharge — powers electric nodes, stuns |
| **Heavy** | Walks the seabed, cannot swim or climb; the only thing that holds a pressure plate |
| **Puffer** | Inflates and rises; currents stop mattering to it |
| **Leech** | Hunters do not register it at all; latches onto big movers and rides |

Gates and switches are matched by the dots printed on them: one dot to one dot.
Levers stay open. Plates and nodes time out, so several levels are really a
question of *where you leave your body* before you go and trip them.

## Everything is procedural

The APK is around 70 KB because it contains no bitmaps and no audio files.
Creatures are built from an animated spine with a tapered outline; rock is baked
into a single `Path` at load; the ambient drone and every sound effect are
synthesised into PCM buffers at startup. Light is a real pass — a darkness layer
with holes punched through it by each light source — which is why the dark
chapters play the way they do.

## Building

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease      # -> app/build/outputs/apk/release/
./gradlew testReleaseUnitTest  # 19 tests
```

The release build is signed with the standard Android debug key so it installs
without extra steps. That is fine for sideloading and wrong for the Play Store —
publishing would need a real upload key.

## Levels are generated, and proven solvable

Levels are authored as ASCII in [`tools/levels.py`](tools/levels.py) and
compiled into `app/src/main/java/com/abyss/silt/level/Levels.java`:

```bash
python3 tools/levels.py --check   # validate only
python3 tools/levels.py           # validate and regenerate Levels.java
```

Nothing ships unless it passes, and the checks are the reason to keep the script:

- structural sanity — one spawn, one exit, uniform widths, sealed border;
- every gate channel has a switch, and some creature in the level can work it;
- **the exit is reachable** — a fixpoint that repeatedly floods the level,
  unlocks whichever creatures that flood can reach, opens the channels those
  creatures can satisfy, and floods again;
- **the exit is *not* reachable if you remove a creature the level depends on** —
  which is what catches a barrier the player could simply swim around;
- a separate walk-and-fall reachability model for the Heavy, since it cannot
  climb and a plate it cannot reach is an unwinnable level.

Barriers are painted with `bars=[(column, char)]` rather than typed into the
grid, so a wall is always full height, and the generator errors out if a barrier
would overwrite something already in that column.

## Tests

`app/src/test/` runs on Robolectric in native graphics mode, so the renderer is
exercised through real Skia rather than stubs:

- every level loads, simulates 600 frames of random input with every button
  being mashed, and draws — the test that catches a crash before the phone does;
- possession respects its range, recall does not;
- a killed creature respawns, so a level cannot deadlock;
- reaching the exit completes a level, and a boss arena's exit stays shut until
  the boss is dead;
- levers, nodes and plates actually open the gates on their channel.

`ScreenshotTest` renders frames to `app/build/screenshots/` for reviewing the
art direction without a device.
