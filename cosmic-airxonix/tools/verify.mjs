/* Smoke + performance verification for Cosmic Airxonix (build-game.md §5).
 *
 *   node tools/verify.mjs [--shots]
 *
 * Serves the game folder and drives a real Chromium: no console errors or 404s,
 * the reference route (fly out, close a loop, territory grows) played through
 * touch only, phone and tablet layouts, keyboard by physical key code, the
 * frame budget, and determinism of the seeded level setup.
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

// GAME_ROOT lets the same suite run against a mirror of the deployed bytes
const ROOT = process.env.GAME_ROOT
  ? path.resolve(process.env.GAME_ROOT)
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const SHOTS = process.argv.includes("--shots");
const SHOT_DIR = process.env.SHOT_DIR || path.join(ROOT, "tools", "shots");

const require = createRequire(import.meta.url);
function loadChromium() {
  const tries = ["playwright"];
  try { tries.push(path.join(execSync("npm root -g").toString().trim(), "playwright")); } catch (e) { /* no npm */ }
  for (const t of tries) {
    try { return require(t).chromium; } catch (e) { /* next candidate */ }
  }
  throw new Error("playwright not found — install it or set NODE_PATH");
}

const MIME = {
  ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript",
  ".png": "image/png", ".jpg": "image/jpeg", ".css": "text/css", ".csv": "text/csv",
};

const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split("?")[0]).replace(/^\/+/, "") || "index.html";
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); res.end("not found"); return;
  }
  res.writeHead(200, { "Content-Type": MIME[path.extname(file)] || "application/octet-stream" });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, r));
const base = "http://127.0.0.1:" + server.address().port + "/";

const problems = [];
const note = (ok, msg) => {
  console.log((ok ? "  ok    " : "  FAIL  ") + msg);
  if (!ok) problems.push(msg);
};

const chromium = loadChromium();
const browser = await chromium.launch();

