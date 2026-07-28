# Cosmic Airxonix — design record

## Style formula (frozen, byte-identical in every asset prompt)

> Photoreal cinematic deep-sky astrophotography with fine film grain. Hard-edged
> silhouettes, no cartoon outlines, crisp rim light separating every object from
> the void. Void in near-black indigo; Milky Way band in luminous cream-gold and
> magenta-violet nebula; player craft in pale titanium white with cyan engine
> glow contrasting the warm starfield; hazard planets ringed with hot amber
> light. Awe-struck silent deep-space mood, one hard distant star as key light.
> High contrast between game elements and backgrounds, clean readable
> silhouettes, consistent top-down perspective across all assets.

Key colour for cut-out sprites: `#00FF00` (the palette carries magenta-violet, so
magenta would have been unkeyable).

## Profile

| Axis | Choice |
|---|---|
| Time | real-time, fixed 60 Hz simulation |
| Space | discrete 100 × 50 grid, continuous movement over it |
| Agency | one hero — the craft |
| Conflict | vs system (planets, hunter moons) |
| Content | procedural levels from a seeded RNG |
| Outcome | win/lose per level, endless level ladder |
| Players | solo |
| Session | 5–30 minutes |
| Engagement | execution first, accumulation second |

**Delivery context:** desktop + tablet/phone browsers + gamepad, all first class.
Keyboard bound to physical key codes. Every player-visible string in
`strings.js` (ru/en), switchable in-game.

## Experience formula

The player feels *daring greed* because the game constantly tempts them to carve
one more deep loop into a living cosmos while planets close in on the fragile
line they are drawing.

## Verbs

- **FLY** — grid-locked movement; turns commit at cell centres, so the line is
  always exact.
- **CUT** — leave a trail across the void. The trail is the risk: anything that
  touches it costs a ship.
- **CLAIM** — return to solid ground and everything the planets cannot reach
  becomes yours, revealing the Milky Way underneath.
- **BOOST** — spend the energy ring for 1.6× speed. Refills slowly, plus a chunk
  on every capture, so greed funds greed.

## Agency metrics (frozen before content)

| Metric | Value |
|---|---|
| Cell | 24 world px |
| Field | 100 × 50 = 5000 cells — **5× the classic 40 × 25** |
| Border | 2 cells |
| Ship speed | 7.6 cells/s, 12.2 boosted |
| Ship hitbox | r = 0.34 cell (smaller than its sprite; planets use their honest radius) |
| Planet speed | 3.5 cells/s at level 1, +5.5 %/level, divided by mass |
| Planet radius | 0.78 (Mercury) … 2.30 (the Sun) cells |
| Target | 75 % of the free field |
| Ships | 3, +1 every 30 000 points |
| Camera | shows 30 cells wide, exponential follow, 3.2-cell look-ahead |

## Level ladder (teaching loop)

| Level | Bodies | Hunters | Pattern taught |
|---|---|---|---|
| 1 | 5 planets | — | safety: cut and close |
| 2 | 6 | — | mild price: the void is busier |
| 3 | 7 | 1 moon | combine: ground is no longer safe |
| 4–5 | 8–9 | 1 | exam: deep loops under pressure |
| 6+ | up to 12 | up to 3 | escalation: speed and density |

## Balance

- Claiming shrinks the void, so the same planets get denser — difficulty rises
  from the player's own progress, no separate ramp needed.
- A capture ≥ 260 cells doubles its points: the greedy line is the paying line.
- Dying clears the trail but never the claimed ground, so a bad run is a setback,
  not a reset.
- Respawn lands on the nearest claimed cell to the loss — the cost of an error is
  repeating the interesting part, not walking back across the field.

## Thresholds

| Budget | Value | Where measured |
|---|---|---|
| Simulation step | < 3 ms of the 16.6 ms frame | `tools/verify.mjs`, dev overlay `?dev=1` |
| Frame rate | 60 fps target; the renderer sheds background layers automatically below 45 fps and restores them above 58 | adaptive `quality` level in `game.js` |
| Allocations in the loop | zero — particle pool, cached gradients, typed-array grid | code review + overlay |
| Draw calls | ≈ 12 per frame (background tiles, one claimed layer, one edge glow, ≤ 12 bodies, batched particles) | dev overlay |
| Asset weight | 2.6 MB total | `tools/build_assets.py` |

## Information map

Everything is visible: the field, the bodies, the trail. The minimap carries the
whole 100 × 50 field because the camera cannot — it is what makes a 5× field
readable. The claim bar always shows the current goal, so a returning player sees
the next step immediately.

## Known limits

Perceptual polish — animation feel, music, the exact tuning of speeds — wants
human hands and eyes. Levels are procedural, so there is no authored difficulty
curve beyond the ladder above. The planets do not rotate: the sprites are lit by
a single fixed key light, and spinning them would drag the terminator around with
them.
