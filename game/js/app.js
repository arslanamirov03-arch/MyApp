(function () {
  "use strict";

  const STORAGE_PREFIX = "hog_progress_";
  const HIT_PAD = 2.5; // percentage points of forgiveness added around each hotspot

  const el = {
    menu: document.getElementById("screen-menu"),
    game: document.getElementById("screen-game"),
    grid: document.getElementById("level-grid"),
    btnMenu: document.getElementById("btn-menu"),
    image: document.getElementById("game-image"),
    imageWrap: document.getElementById("image-wrap"),
    markers: document.getElementById("markers-layer"),
    title: document.getElementById("level-title"),
    objectList: document.getElementById("object-list"),
    hintBtn: document.getElementById("btn-hint"),
    hintCount: document.getElementById("hint-count"),
    resetBtn: document.getElementById("btn-reset"),
    foundCount: document.getElementById("found-count"),
    totalCount: document.getElementById("total-count"),
    winOverlay: document.getElementById("win-overlay"),
    winStats: document.getElementById("win-stats"),
    btnReplay: document.getElementById("btn-replay"),
    btnBackMenu: document.getElementById("btn-back-menu"),
  };

  let manifest = [];
  let level = null;       // currently loaded level data
  let foundSet = new Set();
  let hintsUsed = 0;

  function getProgress(id) {
    try {
      const raw = localStorage.getItem(STORAGE_PREFIX + id);
      if (!raw) return { foundIds: [], hintsUsed: 0, completed: false };
      return JSON.parse(raw);
    } catch (e) {
      return { foundIds: [], hintsUsed: 0, completed: false };
    }
  }

  function saveProgress(id, data) {
    localStorage.setItem(STORAGE_PREFIX + id, JSON.stringify(data));
  }

  function clearProgress(id) {
    localStorage.removeItem(STORAGE_PREFIX + id);
  }

  // ---------- Menu ----------

  function renderMenu() {
    el.grid.innerHTML = "";
    manifest.forEach((entry) => {
      const progress = getProgress(entry.id);
      const card = document.createElement("div");
      card.className = "level-card";

      const img = document.createElement("img");
      img.className = "thumb";
      img.src = entry.image;
      img.alt = entry.title;

      const body = document.createElement("div");
      body.className = "card-body";

      const h3 = document.createElement("h3");
      h3.textContent = entry.title;

      const meta = document.createElement("div");
      meta.className = "card-meta";

      const left = document.createElement("span");
      left.textContent = entry.objectCount + " предметов";

      const right = document.createElement("span");
      if (progress.completed) {
        right.textContent = "✓ Пройдено";
        right.className = "badge-done";
      } else if (progress.foundIds && progress.foundIds.length) {
        right.textContent = progress.foundIds.length + "/" + entry.objectCount;
      } else {
        right.textContent = "";
      }

      meta.appendChild(left);
      meta.appendChild(right);
      body.appendChild(h3);
      body.appendChild(meta);
      card.appendChild(img);
      card.appendChild(body);

      card.addEventListener("click", () => openLevel(entry));
      el.grid.appendChild(card);
    });
  }

  function showScreen(name) {
    el.menu.classList.toggle("hidden", name !== "menu");
    el.game.classList.toggle("hidden", name !== "game");
    el.btnMenu.classList.toggle("hidden", name !== "game");
  }

  // ---------- Game ----------

  function openLevel(entry) {
    fetch(entry.file)
      .then((r) => r.json())
      .then((data) => {
        level = data;
        startLevel();
      });
  }

  function startLevel() {
    const progress = getProgress(level.id);
    foundSet = new Set(progress.foundIds || []);
    hintsUsed = progress.hintsUsed || 0;

    el.title.textContent = level.title;
    el.image.src = level.image;
    el.markers.innerHTML = "";
    el.winOverlay.classList.add("hidden");

    el.objectList.innerHTML = "";
    level.objects.forEach((obj) => {
      const li = document.createElement("li");
      li.textContent = obj.name;
      li.dataset.id = obj.id;
      if (foundSet.has(obj.id)) li.classList.add("found");
      el.objectList.appendChild(li);
    });

    // redraw rings for already-found objects
    level.objects.forEach((obj) => {
      if (foundSet.has(obj.id)) drawFoundRing(obj);
    });

    updateCounters();
    updateHintButton();
    showScreen("game");
  }

  function updateCounters() {
    el.foundCount.textContent = foundSet.size;
    el.totalCount.textContent = level.objects.length;
  }

  function totalHints() {
    return typeof level.hints === "number" ? level.hints : 3;
  }

  function updateHintButton() {
    const remaining = Math.max(0, totalHints() - hintsUsed);
    el.hintCount.textContent = remaining;
    el.hintBtn.disabled = remaining <= 0 || foundSet.size === level.objects.length;
  }

  function persist(completed) {
    saveProgress(level.id, {
      foundIds: Array.from(foundSet),
      hintsUsed: hintsUsed,
      completed: !!completed,
    });
  }

  function drawFoundRing(obj) {
    const ring = document.createElement("div");
    ring.className = "found-ring";
    ring.style.left = obj.x + "%";
    ring.style.top = obj.y + "%";
    ring.style.width = obj.w + "%";
    ring.style.height = obj.h + "%";
    el.markers.appendChild(ring);
  }

  function drawMissMarker(xPct, yPct) {
    const marker = document.createElement("div");
    marker.className = "miss-marker";
    marker.style.left = xPct + "%";
    marker.style.top = yPct + "%";
    marker.textContent = "✕";
    el.markers.appendChild(marker);
    setTimeout(() => marker.remove(), 600);
  }

  function drawHintMarker(obj) {
    const marker = document.createElement("div");
    marker.className = "hint-marker";
    marker.style.left = (obj.x + obj.w / 2) + "%";
    marker.style.top = (obj.y + obj.h / 2) + "%";
    el.markers.appendChild(marker);
    setTimeout(() => marker.remove(), 2500);
  }

  function markFound(obj) {
    foundSet.add(obj.id);
    const li = el.objectList.querySelector('li[data-id="' + obj.id + '"]');
    if (li) li.classList.add("found");
    drawFoundRing(obj);
    updateCounters();
    updateHintButton();

    const done = foundSet.size === level.objects.length;
    persist(done);
    if (done) showWin();
  }

  function showWin() {
    el.winStats.textContent =
      "Найдено предметов: " + level.objects.length +
      ". Подсказок использовано: " + hintsUsed + ".";
    el.winOverlay.classList.remove("hidden");
  }

  function handleImageClick(evt) {
    if (!level) return;
    const rect = el.image.getBoundingClientRect();
    const clickX = ((evt.clientX - rect.left) / rect.width) * 100;
    const clickY = ((evt.clientY - rect.top) / rect.height) * 100;

    for (const obj of level.objects) {
      if (foundSet.has(obj.id)) continue;
      const x0 = obj.x - HIT_PAD;
      const y0 = obj.y - HIT_PAD;
      const x1 = obj.x + obj.w + HIT_PAD;
      const y1 = obj.y + obj.h + HIT_PAD;
      if (clickX >= x0 && clickX <= x1 && clickY >= y0 && clickY <= y1) {
        markFound(obj);
        return;
      }
    }
    drawMissMarker(clickX, clickY);
  }

  function handleHint() {
    if (!level) return;
    const remaining = totalHints() - hintsUsed;
    if (remaining <= 0) return;
    const notFound = level.objects.filter((o) => !foundSet.has(o.id));
    if (!notFound.length) return;
    const target = notFound[Math.floor(Math.random() * notFound.length)];
    drawHintMarker(target);
    hintsUsed++;
    persist(false);
    updateHintButton();
  }

  function handleReset() {
    if (!level) return;
    clearProgress(level.id);
    startLevel();
  }

  // ---------- Wiring ----------

  el.image.addEventListener("click", handleImageClick);
  el.hintBtn.addEventListener("click", handleHint);
  el.resetBtn.addEventListener("click", handleReset);
  el.btnMenu.addEventListener("click", () => {
    showScreen("menu");
    renderMenu();
  });
  el.btnBackMenu.addEventListener("click", () => {
    el.winOverlay.classList.add("hidden");
    showScreen("menu");
    renderMenu();
  });
  el.btnReplay.addEventListener("click", () => {
    el.winOverlay.classList.add("hidden");
    handleReset();
  });

  fetch("data/levels.json")
    .then((r) => r.json())
    .then((data) => {
      manifest = data;
      renderMenu();
      showScreen("menu");
    });
})();
