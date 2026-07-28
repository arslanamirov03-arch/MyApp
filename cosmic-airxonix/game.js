/* Cosmic Airxonix — claim the galaxy.
 *
 * Simulation: fixed 60 Hz timestep, seeded RNG, logic separate from rendering.
 * Input arrives as one command object per step from keyboard (physical key
 * codes), touch (floating stick + boost pad) and gamepad.
 *
 * Two random streams on purpose: `rnd` is the seeded simulation stream (level
 * layout, spawns) and `vrnd` is the cosmetic one (sparks, shake). Mixing them
 * would make replays diverge on a different frame rate.
 */

import { t, setLang, getLang, detectLang, LANGS } from "./strings.js";

/* ------------------------------------------------------------------ config
 * Agency metrics — frozen before content (game-design-system.md §5.3).
 */
const CELL = 24;
const GRID_W = 100, GRID_H = 50;            // 5000 cells = 5x the classic 40x25 field
const WORLD_W = GRID_W * CELL, WORLD_H = GRID_H * CELL;
const TOTAL_CELLS = GRID_W * GRID_H;
const BORDER = 2;                           // claimed frame thickness

const SPACE = 0, CLAIMED = 1, TRAIL = 2;

const TARGET = 0.75;                        // share of the free field that clears a level
const START_LIVES = 3;
const PLAYER_SPEED = 7.6;                   // cells / second
const BOOST_MUL = 1.6;
const BOOST_DRAIN = 0.5, BOOST_REFILL = 0.14, BOOST_CAPTURE_GAIN = 0.3;
const PLAYER_HIT_R = 0.34;                  // player hitbox smaller than its sprite
const INVULN_TIME = 1.6;
const DEATH_TIME = 1.15;
const EXTRA_LIFE_EVERY = 30000;
const BIG_CAPTURE = 260;                    // cells in one bite that count as greedy

const VIEW_CELLS = 30;                      // horizontal cells the camera aims to show
const CAM_LAG = 7.0, CAM_LOOKAHEAD = 3.2;
const STEP = 1 / 60;
const DPR_CAP = 1.5;

const UP = 1, DOWN = 2, LEFT = 3, RIGHT = 4;
const DX = [0, 0, 0, -1, 1], DY = [0, -1, 1, 0, 0];
const OPPOSITE = [0, DOWN, UP, RIGHT, LEFT];

/* Bodies in the order levels introduce them. r is the radius in cells; heavier
 * bodies drift slower, so the field always mixes tempos. */
const BODIES = [
  { id: "earth", r: 1.10 }, { id: "mars", r: 0.95 }, { id: "venus", r: 1.10 },
  { id: "jupiter", r: 2.00 }, { id: "neptune", r: 1.35 }, { id: "saturn", r: 1.75 },
  { id: "uranus", r: 1.35 }, { id: "sun", r: 2.30 }, { id: "lava", r: 1.00 },
  { id: "ocean", r: 1.15 }, { id: "aurora", r: 1.50 }, { id: "mercury", r: 0.78 },
];
const HUNTERS = [{ id: "moon", r: 0.80 }, { id: "dune", r: 0.85 }, { id: "mercury", r: 0.78 }];

const ART = [
  "sun", "mercury", "venus", "earth", "moon", "mars", "jupiter",
  "saturn", "uranus", "neptune", "lava", "aurora", "ocean", "dune",
];

/* ---------------------------------------------------------------- helpers */
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const lerp = (a, b, k) => a + (b - a) * k;
const vrnd = Math.random;                   // cosmetic stream

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let x = Math.imul(a ^ (a >>> 15), 1 | a);
    x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x;
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
  };
}
let rnd = mulberry32(1);                    // seeded simulation stream

/* ------------------------------------------------------------------ audio */
const Audio = {
  ctx: null, master: null, on: true,
  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    this.ctx = new AC();
    this.master = this.ctx.createGain();
    this.master.gain.value = 0.5;
    this.master.connect(this.ctx.destination);
  },
  resume() { if (this.ctx && this.ctx.state === "suspended") this.ctx.resume(); },
  tone(freq, dur, type, gain, slideTo) {
    if (!this.on || !this.ctx) return;
    const c = this.ctx, now = c.currentTime;
    const o = c.createOscillator(), g = c.createGain();
    o.type = type || "sine";
    o.frequency.setValueAtTime(freq, now);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(Math.max(20, slideTo), now + dur);
    g.gain.setValueAtTime(0.0001, now);
    g.gain.exponentialRampToValueAtTime(gain || 0.2, now + Math.min(0.02, dur * 0.3));
    g.gain.exponentialRampToValueAtTime(0.0001, now + dur);
    o.connect(g); g.connect(this.master);
    o.start(now); o.stop(now + dur + 0.02);
  },
  noise(dur, gain, freq) {
    if (!this.on || !this.ctx) return;
    const c = this.ctx, now = c.currentTime;
    const n = Math.floor(c.sampleRate * dur);
    const buf = c.createBuffer(1, n, c.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / n);
    const s = c.createBufferSource(); s.buffer = buf;
    const f = c.createBiquadFilter(); f.type = "lowpass"; f.frequency.value = freq || 900;
    const g = c.createGain(); g.gain.value = gain || 0.25;
    s.connect(f); f.connect(g); g.connect(this.master);
    s.start(now);
  },
  bounce(r) { this.tone(90 + 180 / Math.max(0.6, r), 0.09, "sine", 0.11); },
  cut() { this.tone(660, 0.04, "square", 0.035); },
  capture(big) {
    const base = big ? 392 : 330;
    for (let i = 0; i < 4; i++) {
      setTimeout(() => this.tone(base * Math.pow(1.26, i), 0.16, "triangle", 0.16), i * 55);
    }
  },
  death() { this.noise(0.7, 0.4, 700); this.tone(220, 0.8, "sawtooth", 0.22, 40); },
  levelup() {
    [0, 1, 2, 4].forEach((k, i) => setTimeout(() => this.tone(330 * Math.pow(1.2, k), 0.3, "triangle", 0.18), i * 90));
  },
  life() {
    for (let i = 0; i < 3; i++) setTimeout(() => this.tone(523 * Math.pow(1.33, i), 0.2, "sine", 0.2), i * 70);
  },
};

/* ----------------------------------------------------------------- canvas */
const canvas = document.getElementById("c");
const ctx = canvas.getContext("2d", { alpha: false });
let viewW = 0, viewH = 0, dpr = 1, scale = 1;
const coarsePointer = window.matchMedia && window.matchMedia("(pointer: coarse)").matches;

/* Cached paint objects: creating a gradient per frame allocates, and the
 * performance law forbids allocations inside the loop. Rebuilt on resize. */
let voidGradient = null, hudGradient = null, plumeGradient = null;
let quality = 2;                            // 2 full, 1 reduced, 0 minimal

function rebuildPaints() {
  voidGradient = ctx.createLinearGradient(0, 0, 0, viewH);
  voidGradient.addColorStop(0, "#04050d");
  voidGradient.addColorStop(1, "#080617");
  hudGradient = ctx.createLinearGradient(0, 0, viewW, 0);
  hudGradient.addColorStop(0, "#4fc3ff");
  hudGradient.addColorStop(1, "#b98cff");
  plumeGradient = ctx.createLinearGradient(0, CELL * 0.5, 0, CELL * 4.6);
  plumeGradient.addColorStop(0, "rgba(190,250,255,0.85)");
  plumeGradient.addColorStop(0.4, "rgba(60,190,255,0.45)");
  plumeGradient.addColorStop(1, "rgba(60,120,255,0)");
}

function resize() {
  dpr = Math.min(window.devicePixelRatio || 1, DPR_CAP);
  viewW = window.innerWidth; viewH = window.innerHeight;
  canvas.width = Math.round(viewW * dpr);
  canvas.height = Math.round(viewH * dpr);
  canvas.style.width = viewW + "px";
  canvas.style.height = viewH + "px";
  scale = viewW / (VIEW_CELLS * CELL);
  scale = Math.min(scale, viewH / (15 * CELL));
  scale = Math.max(scale, viewW / WORLD_W, viewH / WORLD_H);   // never show past the field
  rebuildPaints();
  layoutUI();
}

/* ----------------------------------------------------------------- assets */
const IMG = {};
const HALO = {};
let assetsLoaded = 0, assetsTotal = 0;

function loadImage(key, src) {
  assetsTotal++;
  return new Promise((resolve) => {
    const im = new Image();
    im.onload = () => { IMG[key] = im; assetsLoaded++; resolve(im); };
    im.onerror = () => { assetsLoaded++; resolve(null); };
    im.src = src;
  });
}

function loadAll() {
  const jobs = [loadImage("milkyway", "./assets/milkyway.jpg"), loadImage("ship", "./assets/ship.png")];
  for (const id of ART) jobs.push(loadImage(id, "./assets/planets/" + id + ".png"));
  return Promise.all(jobs);
}

function buildHalos() {
  for (const id of ART) {
    const src = IMG[id];
    if (!src) continue;
    const s = 128, pad = 26;
    const cv = document.createElement("canvas");
    cv.width = cv.height = s + pad * 2;
    const c2 = cv.getContext("2d");
    try { c2.filter = "blur(14px)"; } catch (e) { /* no filter support: flat halo */ }
    c2.drawImage(src, pad, pad, s, s);
    HALO[id] = cv;
  }
}

/* ------------------------------------------------- pre-rendered void layer */
const starLayers = [];
let nebulaCv = null;

const TILE = 512;

