# Cosmic Airxonix

Xonix/Airxonix in deep space: fly out into the void, close a loop, and the Milky
Way blazes up inside the territory you took. Instead of balls there are planets —
the Sun, the Moon, all eight planets and a few exoplanets — and instead of a
helicopter, an alien starship. The field is **100 × 50 = 5000 cells, five times
the classic 40 × 25**, with a camera that follows the ship and a minimap that
keeps the whole field readable.

Built for tablets first: a floating touch stick, a boost pad, and a full
keyboard/gamepad path on desktop.

**Play it: https://clean-spire-614.higgsfield.gg/**

## Play

```bash
python3 -m http.server 8000     # ES modules do not load over file://
# open http://localhost:8000/
```

Add `?dev=1` for the fps / frame-time / draw-count overlay.

## Controls

| | |
|---|---|
| **Touch** | Hold and drag anywhere — a stick appears under your thumb; flick to turn. Release and the ship keeps its heading. Bottom-right pad is boost. |
| **Keyboard** | Arrows or WASD, `Space` boost, `P`/`Esc` pause, `Enter` confirm. Bound to physical key codes, so non-Latin layouts work. |
| **Gamepad** | D-pad or left stick, `A` boost, `Start` pause. |

## Rules

- Leaving claimed ground starts a glowing trail. Returning to claimed ground
  closes it, and everything the planets can no longer reach becomes yours.
- A planet touching your trail costs a ship. So does crossing your own line.
- Hunter moons patrol the ground you already claimed and chase the ship.
- Claim 75 % of the field to clear a level. Captures of 260+ cells score double.
- An extra ship every 30 000 points.

## Layout

```
index.html          the game page
game.js             engine: fixed-timestep simulation, renderer, input
strings.js          every player-visible string (ru / en)
logic.js            solo rules stub required by the publishing platform
assets/             milkyway.jpg, ship.png, icon.png, cover.jpg, planets/*.png
design/             assets.csv (the asset manifest) and plan.md (the design record)
tools/              build_assets.py (raw art -> game sprites), verify.mjs (browser tests)
```

## Assets

Art generated with Higgsfield (`nano_banana_2`) from one frozen style formula —
see `design/plan.md`. `tools/build_assets.py` chroma-keys the sprite sheets, cuts
the 14 celestial bodies out of the 4 × 4 contact sheet, patches a stray starship
the model painted into the background, and composes the icon and cover. The void
layer (parallax stars, drifting nebula) is drawn procedurally in code from the
same formula, so it tiles seamlessly and animates.

## Verification

```bash
node tools/verify.mjs [--shots]
```

Serves the folder and drives a real Chromium through: the reference route played
**touch-only** on a tablet viewport, phone-portrait layout, keyboard-only desktop
play by physical key code, the frame and simulation budgets, determinism of the
seeded level setup, and the full life cycle (ship lost → respawn → level cleared →
game over → restart). 30 checks, all green.

Headless Chromium rasterises on the CPU, so the fps number it reports is a floor,
not what a real tablet sees; the renderer also drops background layers on its own
if a device cannot hold the budget.