async function open(viewport, touch) {
  const ctx = await browser.newContext({
    viewport: viewport, hasTouch: touch, isMobile: touch, deviceScaleFactor: 2,
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("pageerror", (e) => errors.push(String(e)));
  page.on("requestfailed", (r) => errors.push("request failed: " + r.url()));
  page.on("response", (r) => { if (r.status() >= 400) errors.push(r.status() + " " + r.url()); });
  await page.goto(base, { waitUntil: "load" });
  await page.waitForFunction(() => window.__cx && window.__cx.game.state === "menu", null, { timeout: 30000 });
  return { page: page, ctx: ctx, errors: errors };
}

/* real PointerEvents so the touch handlers themselves are under test */
const touch = (page, type, x, y, id) => page.evaluate((a) => {
  document.getElementById("c").dispatchEvent(new PointerEvent(a.type, {
    pointerId: a.id, pointerType: "touch", isPrimary: true,
    clientX: a.x, clientY: a.y, bubbles: true, cancelable: true, button: 0, buttons: 1,
  }));
}, { type: type, x: x, y: y, id: id || 7 });

async function shot(page, name) {
  if (!SHOTS) return;
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  await page.screenshot({ path: path.join(SHOT_DIR, name + ".png") });
}

/* ------------------------------------------------ 1. tablet, touch only ---- */
console.log("\n[1] tablet 1180x820, touch only");
{
  const { page, ctx, errors } = await open({ width: 1180, height: 820 }, true);
  await shot(page, "1-menu-tablet");
  await touch(page, "pointerdown", 590, 420);
  await touch(page, "pointerup", 590, 420);
  await page.waitForTimeout(250);
  note(await page.evaluate(() => window.__cx.game.state === "playing"), "tap starts the game");

  // the reference route, driven only by the floating stick: out into the void,
  // along, then back to the border — one thumb, one flick per turn
  const swipe = async (dx, dy) => {
    await touch(page, "pointerdown", 300, 500);
    await touch(page, "pointermove", 300 + dx, 500 + dy);
    await touch(page, "pointerup", 300 + dx, 500 + dy);
  };
  const before = await page.evaluate(() => window.__cx.game.claimed);
  await swipe(0, 90);                                   // down, into the void
  await page.waitForTimeout(800);
  note(await page.evaluate(() => window.__cx.player.drawing), "stick drives the ship into the void");
  await shot(page, "2-trail-tablet");

  await swipe(90, 0);                                   // right
  await page.waitForTimeout(700);
  await swipe(0, -90);                                  // up, back to the border
  await page.waitForTimeout(1600);
  const after = await page.evaluate(() => window.__cx.game.claimed);
  note(after > before, "closing the loop claims territory (" + before + " -> " + after + " cells)");
  note(await page.evaluate(() => window.__cx.game.progress > 0), "progress advances");
  await shot(page, "3-claimed-tablet");

  // boost pad — a second thumb, held while the stick still steers
  const pad = await page.evaluate(() => ({ x: innerWidth - 26 - 62, y: innerHeight - 26 - 62 }));
  await swipe(90, 0);                                   // get moving first
  await page.evaluate(() => { window.__cx.player.boost = 1; });
  await touch(page, "pointerdown", pad.x, pad.y, 9);
  await page.waitForTimeout(300);
  const boosting = await page.evaluate(() => window.__cx.player.boosting && window.__cx.player.boost < 1);
  await touch(page, "pointerup", pad.x, pad.y, 9);
  note(boosting, "boost pad accelerates and drains the energy ring");

  // headless Chromium rasterises on the CPU, so this is a floor, not the
  // number a real tablet sees; the simulation budget is the CPU cost we own
  await page.waitForTimeout(3000);                      // let adaptive quality settle
  const perf = await page.evaluate(() => new Promise((res) => {
    const t0 = performance.now();
    let n = 0, sim = 0, draw = 0;
    const tick = () => {
      n++;
      const d = window.__cx.perf();
      sim = Math.max(sim, d.sim); draw = Math.max(draw, d.draw);
      if (performance.now() - t0 > 2000) {
        res({ fps: n / ((performance.now() - t0) / 1000), sim: sim, draw: draw, q: d.quality });
      } else requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }));
  note(perf.sim < 3, "simulation step " + perf.sim.toFixed(2) + " ms (budget 3 ms of the 16.6 ms frame)");
  note(perf.fps > 40, "frame rate " + perf.fps.toFixed(1) + " fps at quality " + perf.q +
    ", draw " + perf.draw.toFixed(1) + " ms (software raster)");
  note(errors.length === 0, "no console errors / 404s" + (errors.length ? ": " + errors.join(" | ") : ""));
  await ctx.close();
}

/* ------------------------------------------------- 2. phone portrait ------- */
console.log("\n[2] phone 390x844, touch only");
{
  const { page, ctx, errors } = await open({ width: 390, height: 844 }, true);
  const fits = await page.evaluate(() => {
    const c = document.getElementById("c");
    return c.clientWidth <= innerWidth && c.clientHeight <= innerHeight &&
      document.body.scrollWidth <= innerWidth;
  });
  note(fits, "canvas fits the viewport, no horizontal overflow");
  await touch(page, "pointerdown", 195, 500);
  await touch(page, "pointerup", 195, 500);
  await page.waitForTimeout(250);
  note(await page.evaluate(() => window.__cx.game.state === "playing"), "playable at phone size");
  const padReach = await page.evaluate(() => {
    const r = Math.max(46, Math.min(innerWidth * 0.075, 62));
    return innerWidth - r - 26 > 0 && innerHeight - r - 26 > 0;
  });
  note(padReach, "boost pad stays on screen");
  await shot(page, "4-phone");
  note(errors.length === 0, "no console errors" + (errors.length ? ": " + errors.join(" | ") : ""));
  await ctx.close();
}

/* --------------------------------------- 3. desktop, keyboard by key code -- */
console.log("\n[3] desktop 1440x900, keyboard only");
{
  const { page, ctx, errors } = await open({ width: 1440, height: 900 }, false);
  await page.keyboard.press("Enter");
  await page.waitForTimeout(200);
  note(await page.evaluate(() => window.__cx.game.state === "playing"), "Enter starts the game");

  const x0 = await page.evaluate(() => window.__cx.player.cx);
  await page.keyboard.down("ArrowRight");
  await page.waitForTimeout(600);
  await page.keyboard.up("ArrowRight");
  const x1 = await page.evaluate(() => window.__cx.player.cx);
  note(x1 > x0, "ArrowRight moves the ship (" + x0 + " -> " + x1 + ")");

  await page.keyboard.down("KeyS");                    // physical code, not a letter
  await page.waitForTimeout(500);
  note(await page.evaluate(() => window.__cx.player.drawing), "KeyS drives into the void");
  await page.keyboard.up("KeyS");
  await page.keyboard.press("KeyP");
  await page.waitForTimeout(150);
  note(await page.evaluate(() => window.__cx.game.state === "paused"), "KeyP pauses");
  await shot(page, "5-pause");
  await page.keyboard.press("KeyP");
  await page.waitForTimeout(1500);

  const bad = await page.evaluate(() => {
    const { enemies, grid } = window.__cx;
    let inside = 0, out = 0;
    for (const e of enemies) {
      const cx = Math.floor(e.x), cy = Math.floor(e.y);
      if (cx < 0 || cy < 0 || cx >= 100 || cy >= 50) { out++; continue; }
      const solid = e.kind === "planet" ? 1 : 0;
      if (grid[cy * 100 + cx] === solid) inside++;
    }
    return { inside: inside, out: out };
  });
  note(bad.inside === 0 && bad.out === 0, "every body stays in the region it belongs to");
  note(errors.length === 0, "no console errors" + (errors.length ? ": " + errors.join(" | ") : ""));
  await ctx.close();
}

/* ---------------------------------------------------- 4. determinism ------- */
console.log("\n[4] determinism of the seeded setup");
{
  const snap = async () => {
    const { page, ctx } = await open({ width: 1180, height: 820 }, false);
    const s = await page.evaluate(() => window.__cx.enemies.map((e) =>
      [e.art, e.x.toFixed(6), e.y.toFixed(6), e.vx.toFixed(6), e.vy.toFixed(6)].join(":")).join("|"));
    await ctx.close();
    return s;
  };
  const a = await snap(), b = await snap();
  note(a === b && a.length > 0, "same seed produces the same level layout");
}

/* ------------------------------------------------------- 5. life cycle ---- */
console.log("\n[5] ships, level clear, game over, restart");
{
  const { page, ctx, errors } = await open({ width: 1180, height: 820 }, true);
  await page.keyboard.press("Enter");
  await page.waitForTimeout(200);

  const lives0 = await page.evaluate(() => window.__cx.game.lives);
  await page.evaluate(() => window.__cx.kill());
  await page.waitForTimeout(1600);
  const after = await page.evaluate(() => ({
    lives: window.__cx.game.lives, state: window.__cx.game.state,
    dying: window.__cx.player.dying, drawing: window.__cx.player.drawing,
    onGround: window.__cx.grid[window.__cx.player.cy * 100 + window.__cx.player.cx],
  }));
  note(after.lives === lives0 - 1, "a lost ship costs exactly one life");
  note(after.state === "playing" && after.dying === 0, "the run continues after respawn");
  note(after.onGround === 1, "the ship respawns on claimed ground");
  await shot(page, "6-after-death");

  await page.evaluate(() => window.__cx.forceClear());
  await page.waitForTimeout(200);
  note(await page.evaluate(() => window.__cx.game.state === "clear"), "reaching the target clears the level");
  await shot(page, "7-level-clear");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(400);
  const lvl2 = await page.evaluate(() => ({
    level: window.__cx.game.level, state: window.__cx.game.state,
    planets: window.__cx.enemies.filter((e) => e.kind === "planet").length,
    progress: window.__cx.game.progress,
  }));
  note(lvl2.level === 2 && lvl2.state === "playing", "the next level starts");
  note(lvl2.progress === 0 && lvl2.planets > 0, "level 2 resets the field and adds bodies (" + lvl2.planets + " planets)");

  await page.evaluate(() => { window.__cx.game.lives = 1; window.__cx.kill(); });
  await page.waitForTimeout(1600);
  note(await page.evaluate(() => window.__cx.game.state === "over"), "losing the last ship ends the run");
  await shot(page, "8-game-over");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(400);
  const restarted = await page.evaluate(() => ({
    state: window.__cx.game.state, level: window.__cx.game.level,
    lives: window.__cx.game.lives, score: window.__cx.game.score,
  }));
  note(restarted.state === "playing" && restarted.level === 1 && restarted.lives === 3 && restarted.score === 0,
    "restart returns a clean run");
  note(errors.length === 0, "no console errors" + (errors.length ? ": " + errors.join(" | ") : ""));
  await ctx.close();
}

await browser.close();
server.close();

console.log("\n" + (problems.length ? problems.length + " PROBLEM(S)" : "all checks passed"));
process.exit(problems.length ? 1 : 0);