/** Draws one soft blob nine times so the tile wraps without a visible seam. */
function wrappedBlob(c2, x, y, radius, stops) {
  for (let oy = -1; oy <= 1; oy++) {
    for (let ox = -1; ox <= 1; ox++) {
      const bx = x + ox * TILE, by = y + oy * TILE;
      if (bx + radius < 0 || bx - radius > TILE || by + radius < 0 || by - radius > TILE) continue;
      const g = c2.createRadialGradient(bx, by, 0, bx, by, radius);
      for (const s of stops) g.addColorStop(s[0], s[1]);
      c2.fillStyle = g;
      c2.fillRect(bx - radius, by - radius, radius * 2, radius * 2);
    }
  }
}

function buildVoid() {
  const sr = mulberry32(0xc051c0de);
  const specs = [{ n: 220, max: 1.0, a: 0.6 }, { n: 120, max: 1.5, a: 0.85 }, { n: 46, max: 2.2, a: 1 }];
  for (const sp of specs) {
    const cv = document.createElement("canvas");
    cv.width = cv.height = TILE;
    const c2 = cv.getContext("2d");
    for (let i = 0; i < sp.n; i++) {
      const x = sr() * TILE, y = sr() * TILE;
      const r = 0.35 + sr() * sp.max;
      const warm = sr();
      const col = warm > 0.82 ? "255,214,170" : warm > 0.66 ? "190,205,255" : "255,255,255";
      wrappedBlob(c2, x, y, r * 2.6, [
        [0, "rgba(" + col + "," + sp.a + ")"],
        [0.22, "rgba(" + col + "," + sp.a * 0.5 + ")"],
        [1, "rgba(" + col + ",0)"],
      ]);
    }
    starLayers.push(cv);
  }
  nebulaCv = document.createElement("canvas");
  nebulaCv.width = nebulaCv.height = TILE;
  const nc = nebulaCv.getContext("2d");
  const blobs = [["120,60,190", 0.20], ["30,90,190", 0.16], ["190,60,120", 0.13], ["40,140,170", 0.14]];
  for (let i = 0; i < 9; i++) {
    const b = blobs[i % blobs.length];
    wrappedBlob(nc, sr() * TILE, sr() * TILE, 90 + sr() * 180, [
      [0, "rgba(" + b[0] + "," + b[1] + ")"],
      [1, "rgba(" + b[0] + ",0)"],
    ]);
  }
}

/* ------------------------------------------------------------ world state */
const grid = new Uint8Array(TOTAL_CELLS);
const visited = new Uint8Array(TOTAL_CELLS);
const fillStack = new Int32Array(TOTAL_CELLS);
const trailCells = new Int32Array(TOTAL_CELLS);
let trailLen = 0;

let maskCv, maskCtx, maskImg;
let edgeCv, edgeCtx, edgeImg;
let claimCv, claimCtx;
let claimDirty = null;

const player = {
  cx: 0, cy: 0, tx: 0, ty: 0, tt: 0, dir: 0, pending: 0,
  drawing: false, dying: 0, invuln: 0, angle: 0, targetAngle: 0,
  boost: 1, boosting: false, plume: 0,
};
const enemies = [];
const cam = { x: 0, y: 0, shake: 0 };

const game = {
  state: "loading",           // loading | menu | playing | paused | clear | over
  level: 1, score: 0, lives: START_LIVES, best: 0,
  claimed: 0, freeCells: 1, progress: 0, borderCells: 0,
  time: 0, levelTime: 0, nextLife: EXTRA_LIFE_EVERY,
  clearBonus: 0, clearShips: 0,
};

const toasts = [];
function toast(msg, color) {
  toasts.push({ text: msg, color: color || "#8fe9ff", life: 2.2 });
  if (toasts.length > 3) toasts.shift();
}

/* particles — fixed pool, no allocation inside the loop */
const PMAX = 420;
const P = {
  x: new Float32Array(PMAX), y: new Float32Array(PMAX),
  vx: new Float32Array(PMAX), vy: new Float32Array(PMAX),
  life: new Float32Array(PMAX), max: new Float32Array(PMAX),
  size: new Float32Array(PMAX), col: new Uint8Array(PMAX),
};
let pHead = 0;
const P_COLORS = ["255,255,255", "140,235,255", "255,190,120", "200,150,255"];

function spawnParticle(x, y, vx, vy, life, size, col) {
  const i = pHead; pHead = (pHead + 1) % PMAX;
  P.x[i] = x; P.y[i] = y; P.vx[i] = vx; P.vy[i] = vy;
  P.life[i] = life; P.max[i] = life; P.size[i] = size; P.col[i] = col;
}

function burst(x, y, n, power, col) {
  for (let i = 0; i < n; i++) {
    const a = vrnd() * Math.PI * 2, s = power * (0.25 + vrnd() * 0.95);
    spawnParticle(x, y, Math.cos(a) * s, Math.sin(a) * s, 0.4 + vrnd() * 0.7,
      1.2 + vrnd() * 2.6, col === undefined ? (vrnd() < 0.5 ? 0 : 1) : col);
  }
}

/* -------------------------------------------------------------- the field */
const idx = (x, y) => y * GRID_W + x;

function buildMaskCanvases() {
  maskCv = document.createElement("canvas");
  maskCv.width = GRID_W; maskCv.height = GRID_H;
  maskCtx = maskCv.getContext("2d");
  maskImg = maskCtx.createImageData(GRID_W, GRID_H);

  edgeCv = document.createElement("canvas");
  edgeCv.width = GRID_W; edgeCv.height = GRID_H;
  edgeCtx = edgeCv.getContext("2d");
  edgeImg = edgeCtx.createImageData(GRID_W, GRID_H);

  claimCv = document.createElement("canvas");
  claimCv.width = WORLD_W; claimCv.height = WORLD_H;
  claimCtx = claimCv.getContext("2d");
}

function markDirty(x0, y0, x1, y1) {
  if (!claimDirty) claimDirty = { x0: x0, y0: y0, x1: x1, y1: y1 };
  else {
    if (x0 < claimDirty.x0) claimDirty.x0 = x0;
    if (y0 < claimDirty.y0) claimDirty.y0 = y0;
    if (x1 > claimDirty.x1) claimDirty.x1 = x1;
    if (y1 > claimDirty.y1) claimDirty.y1 = y1;
  }
}

function setClaimed(x, y) {
  grid[idx(x, y)] = CLAIMED;
  const o = (y * GRID_W + x) * 4;
  maskImg.data[o] = 255; maskImg.data[o + 1] = 255;
  maskImg.data[o + 2] = 255; maskImg.data[o + 3] = 255;
}

function refreshEdges(x0, y0, x1, y1) {
  x0 = Math.max(0, x0 - 1); y0 = Math.max(0, y0 - 1);
  x1 = Math.min(GRID_W, x1 + 1); y1 = Math.min(GRID_H, y1 + 1);
  const d = edgeImg.data;
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      const i = idx(x, y), o = i * 4;
      let edge = false;
      if (grid[i] === CLAIMED && x > 0 && y > 0 && x < GRID_W - 1 && y < GRID_H - 1) {
        edge = grid[i - 1] !== CLAIMED || grid[i + 1] !== CLAIMED ||
          grid[i - GRID_W] !== CLAIMED || grid[i + GRID_W] !== CLAIMED;
      }
      d[o] = 150; d[o + 1] = 235; d[o + 2] = 255; d[o + 3] = edge ? 255 : 0;
    }
  }
}

function flushClaimLayer() {
  if (!claimDirty) return;
  const b = claimDirty; claimDirty = null;
  maskCtx.putImageData(maskImg, 0, 0);
  refreshEdges(b.x0, b.y0, b.x1, b.y1);
  edgeCtx.putImageData(edgeImg, 0, 0);

  const px = b.x0 * CELL, py = b.y0 * CELL;
  const pw = (b.x1 - b.x0) * CELL, ph = (b.y1 - b.y0) * CELL;
  claimCtx.save();
  claimCtx.beginPath();
  claimCtx.rect(px, py, pw, ph);
  claimCtx.clip();
  claimCtx.clearRect(px, py, pw, ph);
  if (IMG.milkyway) {
    claimCtx.drawImage(IMG.milkyway, 0, 0, WORLD_W, WORLD_H);
    // claimed sky reads brighter than the void it replaced — that contrast is
    // the reward for closing a loop
    claimCtx.globalCompositeOperation = "lighter";
    claimCtx.globalAlpha = 0.30;
    claimCtx.drawImage(IMG.milkyway, 0, 0, WORLD_W, WORLD_H);
    claimCtx.globalAlpha = 1;
  } else { claimCtx.fillStyle = "#241f47"; claimCtx.fillRect(px, py, pw, ph); }
  claimCtx.globalCompositeOperation = "destination-in";
  claimCtx.imageSmoothingEnabled = false;
  claimCtx.drawImage(maskCv, 0, 0, WORLD_W, WORLD_H);
  claimCtx.globalCompositeOperation = "source-over";
  claimCtx.restore();
}

function resetField() {
  grid.fill(SPACE);
  maskImg.data.fill(0);
  edgeImg.data.fill(0);
  claimCtx.clearRect(0, 0, WORLD_W, WORLD_H);
  claimDirty = null;
  trailLen = 0;
  game.claimed = 0;
  for (let y = 0; y < GRID_H; y++) {
    for (let x = 0; x < GRID_W; x++) {
      if (x < BORDER || y < BORDER || x >= GRID_W - BORDER || y >= GRID_H - BORDER) {
        setClaimed(x, y);
        game.claimed++;
      }
    }
  }
  game.borderCells = game.claimed;
  game.freeCells = TOTAL_CELLS - game.claimed;
  game.progress = 0;
  markDirty(0, 0, GRID_W, GRID_H);
}

