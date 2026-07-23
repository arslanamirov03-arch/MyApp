(function () {
  "use strict";

  const STORAGE_PREFIX = "hog_progress_";
  const HIT_PAD = 2.5; // percentage points of forgiveness added around each hotspot

  // levels that gate the room renovation: finishing N of them unlocks stage N
  const GATING_LEVELS = ["library", "beach", "castle"];
  const RENOVATION_KEY = "hog_renovation";

  const STAGE_CONFIG = {
    stage1: {
      title: "Ремонт 1: Стены и мебель",
      sub: "Выберите настроение комнаты",
      hologram: true,
      options: [
        { key: "light", label: "Светлая", bg: "#f3ecd8", fg: "#3a2f1d" },
        { key: "dark", label: "Тёмная", bg: "#232c3a", fg: "#f0f0f0" },
      ],
    },
    stage2: {
      title: "Ремонт 2: Стол",
      sub: "Выберите цвет стола",
      options: [
        { key: "white", label: "Белый", bg: "#ffffff", fg: "#333333" },
        { key: "gray", label: "Серый", bg: "#9a9a9a", fg: "#ffffff" },
      ],
    },
    stage3: {
      title: "Ремонт 3: Мебель",
      sub: "Выберите цвет",
      options: [
        { key: "black", label: "Чёрный", bg: "#1a1a1a", fg: "#ffffff" },
        { key: "white", label: "Белый", bg: "#ffffff", fg: "#333333" },
      ],
      categories: [
        { key: "armchairs", label: "Кресла" },
        { key: "bookshelf", label: "Стеллаж" },
        { key: "lamp", label: "Лампа" },
      ],
    },
  };

  // hand-mapped bounding boxes (percent left,top,width,height) locating each
  // recolorable piece of furniture in room-before.jpg, found with the same
  // pixel-grid method used for the hidden-object hotspots
  const RENO_REGIONS = {
    table:     { left: 44.4, top: 44.9, width: 19.9, height: 35.6 },
    armchairs: { left: 79.6, top: 47.9, width: 13.1, height: 32.2 },
    bookshelf: { left: 72.4, top: 24.1, width: 13.1, height: 21.5 },
    lamp:      { left: 91.6, top: 33.2, width: 8.4,  height: 10.4 },
  };

  const SWATCH_HEX = { light: "#f3ecd8", dark: "#232c3a", white: "#ffffff", gray: "#8a8a8a", black: "#1a1a1a" };

  const el = {
    menu: document.getElementById("screen-menu"),
    game: document.getElementById("screen-game"),
    renovation: document.getElementById("screen-renovation"),
    grid: document.getElementById("level-grid"),
    btnMenu: document.getElementById("btn-menu"),
    tabLevels: document.getElementById("tab-levels"),
    tabRenovation: document.getElementById("tab-renovation"),
    renoSlots: document.getElementById("reno-slots"),
    renoOverlay: document.getElementById("reno-overlay-layer"),
    renoModal: document.getElementById("reno-modal"),
    renoModalCard: document.getElementById("reno-modal-card"),
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
    el.renovation.classList.toggle("hidden", name !== "renovation");
    el.btnMenu.classList.toggle("hidden", name !== "game");
    el.tabLevels.parentElement.classList.toggle("hidden", name === "game");
    el.tabLevels.classList.toggle("active", name === "menu");
    el.tabRenovation.classList.toggle("active", name === "renovation");
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

  // an object is normally one hotspot ({x,y,w,h}), but some scenes have the
  // same item appear twice (e.g. two turtles) - "regions" lets either count
  function getRegions(obj) {
    return obj.regions && obj.regions.length ? obj.regions : [{ x: obj.x, y: obj.y, w: obj.w, h: obj.h }];
  }

  function drawFoundRing(obj) {
    getRegions(obj).forEach((r) => {
      const ring = document.createElement("div");
      ring.className = "found-ring";
      ring.style.left = r.x + "%";
      ring.style.top = r.y + "%";
      ring.style.width = r.w + "%";
      ring.style.height = r.h + "%";
      el.markers.appendChild(ring);
    });
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
    getRegions(obj).forEach((r) => {
      const marker = document.createElement("div");
      marker.className = "hint-marker";
      marker.style.left = (r.x + r.w / 2) + "%";
      marker.style.top = (r.y + r.h / 2) + "%";
      el.markers.appendChild(marker);
      setTimeout(() => marker.remove(), 2500);
    });
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
    let text =
      "Найдено предметов: " + level.objects.length +
      ". Подсказок использовано: " + hintsUsed + ".";
    if (GATING_LEVELS.includes(level.id)) {
      text += " Открыт новый шаг ремонта — загляните в раздел «🏠 Ремонт»!";
    }
    el.winStats.textContent = text;
    el.winOverlay.classList.remove("hidden");
  }

  function handleImageClick(evt) {
    if (!level) return;
    const rect = el.image.getBoundingClientRect();
    const clickX = ((evt.clientX - rect.left) / rect.width) * 100;
    const clickY = ((evt.clientY - rect.top) / rect.height) * 100;

    for (const obj of level.objects) {
      if (foundSet.has(obj.id)) continue;
      for (const r of getRegions(obj)) {
        const x0 = r.x - HIT_PAD;
        const y0 = r.y - HIT_PAD;
        const x1 = r.x + r.w + HIT_PAD;
        const y1 = r.y + r.h + HIT_PAD;
        if (clickX >= x0 && clickX <= x1 && clickY >= y0 && clickY <= y1) {
          markFound(obj);
          return;
        }
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

  // ---------- Renovation ----------

  function getRenovation() {
    try {
      const raw = localStorage.getItem(RENOVATION_KEY);
      if (!raw) throw new Error("empty");
      const parsed = JSON.parse(raw);
      parsed.stage3 = parsed.stage3 || {};
      return parsed;
    } catch (e) {
      return { stage1: null, stage2: null, stage3: { armchairs: null, bookshelf: null, lamp: null } };
    }
  }

  function saveRenovation(data) {
    localStorage.setItem(RENOVATION_KEY, JSON.stringify(data));
  }

  function completedLevelsCount() {
    return GATING_LEVELS.filter((id) => getProgress(id).completed).length;
  }

  function findOption(stageKey, optionKey) {
    return STAGE_CONFIG[stageKey].options.find((o) => o.key === optionKey);
  }

  function renoChoiceChipHTML(stageKey, categoryKey, optionKey) {
    const opt = findOption(stageKey, optionKey);
    const catAttr = categoryKey ? ` data-category="${categoryKey}"` : "";
    return (
      '<span class="reno-choice-chip">' +
      '<span class="swatch-dot" style="background:' + opt.bg + '"></span>' +
      opt.label +
      '<button type="button" class="reno-x" data-action="reset" data-stage="' + stageKey + '"' + catAttr + '>✕</button>' +
      "</span>"
    );
  }

  function renderRenovation() {
    const count = completedLevelsCount();
    const reno = getRenovation();
    const parts = [];

    [1, 2, 3].forEach((stageNum) => {
      const stageKey = "stage" + stageNum;
      const cfg = STAGE_CONFIG[stageKey];
      const unlocked = count >= stageNum;

      let inner;
      if (!unlocked) {
        inner = '<p class="reno-hint">🔒 Откроется после ' + stageNum + ' пройденных уровней (сейчас: ' + count + ')</p>';
      } else if (stageKey === "stage3") {
        inner = cfg.categories
          .map((cat) => {
            const chosen = reno.stage3[cat.key];
            const right = chosen
              ? renoChoiceChipHTML(stageKey, cat.key, chosen)
              : '<button type="button" class="reno-pick-btn" data-action="pick" data-stage="' + stageKey + '" data-category="' + cat.key + '">Выбрать</button>';
            return '<div class="reno-row"><span class="reno-cat-label">' + cat.label + "</span>" + right + "</div>";
          })
          .join("");
      } else {
        const chosen = reno[stageKey];
        inner = chosen
          ? '<div class="reno-row">' + renoChoiceChipHTML(stageKey, null, chosen) + "</div>"
          : '<button type="button" class="reno-pick-btn" data-action="pick" data-stage="' + stageKey + '">Выбрать</button>';
      }

      parts.push(
        '<div class="reno-slot' + (unlocked ? "" : " locked") + '">' +
        "<h4>" + cfg.title + "</h4>" +
        inner +
        "</div>"
      );
    });

    el.renoSlots.innerHTML = parts.join("");
    renderRenoVisuals(reno);
  }

  function addRegionOverlay(regionKey, colorKey, full) {
    if (full) {
      const div = document.createElement("div");
      div.className = "reno-overlay-full reno-mood-" + colorKey;
      el.renoOverlay.appendChild(div);
      return;
    }
    const box = RENO_REGIONS[regionKey];
    const color = SWATCH_HEX[colorKey];
    const div = document.createElement("div");
    div.className = "reno-overlay-region reno-color-" + colorKey;
    div.style.left = box.left + "%";
    div.style.top = box.top + "%";
    div.style.width = box.width + "%";
    div.style.height = box.height + "%";
    div.style.background =
      "radial-gradient(ellipse closest-side at 50% 50%, " + color + " 0%, " + color + " 45%, transparent 100%)";
    el.renoOverlay.appendChild(div);
  }

  function renderRenoVisuals(reno) {
    el.renoOverlay.innerHTML = "";
    if (reno.stage1) addRegionOverlay(null, reno.stage1, true);
    if (reno.stage2) addRegionOverlay("table", reno.stage2, false);
    if (reno.stage3.armchairs) addRegionOverlay("armchairs", reno.stage3.armchairs, false);
    if (reno.stage3.bookshelf) addRegionOverlay("bookshelf", reno.stage3.bookshelf, false);
    if (reno.stage3.lamp) addRegionOverlay("lamp", reno.stage3.lamp, false);
  }

  function openRenoModal(stageKey, categoryKey) {
    const cfg = STAGE_CONFIG[stageKey];
    const catAttr = categoryKey ? ' data-category="' + categoryKey + '"' : "";
    const swatches = cfg.options
      .map(
        (o) =>
          '<button type="button" class="swatch-btn" style="background:' + o.bg + ";color:" + o.fg + '" data-option="' + o.key + '">' + o.label + "</button>"
      )
      .join("");
    const category = categoryKey ? cfg.categories.find((c) => c.key === categoryKey) : null;

    el.renoModalCard.innerHTML =
      "<h3>" + cfg.title + "</h3>" +
      '<p class="reno-modal-sub">' + (category ? category.label + " — " + cfg.sub : cfg.sub) + "</p>" +
      '<div class="' + (cfg.hologram ? "reno-hologram" : "") + '">' +
      '<div class="swatch-row" data-stage="' + stageKey + '"' + catAttr + ">" + swatches + "</div>" +
      "</div>" +
      '<button type="button" class="reno-modal-close">Отмена</button>';

    el.renoModalCard.querySelectorAll(".swatch-btn").forEach((btn) => {
      btn.addEventListener("click", () => chooseRenoOption(stageKey, categoryKey, btn.dataset.option));
    });
    el.renoModalCard.querySelector(".reno-modal-close").addEventListener("click", closeRenoModal);

    el.renoModal.classList.remove("hidden");
  }

  function closeRenoModal() {
    el.renoModal.classList.add("hidden");
  }

  function chooseRenoOption(stageKey, categoryKey, optionKey) {
    const reno = getRenovation();
    if (stageKey === "stage3") {
      reno.stage3[categoryKey] = optionKey;
    } else {
      reno[stageKey] = optionKey;
    }
    saveRenovation(reno);
    closeRenoModal();
    renderRenovation();
  }

  function resetRenoChoice(stageKey, categoryKey) {
    const reno = getRenovation();
    if (stageKey === "stage3") {
      reno.stage3[categoryKey] = null;
    } else {
      reno[stageKey] = null;
    }
    saveRenovation(reno);
    renderRenovation();
    openRenoModal(stageKey, categoryKey);
  }

  el.renoSlots.addEventListener("click", (evt) => {
    const btn = evt.target.closest("[data-action]");
    if (!btn) return;
    const stageKey = btn.dataset.stage;
    const categoryKey = btn.dataset.category || null;
    if (btn.dataset.action === "pick") openRenoModal(stageKey, categoryKey);
    else if (btn.dataset.action === "reset") resetRenoChoice(stageKey, categoryKey);
  });

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
  el.tabLevels.addEventListener("click", () => {
    showScreen("menu");
    renderMenu();
  });
  el.tabRenovation.addEventListener("click", () => {
    showScreen("renovation");
    renderRenovation();
  });

  fetch("data/levels.json")
    .then((r) => r.json())
    .then((data) => {
      manifest = data;
      renderMenu();
      showScreen("menu");
    });
})();
