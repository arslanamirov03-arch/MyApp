// Cosmic Airxonix is a single-player game: all simulation runs on the client.
// The platform still requires one rules module at the archive root, so this is
// the documented solo stub (build-game.md §1) — no imports, no timers.

export const meta = { game: "cosmic-airxonix", minPlayers: 1, maxPlayers: 1 };

export function setup() {
  return {};
}

export function validateAction() {
  return { ok: true };
}

export function applyAction(state) {
  return state;
}

export function isGameOver() {
  return { over: false };
}

export function viewFor(state) {
  return state;
}