/* ------------------------------------------------------------ level setup */
function levelPlan(level) {
  return {
    // the field is five times the classic one, so the opening count is scaled
    // with it — two planets on this much void reads as an empty screen
    planets: Math.min(4 + level, BODIES.length),
    hunters: level >= 3 ? Math.min(1 + Math.floor((level - 3) / 3), 3) : 0,
    speed: 3.5 * (1 + 0.055 * (level - 1)),
  };
}

function freeSpotFor(r, minDistFromPlayer) {
  const pxp = playerX(), pyp = playerY();
  for (let tries = 0; tries < 400; tries++) {
    const x = BORDER + 2 + rnd() * (GRID_W - 2 * BORDER - 4);
    const y = BORDER + 2 + rnd() * (GRID_H - 2 * BORDER - 4);
    if (x - r < BORDER + 0.4 || x + r > GRID_W - BORDER - 0.4) continue;
    if (y - r < BORDER + 0.4 || y + r > GRID_H - BORDER - 0.4) continue;
    if (Math.hypot(x - pxp, y - pyp) < minDistFromPlayer) continue;
    let ok = true;
    for (const e of enemies) {
      if (Math.hypot(x - e.x, y - e.y) < e.r + r + 2) { ok = false; break; }
    }
    if (ok) return { x: x, y: y };
  }
  return { x: GRID_W / 2, y: GRID_H / 2 };
}

function startLevel(level) {
  rnd = mulberry32((0x9e3779b9 ^ Math.imul(level, 2654435761)) >>> 0);
  game.level = level;
  game.levelTime = 0;
  toasts.length = 0;
  resetField();

  player.cx = Math.floor(GRID_W / 2); player.cy = BORDER - 1;
  player.tx = player.cx; player.ty = player.cy;
  player.tt = 0; player.dir = 0; player.pending = 0;
  player.drawing = false; player.dying = 0; player.invuln = INVULN_TIME;
  player.angle = Math.PI; player.targetAngle = Math.PI;
  player.boost = 1; player.boosting = false; player.plume = 0;

  enemies.length = 0;
  const plan = levelPlan(level);
  for (let i = 0; i < plan.planets; i++) {
    const b = BODIES[i % BODIES.length];
    const spot = freeSpotFor(b.r, 14);
    const sp = plan.speed / (0.72 + 0.30 * b.r);
    const quad = Math.floor(rnd() * 4);
    const a = Math.PI / 6 + rnd() * (Math.PI / 6) + quad * (Math.PI / 2);
    enemies.push({
      kind: "planet", art: b.id, r: b.r, x: spot.x, y: spot.y, bounceCd: 0,
      vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, speed: sp, pulse: rnd() * 6.283,
    });
  }
  for (let i = 0; i < plan.hunters; i++) {
    const h = HUNTERS[i % HUNTERS.length];
    const sp = plan.speed * 0.62;
    const a = rnd() * Math.PI * 2;
    const along = rnd() < 0.5;
    enemies.push({
      kind: "hunter", art: h.id, r: h.r, speed: sp, pulse: rnd() * 6.283, bounceCd: 0,
      x: along ? BORDER + 0.5 + rnd() * (GRID_W - 2 * BORDER - 1) : (rnd() < 0.5 ? BORDER * 0.5 : GRID_W - BORDER * 0.5),
      y: along ? (rnd() < 0.5 ? BORDER * 0.5 : GRID_H - BORDER * 0.5) : BORDER + 0.5 + rnd() * (GRID_H - 2 * BORDER - 1),
      vx: Math.cos(a) * sp, vy: Math.sin(a) * sp,
    });
  }
  for (const e of enemies) nudgeOut(e);

  cam.x = clamp(playerX() * CELL - viewW / (2 * scale), 0, Math.max(0, WORLD_W - viewW / scale));
  cam.y = clamp(playerY() * CELL - viewH / (2 * scale), 0, Math.max(0, WORLD_H - viewH / scale));
  cam.shake = 0;
  toast(t("level") + " " + level, "#ffd9a0");
}

/* ------------------------------------------------------------- the player */
const playerX = () => lerp(player.cx + 0.5, player.tx + 0.5, player.tt);
const playerY = () => lerp(player.cy + 0.5, player.ty + 0.5, player.tt);

function setTarget(d) {
  const nx = player.cx + DX[d], ny = player.cy + DY[d];
  if (nx < 0 || ny < 0 || nx >= GRID_W || ny >= GRID_H) return false;
  player.tx = nx; player.ty = ny; player.dir = d;
  return true;
}

function angleFor(d) {
  return d === UP ? 0 : d === RIGHT ? Math.PI / 2 : d === DOWN ? Math.PI : -Math.PI / 2;
}

function updatePlayer(dt, cmd) {
  if (player.dying > 0) {
    player.dying -= dt;
    if (player.dying <= 0) respawn();
    return;
  }
  if (player.invuln > 0) player.invuln -= dt;

  const want = cmd.dir;
  if (want !== 0) {
    if (player.dir === 0) {
      if (setTarget(want)) { player.tt = 0; player.pending = 0; }
    } else if (want === OPPOSITE[player.dir]) {
      // reversing onto your own line is certain death, so it is refused while
      // drawing and instant on claimed ground
      if (!player.drawing) {
        const cx = player.cx, cy = player.cy;
        player.cx = player.tx; player.cy = player.ty;
        player.tx = cx; player.ty = cy;
        player.tt = 1 - player.tt;
        player.dir = want; player.pending = 0;
      }
    } else if (want !== player.dir) {
      player.pending = want;
    }
  }

  player.boosting = cmd.boost && player.boost > 0.02 && player.dir !== 0;
  if (player.boosting) player.boost = Math.max(0, player.boost - BOOST_DRAIN * dt);
  else player.boost = Math.min(1, player.boost + BOOST_REFILL * dt);
  const plumeTarget = player.dir === 0 ? 0.25 : (player.boosting ? 1 : 0.6);
  player.plume = lerp(player.plume, plumeTarget, 1 - Math.exp(-12 * dt));

  let move = PLAYER_SPEED * (player.boosting ? BOOST_MUL : 1) * dt;
  let guard = 8;
  while (move > 0 && player.dir !== 0 && guard-- > 0) {
    const seg = 1 - player.tt;
    if (move < seg) { player.tt += move; break; }
    move -= seg;
    player.cx = player.tx; player.cy = player.ty; player.tt = 0;
    if (arriveCell(player.cx, player.cy)) return;
    const nd = player.pending || player.dir;
    let ok = setTarget(nd);
    if (!ok && nd !== player.dir) ok = setTarget(player.dir);
    player.pending = 0;
    if (!ok) { player.dir = 0; player.tx = player.cx; player.ty = player.cy; break; }
  }

  if (player.dir !== 0) player.targetAngle = angleFor(player.dir);
  let da = player.targetAngle - player.angle;
  while (da > Math.PI) da -= Math.PI * 2;
  while (da < -Math.PI) da += Math.PI * 2;
  player.angle += da * (1 - Math.exp(-18 * dt));

  if (player.drawing && (player.boosting || vrnd() < 0.3)) {
    spawnParticle(playerX() * CELL, playerY() * CELL,
      (vrnd() - 0.5) * 22, (vrnd() - 0.5) * 22, 0.35 + vrnd() * 0.3, 1 + vrnd() * 1.6, 1);
  }
}

/** Returns true when the step must stop: the ship died or the level ended. */
function arriveCell(x, y) {
  const i = idx(x, y), v = grid[i];
  if (player.drawing) {
    if (v === TRAIL) { killPlayer(); return true; }
    if (v === SPACE) {
      grid[i] = TRAIL;
      trailCells[trailLen++] = i;
      Audio.cut();
      return false;
    }
    return capture();
  }
  if (v === SPACE) {
    player.drawing = true;
    grid[i] = TRAIL;
    trailCells[trailLen++] = i;
    Audio.cut();
  }
  return false;
}

/* -------------------------------------------------------------- capturing */
function capture() {
  player.drawing = false;
  if (trailLen === 0) return false;

  // everything the planets can still reach stays void; the rest is yours
  visited.fill(0);
  let sp = 0;
  for (const e of enemies) {
    if (e.kind !== "planet") continue;
    const ex = clamp(Math.floor(e.x), 0, GRID_W - 1), ey = clamp(Math.floor(e.y), 0, GRID_H - 1);
    for (let k = 0; k < 5; k++) {
      const sx = clamp(ex + (k === 1 ? 1 : k === 2 ? -1 : 0), 0, GRID_W - 1);
      const sy = clamp(ey + (k === 3 ? 1 : k === 4 ? -1 : 0), 0, GRID_H - 1);
      const s = idx(sx, sy);
      if (grid[s] === SPACE && !visited[s]) { visited[s] = 1; fillStack[sp++] = s; }
    }
  }
  while (sp > 0) {
    const i = fillStack[--sp];
    const x = i % GRID_W, y = (i / GRID_W) | 0;
    if (x > 0 && grid[i - 1] === SPACE && !visited[i - 1]) { visited[i - 1] = 1; fillStack[sp++] = i - 1; }
    if (x < GRID_W - 1 && grid[i + 1] === SPACE && !visited[i + 1]) { visited[i + 1] = 1; fillStack[sp++] = i + 1; }
    if (y > 0 && grid[i - GRID_W] === SPACE && !visited[i - GRID_W]) { visited[i - GRID_W] = 1; fillStack[sp++] = i - GRID_W; }
    if (y < GRID_H - 1 && grid[i + GRID_W] === SPACE && !visited[i + GRID_W]) { visited[i + GRID_W] = 1; fillStack[sp++] = i + GRID_W; }
  }

  let gained = 0;
  let bx0 = GRID_W, by0 = GRID_H, bx1 = 0, by1 = 0;
  for (let y = 0; y < GRID_H; y++) {
    for (let x = 0; x < GRID_W; x++) {
      const i = idx(x, y);
      if ((grid[i] === SPACE && !visited[i]) || grid[i] === TRAIL) {
        setClaimed(x, y);
        gained++;
        if (x < bx0) bx0 = x;
        if (y < by0) by0 = y;
        if (x + 1 > bx1) bx1 = x + 1;
        if (y + 1 > by1) by1 = y + 1;
      }
    }
  }
  trailLen = 0;
  if (gained > 0) markDirty(bx0, by0, bx1, by1);
  game.claimed += gained;
  game.progress = (game.claimed - game.borderCells) / game.freeCells;

  const big = gained >= BIG_CAPTURE;
  const points = Math.round(gained * (10 + game.level) * (big ? 2 : 1));
  addScore(points);
  player.boost = Math.min(1, player.boost + BOOST_CAPTURE_GAIN);
  if (big) toast(t("big_capture") + "  +" + points, "#ffd9a0");

  if (gained > 0) {
    burst((bx0 + bx1) / 2 * CELL, (by0 + by1) / 2 * CELL, big ? 46 : 26, big ? 210 : 140, 2);
    cam.shake = Math.min(1, cam.shake + (big ? 0.5 : 0.22));
  }
  Audio.capture(big);
  for (const e of enemies) nudgeOut(e);

  if (game.progress >= TARGET) { levelCleared(); return true; }
  return false;
}

function addScore(points) {
  game.score += points;
  while (game.score >= game.nextLife) {
    game.nextLife += EXTRA_LIFE_EVERY;
    game.lives++;
    toast(t("extra_life"), "#a0ffb0");
    Audio.life();
  }
}

function levelCleared() {
  game.clearBonus = Math.round((game.progress - TARGET) * game.freeCells * 6);
  game.clearShips = game.lives * 500;
  addScore(game.clearBonus + game.clearShips);
  game.state = "clear";
  cam.shake = 0.5;
  Audio.levelup();
  burst(playerX() * CELL, playerY() * CELL, 60, 260, 1);
}

/* ------------------------------------------------------------------ death */
function killPlayer() {
  if (player.dying > 0 || player.invuln > 0) return;
  player.dying = DEATH_TIME;
  player.drawing = false;
  player.dir = 0; player.pending = 0;
  for (let i = 0; i < trailLen; i++) {
    const c = trailCells[i];
    grid[c] = SPACE;
    if (vrnd() < 0.4) {
      spawnParticle((c % GRID_W + 0.5) * CELL, (((c / GRID_W) | 0) + 0.5) * CELL,
        (vrnd() - 0.5) * 90, (vrnd() - 0.5) * 90, 0.5 + vrnd() * 0.5, 1.5 + vrnd() * 2, 1);
    }
  }
  trailLen = 0;
  burst(playerX() * CELL, playerY() * CELL, 70, 340);
  cam.shake = 1;
  game.lives--;
  Audio.death();
}

function respawn() {
  player.dying = 0;
  if (game.lives <= 0) {
    game.state = "over";
    if (game.score > game.best) {
      game.best = game.score;
      try { localStorage.setItem("cosmic-airxonix-best", String(game.best)); } catch (e) { /* private mode */ }
    }
    return;
  }
  // nearest claimed ground to where the ship was lost: the error costs the
  // interesting part of the run, not a long walk back
  const sx = clamp(Math.round(playerX() - 0.5), 0, GRID_W - 1);
  const sy = clamp(Math.round(playerY() - 0.5), 0, GRID_H - 1);
  visited.fill(0);
  let head = 0, tail = 0;
  const start = idx(sx, sy);
  fillStack[tail++] = start;
  visited[start] = 1;
  let found = idx(BORDER - 1, BORDER - 1);
  while (head < tail) {
    const i = fillStack[head++];
    if (grid[i] === CLAIMED) { found = i; break; }
    const x = i % GRID_W, y = (i / GRID_W) | 0;
    if (x > 0 && !visited[i - 1]) { visited[i - 1] = 1; fillStack[tail++] = i - 1; }
    if (x < GRID_W - 1 && !visited[i + 1]) { visited[i + 1] = 1; fillStack[tail++] = i + 1; }
    if (y > 0 && !visited[i - GRID_W]) { visited[i - GRID_W] = 1; fillStack[tail++] = i - GRID_W; }
    if (y < GRID_H - 1 && !visited[i + GRID_W]) { visited[i + GRID_W] = 1; fillStack[tail++] = i + GRID_W; }
  }
  player.cx = found % GRID_W; player.cy = (found / GRID_W) | 0;
  player.tx = player.cx; player.ty = player.cy;
  player.tt = 0; player.dir = 0; player.pending = 0;
  player.drawing = false;
  player.invuln = INVULN_TIME;
  player.boost = Math.max(player.boost, 0.5);
}

/* ---------------------------------------------------------------- enemies */
function solidFor(e, x, y) {
  if (x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) return true;
  const v = grid[idx(x, y)];
  return e.kind === "planet" ? v === CLAIMED : v === SPACE;
}

function collideAxis(e, axis) {
  const x0 = Math.floor(e.x - e.r), x1 = Math.floor(e.x + e.r);
  const y0 = Math.floor(e.y - e.r), y1 = Math.floor(e.y + e.r);
  const r2 = e.r * e.r;
  for (let cy = y0; cy <= y1; cy++) {
    for (let cx = x0; cx <= x1; cx++) {
      if (!solidFor(e, cx, cy)) continue;
      const nx = clamp(e.x, cx, cx + 1), ny = clamp(e.y, cy, cy + 1);
      const dx = e.x - nx, dy = e.y - ny;
      if (dx * dx + dy * dy >= r2) continue;
      if (axis === 0) {
        if (e.vx > 0) e.x = cx - e.r - 1e-4; else e.x = cx + 1 + e.r + 1e-4;
        e.vx = -e.vx;
      } else {
        if (e.vy > 0) e.y = cy - e.r - 1e-4; else e.y = cy + 1 + e.r + 1e-4;
        e.vy = -e.vy;
      }
      if (e.bounceCd <= 0) { Audio.bounce(e.r); e.bounceCd = 0.08; }
      return true;
    }
  }
  return false;
}

function nudgeOut(e) {
  if (!solidFor(e, Math.floor(e.x), Math.floor(e.y))) return;
  for (let ring = 1; ring < 16; ring++) {
    for (let a = 0; a < 16; a++) {
      const ang = (a / 16) * Math.PI * 2;
      const x = e.x + Math.cos(ang) * ring, y = e.y + Math.sin(ang) * ring;
      if (x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) continue;
      if (!solidFor(e, Math.floor(x), Math.floor(y))) { e.x = x; e.y = y; return; }
    }
  }
}

function touchesState(e, want, shrink) {
  const rr = e.r * (shrink || 1);
  const x0 = Math.max(0, Math.floor(e.x - rr)), x1 = Math.min(GRID_W - 1, Math.floor(e.x + rr));
  const y0 = Math.max(0, Math.floor(e.y - rr)), y1 = Math.min(GRID_H - 1, Math.floor(e.y + rr));
  const r2 = rr * rr;
  for (let cy = y0; cy <= y1; cy++) {
    for (let cx = x0; cx <= x1; cx++) {
      if (grid[idx(cx, cy)] !== want) continue;
      const nx = clamp(e.x, cx, cx + 1), ny = clamp(e.y, cy, cy + 1);
      const dx = e.x - nx, dy = e.y - ny;
      if (dx * dx + dy * dy < r2) return true;
    }
  }
  return false;
}

function updateEnemies(dt) {
  for (const e of enemies) {
    if (e.bounceCd > 0) e.bounceCd -= dt;
    e.pulse += dt;

    if (e.kind === "hunter" && player.dying <= 0) {
      // steady pursuit: a bounded turn toward the ship, speed preserved
      const dx = playerX() - e.x, dy = playerY() - e.y;
      const d = Math.hypot(dx, dy) || 1;
      e.vx += (dx / d) * e.speed * 1.1 * dt;
      e.vy += (dy / d) * e.speed * 1.1 * dt;
      const s = Math.hypot(e.vx, e.vy) || 1;
      e.vx = (e.vx / s) * e.speed; e.vy = (e.vy / s) * e.speed;
    }

    const steps = Math.max(1, Math.ceil((Math.hypot(e.vx, e.vy) * dt) / 0.18));
    const sdt = dt / steps;
    for (let s = 0; s < steps; s++) {
      e.x += e.vx * sdt; collideAxis(e, 0);
      e.y += e.vy * sdt; collideAxis(e, 1);
      if (player.dying > 0 || game.state !== "playing") continue;
      if (trailLen > 0 && touchesState(e, TRAIL, 0.92)) { killPlayer(); return; }
      if ((e.kind === "hunter" || player.drawing) && player.invuln <= 0) {
        const ddx = e.x - playerX(), ddy = e.y - playerY();
        const rr = e.r + PLAYER_HIT_R;
        if (ddx * ddx + ddy * ddy < rr * rr) { killPlayer(); return; }
      }
    }
  }

  // planet on planet: elastic, mass by area, with positional separation
  for (let i = 0; i < enemies.length; i++) {
    const a = enemies[i];
    if (a.kind !== "planet") continue;
    for (let j = i + 1; j < enemies.length; j++) {
      const b = enemies[j];
      if (b.kind !== "planet") continue;
      const dx = b.x - a.x, dy = b.y - a.y;
      const d = Math.hypot(dx, dy);
      const min = a.r + b.r;
      if (d >= min || d === 0) continue;
      const nx = dx / d, ny = dy / d;
      const ma = a.r * a.r, mb = b.r * b.r, mt = ma + mb;
      const push = min - d;
      a.x -= nx * push * (mb / mt); a.y -= ny * push * (mb / mt);
      b.x += nx * push * (ma / mt); b.y += ny * push * (ma / mt);
      const rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
      if (rel >= 0) continue;
      const imp = (2 * rel) / mt;
      a.vx += imp * mb * nx; a.vy += imp * mb * ny;
      b.vx -= imp * ma * nx; b.vy -= imp * ma * ny;
      const sa = Math.hypot(a.vx, a.vy) || 1, sb = Math.hypot(b.vx, b.vy) || 1;
      a.vx = (a.vx / sa) * a.speed; a.vy = (a.vy / sa) * a.speed;
      b.vx = (b.vx / sb) * b.speed; b.vy = (b.vy / sb) * b.speed;
      if (a.bounceCd <= 0) { Audio.bounce(Math.max(a.r, b.r)); a.bounceCd = 0.1; }
      nudgeOut(a); nudgeOut(b);
    }
  }
}

/* -------------------------------------------------------------- particles */
function updateParticles(dt) {
  for (let i = 0; i < PMAX; i++) {
    if (P.life[i] <= 0) continue;
    P.life[i] -= dt;
    P.x[i] += P.vx[i] * dt;
    P.y[i] += P.vy[i] * dt;
    P.vx[i] *= 0.965; P.vy[i] *= 0.965;
  }
}

/* ----------------------------------------------------------------- camera */
function updateCamera(dt) {
  const k = 1 - Math.exp(-CAM_LAG * dt);
  const halfW = viewW / (2 * scale), halfH = viewH / (2 * scale);
  const aheadX = player.dir === LEFT ? -CAM_LOOKAHEAD : player.dir === RIGHT ? CAM_LOOKAHEAD : 0;
  const aheadY = player.dir === UP ? -CAM_LOOKAHEAD : player.dir === DOWN ? CAM_LOOKAHEAD : 0;
  const tx = (playerX() + aheadX) * CELL - halfW;
  const ty = (playerY() + aheadY) * CELL - halfH;
  cam.x = clamp(lerp(cam.x, tx, k), 0, Math.max(0, WORLD_W - viewW / scale));
  cam.y = clamp(lerp(cam.y, ty, k), 0, Math.max(0, WORLD_H - viewH / scale));
  if (cam.shake > 0) cam.shake = Math.max(0, cam.shake - dt * 1.6);
}

/* ------------------------------------------------------------------ input */
const CMD = { dir: 0, boost: false };
const BIND = {
  KeyW: UP, ArrowUp: UP, KeyS: DOWN, ArrowDown: DOWN,
  KeyA: LEFT, ArrowLeft: LEFT, KeyD: RIGHT, ArrowRight: RIGHT,
};
const heldKeys = [];
let keyBoost = false;
const padState = { dir: 0, boost: false, pause: false };
let padPauseWas = false;

addEventListener("keydown", (e) => {
  Audio.init(); Audio.resume();
  const d = BIND[e.code];
  if (d) {
    if (heldKeys.indexOf(d) === -1) heldKeys.push(d);
    e.preventDefault();
  } else if (e.code === "Space") {
    keyBoost = true;
    e.preventDefault();
    if (game.state !== "playing") primaryAction();
  } else if (e.code === "KeyP" || e.code === "Escape") {
    togglePause();
    e.preventDefault();
  } else if (e.code === "Enter") {
    primaryAction();
    e.preventDefault();
  }
});

addEventListener("keyup", (e) => {
  const d = BIND[e.code];
  if (d) {
    const i = heldKeys.indexOf(d);
    if (i !== -1) heldKeys.splice(i, 1);
  } else if (e.code === "Space") keyBoost = false;
});

/* touch: one floating stick anywhere + a boost pad; the thumbs are independent */
const STICK_DEAD = 16, STICK_MAX = 52, STICK_HYST = 1.25;
let stick = null;              // { id, ox, oy, x, y }
let boostPointer = null;
let touchDir = 0;
let usingTouch = coarsePointer;

function uiHit(x, y) {
  for (const b of UI.buttons) {
    if (!b.visible) continue;
    if (x >= b.x - 6 && x <= b.x + b.w + 6 && y >= b.y - 6 && y <= b.y + b.h + 6) return b;
  }
  return null;
}

function onDown(e) {
  Audio.init(); Audio.resume();
  if (e.pointerType !== "mouse") usingTouch = true;
  const x = e.clientX, y = e.clientY;
  e.preventDefault();

  const b = uiHit(x, y);
  if (b) { b.action(); return; }

  if (game.state === "menu" || game.state === "clear") { primaryAction(); return; }
  if (game.state === "paused") { togglePause(); return; }
  if (game.state !== "playing") return;

  const p = UI.boostPad;
  if (p && boostPointer === null && Math.hypot(x - p.cx, y - p.cy) < p.r * 1.3) {
    boostPointer = e.pointerId;
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* synthetic pointer */ }
    return;
  }
  if (stick === null) {
    stick = { id: e.pointerId, ox: x, oy: y, x: x, y: y };
    try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* synthetic pointer */ }
  }
}

function onMove(e) {
  if (!stick || e.pointerId !== stick.id) return;
  e.preventDefault();
  stick.x = e.clientX; stick.y = e.clientY;
  let dx = stick.x - stick.ox, dy = stick.y - stick.oy;
  const len = Math.hypot(dx, dy);
  if (len > STICK_MAX) {                    // re-anchor so long drags stay responsive
    stick.ox = stick.x - (dx / len) * STICK_MAX;
    stick.oy = stick.y - (dy / len) * STICK_MAX;
    dx = (dx / len) * STICK_MAX; dy = (dy / len) * STICK_MAX;
  }
  if (len < STICK_DEAD) return;
  const ax = Math.abs(dx), ay = Math.abs(dy);
  if (ax > ay * STICK_HYST) touchDir = dx > 0 ? RIGHT : LEFT;
  else if (ay > ax * STICK_HYST) touchDir = dy > 0 ? DOWN : UP;
}

function onUp(e) {
  if (stick && e.pointerId === stick.id) stick = null;   // heading is kept
  if (boostPointer === e.pointerId) boostPointer = null;
}

canvas.addEventListener("pointerdown", onDown, { passive: false });
canvas.addEventListener("pointermove", onMove, { passive: false });
canvas.addEventListener("pointerup", onUp);
canvas.addEventListener("pointercancel", onUp);
canvas.addEventListener("contextmenu", (e) => e.preventDefault());

function pollGamepad() {
  padState.dir = 0; padState.boost = false;
  let pause = false;
  const pads = navigator.getGamepads ? navigator.getGamepads() : null;
  if (pads) {
    for (let i = 0; i < pads.length; i++) {
      const gp = pads[i];
      if (!gp) continue;
      const b = gp.buttons;
      if (b[12] && b[12].pressed) padState.dir = UP;
      else if (b[13] && b[13].pressed) padState.dir = DOWN;
      else if (b[14] && b[14].pressed) padState.dir = LEFT;
      else if (b[15] && b[15].pressed) padState.dir = RIGHT;
      const ax = gp.axes[0] || 0, ay = gp.axes[1] || 0;
      if (Math.abs(ax) > 0.45 || Math.abs(ay) > 0.45) {
        if (Math.abs(ax) > Math.abs(ay) * STICK_HYST) padState.dir = ax > 0 ? RIGHT : LEFT;
        else if (Math.abs(ay) > Math.abs(ax) * STICK_HYST) padState.dir = ay > 0 ? DOWN : UP;
      }
      if (b[0] && b[0].pressed) padState.boost = true;
      if ((b[9] && b[9].pressed) || (b[8] && b[8].pressed)) pause = true;
    }
  }
  if (pause && !padPauseWas) togglePause();
  padPauseWas = pause;
}

function commands() {
  CMD.dir = heldKeys.length ? heldKeys[heldKeys.length - 1] : (padState.dir || touchDir);
  CMD.boost = keyBoost || boostPointer !== null || padState.boost;
  return CMD;
}

/* ------------------------------------------------------------- UI plumbing */
const UI = { buttons: [], boostPad: null, minimap: null, safeTop: 0, safeBottom: 0 };
const settings = { sound: true, shake: true };

function mkButton(action) {
  return { action: action, x: 0, y: 0, w: 0, h: 0, visible: false };
}

const BTN = {
  pause: mkButton(() => togglePause()),
  sound: mkButton(() => { settings.sound = !settings.sound; Audio.on = settings.sound; save(); }),
  full: mkButton(() => toggleFullscreen()),
  primary: mkButton(() => primaryAction()),
  restart: mkButton(() => { newGame(); game.state = "playing"; }),
  menu: mkButton(() => { game.state = "menu"; }),
  optLang: mkButton(() => {
    setLang(LANGS[(LANGS.indexOf(getLang()) + 1) % LANGS.length]);
    save();
  }),
  optSound: mkButton(() => { settings.sound = !settings.sound; Audio.on = settings.sound; save(); }),
  optShake: mkButton(() => { settings.shake = !settings.shake; save(); }),
};
UI.buttons = Object.keys(BTN).map((k) => BTN[k]);

function save() {
  try {
    localStorage.setItem("cosmic-airxonix-cfg", JSON.stringify({
      sound: settings.sound, shake: settings.shake, lang: getLang(),
    }));
  } catch (e) { /* private mode */ }
}

function load() {
  let cfg = null;
  try {
    const best = localStorage.getItem("cosmic-airxonix-best");
    if (best) game.best = parseInt(best, 10) || 0;
    cfg = JSON.parse(localStorage.getItem("cosmic-airxonix-cfg") || "null");
  } catch (e) { cfg = null; }
  if (cfg) {
    settings.sound = cfg.sound !== false;
    settings.shake = cfg.shake !== false;
    setLang(cfg.lang || detectLang());
  } else setLang(detectLang());
  Audio.on = settings.sound;
}

function layoutUI() {
  const cs = getComputedStyle(document.documentElement);
  UI.safeTop = parseFloat(cs.getPropertyValue("--sat")) || 0;
  UI.safeBottom = parseFloat(cs.getPropertyValue("--sab")) || 0;
  const mmW = Math.min(200, viewW * 0.28), mmH = mmW * (GRID_H / GRID_W);
  UI.minimap = { x: viewW - 14 - mmW, y: UI.safeTop + 14 + 46 + 10, w: mmW, h: mmH };
  const r = clamp(viewW * 0.075, 46, 62);
  UI.boostPad = { cx: viewW - r - 26, cy: viewH - UI.safeBottom - r - 26, r: r };
}

function togglePause() {
  if (game.state === "playing") game.state = "paused";
  else if (game.state === "paused") game.state = "playing";
}

function toggleFullscreen() {
  const el = document.documentElement;
  try {
    if (!document.fullscreenElement && !document.webkitFullscreenElement) {
      (el.requestFullscreen || el.webkitRequestFullscreen || function () {}).call(el);
    } else {
      (document.exitFullscreen || document.webkitExitFullscreen || function () {}).call(document);
    }
  } catch (e) { /* fullscreen refused */ }
}

function primaryAction() {
  if (game.state === "menu") { newGame(); game.state = "playing"; }
  else if (game.state === "clear") { startLevel(game.level + 1); game.state = "playing"; }
  else if (game.state === "over") { newGame(); game.state = "playing"; }
  else if (game.state === "paused") game.state = "playing";
}

function newGame() {
  game.score = 0;
  game.lives = START_LIVES;
  game.nextLife = EXTRA_LIFE_EVERY;
  startLevel(1);
}

/* --------------------------------------------------------------- renderer */
function worldTransform() {
  const sh = settings.shake && cam.shake > 0 ? cam.shake : 0;
  const ox = sh ? (vrnd() - 0.5) * 18 * sh : 0;
  const oy = sh ? (vrnd() - 0.5) * 18 * sh : 0;
  ctx.setTransform(dpr * scale, 0, 0, dpr * scale,
    dpr * (-cam.x * scale + ox), dpr * (-cam.y * scale + oy));
}

function screenSpace() {
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}

function drawVoid(now) {
  screenSpace();
  ctx.fillStyle = voidGradient;
  ctx.fillRect(0, 0, viewW, viewH);

  ctx.globalCompositeOperation = "lighter";
  if (quality >= 2) {
    ctx.globalAlpha = 0.5;
    let nx = (-cam.x * 0.06 + now * 3) % 512, ny = (-cam.y * 0.06) % 512;
    nx = ((nx % 512) + 512) % 512 - 512;
    ny = ((ny % 512) + 512) % 512 - 512;
    for (let y = ny; y < viewH; y += 512) {
      for (let x = nx; x < viewW; x += 512) ctx.drawImage(nebulaCv, x, y);
    }
  }

  const par = [0.18, 0.42, 0.72];
  const layers = quality >= 2 ? 3 : quality === 1 ? 2 : 1;
  for (let li = 3 - layers; li < starLayers.length; li++) {
    const cv = starLayers[li], p = par[li];
    ctx.globalAlpha = (li === 2 ? 0.85 : 0.7) * (0.86 + 0.14 * Math.sin(now * (0.7 + li * 0.4) + li));
    let ox = (-cam.x * scale * p) % 512, oy = (-cam.y * scale * p) % 512;
    ox = ((ox % 512) + 512) % 512 - 512;
    oy = ((oy % 512) + 512) % 512 - 512;
    for (let y = oy; y < viewH; y += 512) {
      for (let x = ox; x < viewW; x += 512) ctx.drawImage(cv, x, y);
    }
  }
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = "source-over";
}

function drawField() {
  worldTransform();
  const vx = clamp(cam.x, 0, WORLD_W), vy = clamp(cam.y, 0, WORLD_H);
  const vw = Math.min(viewW / scale, WORLD_W - vx), vh = Math.min(viewH / scale, WORLD_H - vy);
  if (vw > 0 && vh > 0) ctx.drawImage(claimCv, vx, vy, vw, vh, vx, vy, vw, vh);

  if (quality >= 1) {
    ctx.globalCompositeOperation = "lighter";
    ctx.globalAlpha = 0.45;
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(edgeCv, 0, 0, WORLD_W, WORLD_H);
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
  }
}

function drawTrail(now) {
  if (trailLen === 0) return;
  const pulse = 0.62 + 0.38 * Math.sin(now * 9);
  ctx.globalCompositeOperation = "lighter";
  ctx.fillStyle = "rgba(60,190,255,0.30)";
  ctx.beginPath();
  for (let i = 0; i < trailLen; i++) {
    const c = trailCells[i];
    ctx.rect((c % GRID_W) * CELL - 2, ((c / GRID_W) | 0) * CELL - 2, CELL + 4, CELL + 4);
  }
  ctx.fill();
  ctx.fillStyle = "rgba(190,250,255," + (0.55 + 0.3 * pulse) + ")";
  ctx.beginPath();
  for (let i = 0; i < trailLen; i++) {
    const c = trailCells[i];
    ctx.rect((c % GRID_W) * CELL + CELL * 0.28, ((c / GRID_W) | 0) * CELL + CELL * 0.28,
      CELL * 0.44, CELL * 0.44);
  }
  ctx.fill();
  ctx.globalCompositeOperation = "source-over";
}

function drawEnemies(now) {
  for (const e of enemies) {
    const px = e.x * CELL, py = e.y * CELL, d = e.r * 2 * CELL;
    const halo = HALO[e.art];
    if (halo) {
      const hs = d * 1.55 * (1 + 0.05 * Math.sin(now * 2 + e.pulse));
      ctx.globalCompositeOperation = "lighter";
      ctx.globalAlpha = e.art === "sun" ? 0.85 : 0.45;
      ctx.drawImage(halo, px - hs / 2, py - hs / 2, hs, hs);
      ctx.globalAlpha = 1;
      ctx.globalCompositeOperation = "source-over";
    }
    const im = IMG[e.art];
    if (im) ctx.drawImage(im, px - d / 2, py - d / 2, d, d);
    else {
      ctx.fillStyle = "#c9d6ff";
      ctx.beginPath(); ctx.arc(px, py, d / 2, 0, 6.283); ctx.fill();
    }
    if (e.kind === "hunter") {
      ctx.strokeStyle = "rgba(255,120,90," + (0.5 + 0.4 * Math.sin(now * 5 + e.pulse)) + ")";
      ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.arc(px, py, d / 2 + 5, 0, 6.283); ctx.stroke();
    }
  }
}

function drawShip(now) {
  if (player.dying > 0) return;
  ctx.save();
  ctx.translate(playerX() * CELL, playerY() * CELL);
  ctx.rotate(player.angle);

  const plume = player.plume * (0.75 + 0.25 * Math.sin(now * 30));
  if (plume > 0.05) {
    ctx.globalCompositeOperation = "lighter";
    const len = CELL * (1.5 + plume * 2.6);
    ctx.globalAlpha = plume;
    ctx.fillStyle = plumeGradient;
    ctx.beginPath();
    ctx.moveTo(-CELL * 0.34, CELL * 0.45);
    ctx.lineTo(CELL * 0.34, CELL * 0.45);
    ctx.lineTo(CELL * 0.12, CELL * 0.5 + len);
    ctx.lineTo(-CELL * 0.12, CELL * 0.5 + len);
    ctx.closePath();
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
  }
  const blink = player.invuln > 0 && Math.floor(now * 12) % 2 === 0;
  if (!blink) {
    const s = CELL * 2.05;
    if (IMG.ship) ctx.drawImage(IMG.ship, -s / 2, -s / 2, s, s);
    else { ctx.fillStyle = "#dff3ff"; ctx.fillRect(-CELL * 0.4, -CELL * 0.4, CELL * 0.8, CELL * 0.8); }
  }
  if (player.invuln > 0) {
    ctx.strokeStyle = "rgba(140,230,255," + (0.25 + 0.25 * Math.sin(now * 8)) + ")";
    ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(0, 0, CELL * 1.15, 0, 6.283); ctx.stroke();
  }
  ctx.restore();
}

function drawParticles() {
  ctx.globalCompositeOperation = "lighter";
  for (let c = 0; c < P_COLORS.length; c++) {
    let any = false;
    ctx.beginPath();
    for (let i = 0; i < PMAX; i++) {
      if (P.life[i] <= 0 || P.col[i] !== c) continue;
      const k = P.life[i] / P.max[i];
      const r = P.size[i] * k;
      ctx.moveTo(P.x[i] + r, P.y[i]);
      ctx.arc(P.x[i], P.y[i], r, 0, 6.283);
      any = true;
    }
    if (any) { ctx.fillStyle = "rgba(" + P_COLORS[c] + ",0.75)"; ctx.fill(); }
  }
  ctx.globalCompositeOperation = "source-over";
}

/* --------------------------------------------------------------------- HUD */
const FONT = "system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif";

function roundRect(x, y, w, h, r) {
  ctx.beginPath();
  if (ctx.roundRect) ctx.roundRect(x, y, w, h, r);
  else ctx.rect(x, y, w, h);
}

function panel(x, y, w, h, r, alpha) {
  ctx.fillStyle = "rgba(8,10,26," + (alpha === undefined ? 0.55 : alpha) + ")";
  roundRect(x, y, w, h, r || 12);
  ctx.fill();
  ctx.strokeStyle = "rgba(120,190,255,0.22)";
  ctx.lineWidth = 1;
  ctx.stroke();
}

function text(str, x, y, size, color, align, weight) {
  ctx.font = (weight || 600) + " " + size + "px " + FONT;
  ctx.fillStyle = color || "#dceaff";
  ctx.textAlign = align || "left";
  ctx.textBaseline = "middle";
  ctx.fillText(str, x, y);
}

function drawButton(b, x, y, w, h, label, primary) {
  b.x = x; b.y = y; b.w = w; b.h = h; b.visible = true;
  ctx.fillStyle = primary ? "rgba(40,140,220,0.35)" : "rgba(10,16,36,0.62)";
  roundRect(x, y, w, h, 12); ctx.fill();
  ctx.strokeStyle = primary ? "rgba(150,230,255,0.85)" : "rgba(120,190,255,0.35)";
  ctx.lineWidth = primary ? 2 : 1.2;
  ctx.stroke();
  text(label, x + w / 2, y + h / 2 + 1, primary ? 21 : 16,
    primary ? "#eaf8ff" : "#bcd6f5", "center", 700);
}

function wrapText(str, x, y, maxW, lineH, size, color) {
  ctx.font = "600 " + size + "px " + FONT;
  ctx.fillStyle = color;
  ctx.textAlign = "left";
  ctx.textBaseline = "middle";
  const words = String(str).split(" ");
  let line = "";
  for (let i = 0; i < words.length; i++) {
    const test = line ? line + " " + words[i] : words[i];
    if (ctx.measureText(test).width > maxW && line) {
      ctx.fillText(line, x, y);
      y += lineH;
      line = words[i];
    } else line = test;
  }
  if (line) { ctx.fillText(line, x, y); y += lineH; }
  return y;
}

function drawMinimap() {
  const m = UI.minimap;
  panel(m.x - 6, m.y - 6, m.w + 12, m.h + 12, 10, 0.5);
  ctx.fillStyle = "rgba(4,6,16,0.9)";
  ctx.fillRect(m.x, m.y, m.w, m.h);
  ctx.globalAlpha = 0.95;
  ctx.imageSmoothingEnabled = true;
  ctx.drawImage(maskCv, m.x, m.y, m.w, m.h);
  ctx.globalAlpha = 1;
  const sx = m.w / GRID_W, sy = m.h / GRID_H;
  for (const e of enemies) {
    ctx.fillStyle = e.kind === "hunter" ? "#ff7a5a" : "#ffd18a";
    ctx.beginPath();
    ctx.arc(m.x + e.x * sx, m.y + e.y * sy, Math.max(1.6, e.r * sx), 0, 6.283);
    ctx.fill();
  }
  if (player.dying <= 0) {
    ctx.fillStyle = "#8ff0ff";
    ctx.beginPath();
    ctx.arc(m.x + playerX() * sx, m.y + playerY() * sy, 3, 0, 6.283);
    ctx.fill();
  }
  ctx.strokeStyle = "rgba(160,220,255,0.55)";
  ctx.lineWidth = 1;
  ctx.strokeRect(m.x + (cam.x / CELL) * sx, m.y + (cam.y / CELL) * sy,
    (viewW / scale / CELL) * sx, (viewH / scale / CELL) * sy);
}

function drawBoostPad() {
  const p = UI.boostPad;
  ctx.save();
  ctx.globalAlpha = player.boost > 0.02 ? 1 : 0.45;
  ctx.fillStyle = CMD.boost ? "rgba(60,180,255,0.32)" : "rgba(10,18,40,0.5)";
  ctx.beginPath(); ctx.arc(p.cx, p.cy, p.r, 0, 6.283); ctx.fill();
  ctx.strokeStyle = "rgba(140,220,255,0.45)";
  ctx.lineWidth = 2;
  ctx.beginPath(); ctx.arc(p.cx, p.cy, p.r, 0, 6.283); ctx.stroke();
  ctx.strokeStyle = "rgba(120,240,255,0.95)";
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.arc(p.cx, p.cy, p.r - 5, -Math.PI / 2, -Math.PI / 2 + 6.283 * player.boost);
  ctx.stroke();
  ctx.fillStyle = "rgba(210,245,255,0.95)";
  ctx.beginPath();
  ctx.moveTo(p.cx, p.cy - p.r * 0.42);
  ctx.lineTo(p.cx + p.r * 0.30, p.cy + p.r * 0.08);
  ctx.lineTo(p.cx + p.r * 0.10, p.cy + p.r * 0.08);
  ctx.lineTo(p.cx + p.r * 0.05, p.cy + p.r * 0.44);
  ctx.lineTo(p.cx - p.r * 0.30, p.cy - p.r * 0.02);
  ctx.lineTo(p.cx - p.r * 0.08, p.cy - p.r * 0.02);
  ctx.closePath(); ctx.fill();
  ctx.restore();
}

function drawStick() {
  if (!stick) return;
  ctx.save();
  ctx.strokeStyle = "rgba(140,220,255,0.35)";
  ctx.lineWidth = 2;
  ctx.beginPath(); ctx.arc(stick.ox, stick.oy, STICK_MAX, 0, 6.283); ctx.stroke();
  const dx = clamp(stick.x - stick.ox, -STICK_MAX, STICK_MAX);
  const dy = clamp(stick.y - stick.oy, -STICK_MAX, STICK_MAX);
  ctx.fillStyle = "rgba(120,210,255,0.30)";
  ctx.beginPath(); ctx.arc(stick.ox + dx, stick.oy + dy, 26, 0, 6.283); ctx.fill();
  ctx.strokeStyle = "rgba(190,240,255,0.7)";
  ctx.beginPath(); ctx.arc(stick.ox + dx, stick.oy + dy, 26, 0, 6.283); ctx.stroke();
  ctx.restore();
}

function drawHud() {
  screenSpace();
  const pad = 14, top = UI.safeTop + pad, bs = 46;

  panel(pad, top, 178, 76, 12);
  text(t("level") + " " + game.level, pad + 14, top + 22, 17, "#9fd6ff");
  text(String(game.score), pad + 14, top + 50, 24, "#ffffff", "left", 800);

  if (viewW > 560) {
    const bw = Math.min(300, viewW - 460), bx = viewW / 2 - bw / 2, by = top + 16;
    if (bw > 120) {
      panel(bx - 10, by - 12, bw + 20, 40, 10);
      ctx.fillStyle = "rgba(255,255,255,0.10)";
      roundRect(bx, by, bw, 14, 7); ctx.fill();
      ctx.fillStyle = hudGradient;
      roundRect(bx, by, Math.max(3, bw * clamp(game.progress / TARGET, 0, 1)), 14, 7); ctx.fill();
      ctx.strokeStyle = "rgba(255,255,255,0.75)";
      ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(bx + bw, by - 3); ctx.lineTo(bx + bw, by + 17); ctx.stroke();
      text(Math.floor(game.progress * 100) + "% / " + Math.round(TARGET * 100) + "%",
        bx + bw / 2, by + 7, 13, "#dceaff", "center", 700);
    }
  } else {
    text(Math.floor(game.progress * 100) + "% / " + Math.round(TARGET * 100) + "%",
      pad + 14, top + 92, 15, "#9fd6ff", "left", 700);
  }

  const ly = viewH - UI.safeBottom - 30;
  for (let i = 0; i < Math.min(game.lives, 8); i++) {
    if (IMG.ship) ctx.drawImage(IMG.ship, pad - 3 + i * 26, ly - 11, 22, 22);
  }
  if (game.lives > 8) text("x" + game.lives, pad + 8 + 8 * 26, ly, 15, "#bcd6f5");

  drawMinimap();
  drawButton(BTN.pause, viewW - pad - bs, top, bs, bs, game.state === "paused" ? "▶" : "II");
  drawButton(BTN.sound, viewW - pad - bs * 2 - 8, top, bs, bs, settings.sound ? "♪" : "✕");
  drawButton(BTN.full, viewW - pad - bs * 3 - 16, top, bs, bs, "⛶");
  drawBoostPad();
  drawStick();

  for (let i = toasts.length - 1; i >= 0; i--) {
    const tt = toasts[i];
    ctx.globalAlpha = clamp(tt.life / 0.6, 0, 1);
    text(tt.text, viewW / 2, viewH * 0.28 + i * 30, 22, tt.color, "center", 800);
    ctx.globalAlpha = 1;
  }
}

function dim(alpha) {
  screenSpace();
  ctx.fillStyle = "rgba(3,5,14," + alpha + ")";
  ctx.fillRect(0, 0, viewW, viewH);
}

function optionsRow(y) {
  const w = Math.min(360, viewW - 40), x = viewW / 2 - w / 2, bw = (w - 16) / 3;
  drawButton(BTN.optLang, x, y, bw, 44, getLang() === "ru" ? "RU" : "EN");
  drawButton(BTN.optSound, x + bw + 8, y, bw, 44, "♪ " + (settings.sound ? t("on") : t("off")));
  drawButton(BTN.optShake, x + (bw + 8) * 2, y, bw, 44, "≈ " + (settings.shake ? t("on") : t("off")));
}

function drawMenu() {
  dim(0.55);
  ctx.save();
  ctx.shadowColor = "rgba(90,200,255,0.85)";
  ctx.shadowBlur = 26;
  text(t("title"), viewW / 2, viewH * 0.14, Math.min(54, viewW * 0.082), "#ffffff", "center", 800);
  ctx.restore();
  text(t("subtitle"), viewW / 2, viewH * 0.14 + 40, Math.min(19, viewW * 0.034), "#9fd6ff", "center");

  const w = Math.min(580, viewW - 44);
  let y = viewH * 0.28;
  for (const line of [t("how_1"), t("how_2"), t("how_3")]) {
    y = wrapText(line, viewW / 2 - w / 2, y, w, 21, 15, "#c9dcf5") + 8;
  }
  y = wrapText(usingTouch ? t("ctrl_touch") : t("ctrl_keys"), viewW / 2 - w / 2, y + 8, w, 20, 14, "#7fb6e8");
  text(t("field_note"), viewW / 2, y + 16, 13, "#6f8fb8", "center");

  const py = Math.min(viewH - 150, y + 46);
  drawButton(BTN.primary, viewW / 2 - Math.min(140, viewW * 0.35), py,
    Math.min(280, viewW * 0.7), 58, t("btn_play"), true);
  optionsRow(py + 70);
  if (game.best > 0) text(t("best") + ": " + game.best, viewW / 2, py + 136, 16, "#9fd6ff", "center");
}

function drawPause() {
  dim(0.6);
  text(t("paused"), viewW / 2, viewH * 0.22, 40, "#ffffff", "center", 800);
  text(t("score") + ": " + game.score, viewW / 2, viewH * 0.30, 20, "#9fd6ff", "center");
  const bw = Math.min(280, viewW * 0.7), x = viewW / 2 - bw / 2;
  let y = viewH * 0.38;
  drawButton(BTN.primary, x, y, bw, 56, t("btn_resume"), true);
  drawButton(BTN.restart, x, y + 68, bw, 48, t("btn_restart"));
  drawButton(BTN.menu, x, y + 126, bw, 48, t("btn_menu"));
  optionsRow(y + 190);
}

function drawClear() {
  dim(0.55);
  text(t("level_clear"), viewW / 2, viewH * 0.24, 38, "#ffffff", "center", 800);
  text(t("claimed") + ": " + Math.floor(game.progress * 100) + "%", viewW / 2, viewH * 0.33, 21, "#9fd6ff", "center");
  text(t("area_bonus") + ": +" + game.clearBonus, viewW / 2, viewH * 0.39, 18, "#c9dcf5", "center");
  text(t("life_bonus") + ": +" + game.clearShips, viewW / 2, viewH * 0.435, 18, "#c9dcf5", "center");
  text(t("score") + ": " + game.score, viewW / 2, viewH * 0.50, 25, "#ffffff", "center", 800);
  drawButton(BTN.primary, viewW / 2 - Math.min(150, viewW * 0.38), viewH * 0.60,
    Math.min(300, viewW * 0.76), 58, t("btn_next") + " → " + t("level") + " " + (game.level + 1), true);
}

function drawOver() {
  dim(0.66);
  text(t("game_over"), viewW / 2, viewH * 0.26, 42, "#ffffff", "center", 800);
  text(t("final_score") + ": " + game.score, viewW / 2, viewH * 0.36, 24, "#9fd6ff", "center", 700);
  text(t("level") + ": " + game.level, viewW / 2, viewH * 0.42, 19, "#c9dcf5", "center");
  if (game.score >= game.best && game.score > 0) {
    text(t("new_best") + " " + game.best, viewW / 2, viewH * 0.48, 20, "#ffd9a0", "center", 800);
  } else {
    text(t("best") + ": " + game.best, viewW / 2, viewH * 0.48, 18, "#8fb6dd", "center");
  }
  const bw = Math.min(280, viewW * 0.7), x = viewW / 2 - bw / 2;
  drawButton(BTN.primary, x, viewH * 0.58, bw, 58, t("btn_restart"), true);
  drawButton(BTN.menu, x, viewH * 0.58 + 70, bw, 48, t("btn_menu"));
}

function drawLoading() {
  screenSpace();
  ctx.fillStyle = "#04050d";
  ctx.fillRect(0, 0, viewW, viewH);
  text(t("loading"), viewW / 2, viewH / 2, 20, "#9fd6ff", "center");
  const w = 220, x = viewW / 2 - w / 2, y = viewH / 2 + 26;
  ctx.fillStyle = "rgba(255,255,255,0.12)";
  roundRect(x, y, w, 8, 4); ctx.fill();
  ctx.fillStyle = "#4fc3ff";
  roundRect(x, y, w * (assetsTotal ? assetsLoaded / assetsTotal : 0), 8, 4); ctx.fill();
}

/* ------------------------------------------------------------------- loop */
let accumulator = 0, lastTime = 0;
let frames = 0, fpsAt = 0, fps = 0, stepMs = 0, renderMs = 0, slowSamples = 0, fastSamples = 0;
const devOverlay = new URLSearchParams(location.search).has("dev");

addEventListener("blur", () => { if (game.state === "playing") game.state = "paused"; });
addEventListener("focus", () => { lastTime = performance.now(); });
document.addEventListener("visibilitychange", () => {
  if (document.hidden && game.state === "playing") game.state = "paused";
});
addEventListener("resize", resize);
addEventListener("orientationchange", () => setTimeout(resize, 150));

function update(dt) {
  game.time += dt;
  for (let i = toasts.length - 1; i >= 0; i--) {
    toasts[i].life -= dt;
    if (toasts[i].life <= 0) toasts.splice(i, 1);
  }
  updateParticles(dt);
  if (game.state !== "playing") return;
  game.levelTime += dt;
  updatePlayer(dt, commands());
  if (game.state !== "playing") return;
  updateEnemies(dt);
  updateCamera(dt);
}

function render() {
  const now = game.time;
  for (const b of UI.buttons) b.visible = false;
  flushClaimLayer();
  drawVoid(now);
  drawField();
  worldTransform();
  drawTrail(now);
  drawEnemies(now);
  drawShip(now);
  drawParticles();

  if (game.state === "playing" || game.state === "paused") drawHud();
  if (game.state === "menu") drawMenu();
  else if (game.state === "paused") drawPause();
  else if (game.state === "clear") drawClear();
  else if (game.state === "over") drawOver();

  if (devOverlay) {
    screenSpace();
    text(fps + " fps  sim " + stepMs.toFixed(2) + " ms  draw " + renderMs.toFixed(2) +
      " ms  q" + quality + "  ent:" + enemies.length + "  trail:" + trailLen +
      "  claimed:" + game.claimed, 10, viewH - 12, 12, "#7CFC00", "left", 600);
  }
}

function frame(now) {
  requestAnimationFrame(frame);
  if (game.state === "loading") { drawLoading(); return; }
  let dtMs = now - lastTime;
  lastTime = now;
  if (dtMs > 250) dtMs = 250;                 // tab was away: never spiral
  if (dtMs < 0) dtMs = 0;
  accumulator += dtMs;
  pollGamepad();
  const t0 = performance.now();
  let steps = 0;
  while (accumulator >= STEP * 1000 && steps < 5) {
    update(STEP);
    accumulator -= STEP * 1000;
    steps++;
  }
  if (steps === 5) accumulator = 0;
  stepMs = performance.now() - t0;
  const t1 = performance.now();
  render();
  renderMs = performance.now() - t1;
  frames++;
  if (now - fpsAt >= 500) {
    fps = Math.round((frames * 1000) / (now - fpsAt));
    frames = 0;
    fpsAt = now;
    // the weakest declared platform sets the budget: shed the most expensive
    // background layers rather than drop frames
    if (fps > 0 && fps < 45) {
      fastSamples = 0;
      if (++slowSamples >= 3 && quality > 0) { quality--; slowSamples = 0; }
    } else {
      slowSamples = 0;
      // a single stutter must not cost the background permanently
      if (fps >= 58 && quality < 2 && ++fastSamples >= 8) { quality++; fastSamples = 0; }
    }
  }
}

/* ------------------------------------------------------------------- boot */
async function boot() {
  load();
  buildMaskCanvases();
  buildVoid();
  resize();
  requestAnimationFrame(frame);
  await loadAll();
  buildHalos();
  startLevel(1);
  game.state = "menu";
  lastTime = performance.now();
}

boot();

/* Test seam for tools/verify.mjs — read-only state plus the two transitions
 * that are too slow to reach by playing (losing every ship, clearing a level). */
window.__cx = {
  game: game, player: player, enemies: enemies, grid: grid,
  start: () => { newGame(); game.state = "playing"; },
  press: (d) => { heldKeys.length = 0; if (d) heldKeys.push(d); },
  dirs: { UP: UP, DOWN: DOWN, LEFT: LEFT, RIGHT: RIGHT },
  progress: () => game.progress,
  perf: () => ({ fps: fps, sim: stepMs, draw: renderMs, quality: quality }),
  kill: () => { player.invuln = 0; killPlayer(); },
  forceClear: () => { game.progress = TARGET; levelCleared(); },
};
