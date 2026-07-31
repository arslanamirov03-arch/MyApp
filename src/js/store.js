/* Хранилище и расписание повторений.

   Учебный день начинается в 6 утра, а не в полночь: слово, добавленное
   вечером, ждёт не «того же часа» на следующие сутки, а появляется
   в 6:00 следующего учебного дня. Так же считаются и все остальные
   ступени лестницы — от 1 дня до 60.

   Всё лежит в памяти устройства, интернет не нужен. */
window.Store = (function () {
  'use strict';

  var KEY = 'wortschatz.state';
  var KEY_SPARE = 'wortschatz.state.spare';
  var LEGACY_KEY = 'wortkabinett.v1';
  var DB_NAME = 'wortschatz';
  var DB_STORE = 'state';

  var MAX_WORDS = 500;

  /* Лестница повторений в днях. Слово поднимается на ступень после каждого
     верного ответа; пройденная последняя ступень означает, что слово освоено. */
  var INTERVALS = [1, 3, 7, 14, 30, 60];
  var MAX_LEVEL = INTERVALS.length;

  var DAY = 24 * 60 * 60 * 1000;
  var DAY_START_HOUR = 6;

  var state = { version: 2, blocks: [], settings: { collapsed: {} } };
  var listeners = [];
  var memoryOnly = false;
  var lastSavedAt = 0;
  var clockWentBack = false;

  function uid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }

  function now() { return Date.now(); }

  /* ---------- Время ---------- */

  /* Начало учебного дня, которому принадлежит момент ts.
     До 6 утра идёт ещё вчерашний учебный день — ночь принадлежит вечеру. */
  function studyDayStart(ts) {
    var d = new Date(ts == null ? now() : ts);
    if (d.getHours() < DAY_START_HOUR) d.setDate(d.getDate() - 1);
    d.setHours(DAY_START_HOUR, 0, 0, 0);
    return d.getTime();
  }

  /* Момент, когда слово всплывёт: 6:00 через указанное число дней.
     Считаем через календарь, чтобы перевод часов не сдвигал время. */
  function dueAfterDays(days, from) {
    var d = new Date(studyDayStart(from));
    d.setDate(d.getDate() + days);
    d.setHours(DAY_START_HOUR, 0, 0, 0);
    return d.getTime();
  }

  function studyDaysBetween(fromTs, toTs) {
    return Math.round((studyDayStart(toTs) - studyDayStart(fromTs)) / DAY);
  }

  /* ---------- Чтение и запись ---------- */

  function readKey(key) {
    try {
      var raw = localStorage.getItem(key);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      if (!parsed || !Array.isArray(parsed.blocks)) return null;
      return parsed;
    } catch (e) {
      return null;
    }
  }

  function load() {
    var candidates = [];
    try {
      var main = readKey(KEY);
      var spare = readKey(KEY_SPARE);
      var legacy = readKey(LEGACY_KEY);
      if (main) candidates.push(main);
      if (spare) candidates.push(spare);
      if (legacy) candidates.push(legacy);
    } catch (e) {
      memoryOnly = true;
      console.warn('Хранилище недоступно, данные будут жить только до закрытия', e);
      return state;
    }

    if (candidates.length) {
      /* Две копии пишутся по очереди: если запись оборвалась, целой
         останется хотя бы одна — берём ту, что новее. */
      candidates.sort(function (a, b) { return (b.savedAt || 0) - (a.savedAt || 0); });
      state = normalize(candidates[0]);
      lastSavedAt = candidates[0].savedAt || 0;
    }

    /* Часы устройства ушли назад — расписание могло съехать. */
    if (lastSavedAt && now() < lastSavedAt - 12 * 60 * 60 * 1000) clockWentBack = true;

    restoreFromDatabase();
    return state;
  }

  function normalize(data) {
    var settings = data.settings || {};
    return {
      version: 2,
      settings: { collapsed: settings.collapsed || {} },
      blocks: (data.blocks || []).filter(Boolean).map(function (b) {
        return {
          id: b.id || uid(),
          title: String(b.title || 'Без названия'),
          createdAt: b.createdAt || now(),
          sets: (b.sets || []).filter(Boolean).map(function (s) {
            return {
              id: s.id || uid(),
              title: String(s.title || 'Список'),
              createdAt: s.createdAt || now(),
              words: (s.words || []).filter(Boolean).map(normalizeWord)
            };
          })
        };
      })
    };
  }

  function normalizeWord(w) {
    var created = w.createdAt || now();
    return {
      id: w.id || uid(),
      de: String(w.de || ''),
      ru: String(w.ru || ''),
      createdAt: created,
      level: typeof w.level === 'number' ? w.level : 0,
      due: typeof w.due === 'number' ? w.due : created,
      reps: w.reps || 0,
      lapses: w.lapses || 0,
      lastReviewed: w.lastReviewed || null,
      mastered: !!w.mastered
    };
  }

  function serialize() {
    return JSON.stringify({
      version: 2,
      savedAt: now(),
      settings: state.settings,
      blocks: state.blocks
    });
  }

  /* Запись идёт в две ячейки по очереди, чтобы обрыв на середине
     не оставил приложение вообще без данных. */
  function writeNow() {
    if (memoryOnly) return;
    var payload = serialize();
    try {
      localStorage.setItem(KEY_SPARE, localStorage.getItem(KEY) || payload);
      localStorage.setItem(KEY, payload);
      lastSavedAt = now();
      mirrorToDatabase(payload);
    } catch (e) {
      memoryOnly = true;
      console.warn('Не удалось сохранить данные', e);
    }
  }

  var saveTimer = null;
  function save() {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(function () { saveTimer = null; writeNow(); }, 60);
  }

  /* Немедленная запись: вызывается перед уходом со страницы. */
  function flush() {
    if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; }
    writeNow();
  }

  /* ---------- Второй экземпляр в базе данных ---------- */

  function openDatabase(callback) {
    try {
      if (!window.indexedDB) { callback(null); return; }
      var request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = function () {
        var db = request.result;
        if (!db.objectStoreNames.contains(DB_STORE)) db.createObjectStore(DB_STORE);
      };
      request.onsuccess = function () { callback(request.result); };
      request.onerror = function () { callback(null); };
    } catch (e) {
      callback(null);
    }
  }

  function mirrorToDatabase(payload) {
    openDatabase(function (db) {
      if (!db) return;
      try {
        db.transaction(DB_STORE, 'readwrite').objectStore(DB_STORE).put(payload, 'main');
      } catch (e) { /* зеркало не критично */ }
    });
  }

  /* Если хранилище браузера почистили, а база уцелела — поднимаем оттуда. */
  function restoreFromDatabase() {
    openDatabase(function (db) {
      if (!db) return;
      try {
        var request = db.transaction(DB_STORE, 'readonly').objectStore(DB_STORE).get('main');
        request.onsuccess = function () {
          var raw = request.result;
          if (!raw) return;
          var parsed;
          try { parsed = JSON.parse(raw); } catch (e) { return; }
          if (!parsed || !Array.isArray(parsed.blocks)) return;
          if ((parsed.savedAt || 0) <= lastSavedAt) return;

          state = normalize(parsed);
          lastSavedAt = parsed.savedAt || 0;
          listeners.forEach(function (fn) { fn(state); });
        };
      } catch (e) { /* восстановление не удалось — работаем с тем, что есть */ }
    });
  }

  /* Подписка нужна только для случая, когда данные пришли со стороны —
     из запасной копии в базе. Обычные изменения экран перерисовывает сам. */
  function subscribe(fn) { listeners.push(fn); }
  function getState() { return state; }
  function isMemoryOnly() { return memoryOnly; }
  function clockSuspicious() { return clockWentBack; }
  function savedAt() { return lastSavedAt; }

  /* ---------- Настройки ---------- */

  function isCollapsed(setId) { return !!state.settings.collapsed[setId]; }

  function toggleCollapsed(setId) {
    if (state.settings.collapsed[setId]) delete state.settings.collapsed[setId];
    else state.settings.collapsed[setId] = true;
    save();
    return isCollapsed(setId);
  }

  /* ---------- Доступ ---------- */

  function getBlock(blockId) {
    return state.blocks.find(function (b) { return b.id === blockId; }) || null;
  }

  function getSet(blockId, setId) {
    var block = getBlock(blockId);
    if (!block) return null;
    return block.sets.find(function (s) { return s.id === setId; }) || null;
  }

  function countWords(block) {
    return block.sets.reduce(function (sum, s) { return sum + s.words.length; }, 0);
  }

  /* ---------- Блоки ---------- */

  function addBlock(title) {
    var block = {
      id: uid(),
      title: String(title || '').trim() || 'Новый блок',
      createdAt: now(),
      sets: []
    };
    state.blocks.push(block);
    save();
    return block;
  }

  function renameBlock(blockId, title) {
    var block = getBlock(blockId);
    if (!block) return;
    block.title = String(title || '').trim() || block.title;
    save();
  }

  function removeBlock(blockId) {
    state.blocks = state.blocks.filter(function (b) { return b.id !== blockId; });
    save();
  }

  /* ---------- Списки ---------- */

  function addSet(blockId, title) {
    var block = getBlock(blockId);
    if (!block) return null;
    var set = {
      id: uid(),
      title: String(title || '').trim() || ('Список ' + (block.sets.length + 1)),
      createdAt: now(),
      words: []
    };
    block.sets.push(set);
    save();
    return set;
  }

  function renameSet(blockId, setId, title) {
    var set = getSet(blockId, setId);
    if (!set) return;
    set.title = String(title || '').trim() || set.title;
    save();
  }

  function removeSet(blockId, setId) {
    var block = getBlock(blockId);
    if (!block) return;
    block.sets = block.sets.filter(function (s) { return s.id !== setId; });
    delete state.settings.collapsed[setId];
    save();
  }

  /* ---------- Слова ---------- */

  function makeWord(de, ru) {
    var ts = now();
    return {
      id: uid(), de: de, ru: ru, createdAt: ts,
      level: 0, due: ts, reps: 0, lapses: 0, lastReviewed: null, mastered: false
    };
  }

  function addWord(blockId, setId, de, ru) {
    var set = getSet(blockId, setId);
    if (!set) return { ok: false, reason: 'Список не найден' };
    if (set.words.length >= MAX_WORDS) {
      return { ok: false, reason: 'Список заполнен: ' + MAX_WORDS + ' слов' };
    }
    de = String(de || '').trim();
    ru = String(ru || '').trim();
    if (!de) return { ok: false, reason: 'Введите слово' };

    var word = makeWord(de, ru);
    set.words.push(word);
    save();
    return { ok: true, word: word, remaining: MAX_WORDS - set.words.length };
  }

  /* Массовый ввод: одна пара на строку, разделитель  -  ;  —  таб  или : */
  function addWordsBulk(blockId, setId, text) {
    var set = getSet(blockId, setId);
    if (!set) return { added: 0, skipped: 0, overflow: 0 };
    var lines = String(text || '').split(/\r?\n/);
    var added = 0, skipped = 0, overflow = 0;

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].trim();
      if (!line) continue;
      if (set.words.length >= MAX_WORDS) { overflow++; continue; }

      var parts = line.split(/\t|\s+[-—–=]\s+|\s*[;|]\s*|\s*:\s+/);
      var de = (parts[0] || '').trim().replace(/^\s*\d+[.)]\s*/, '');
      var ru = parts.slice(1).join(' ').trim();
      if (!de) { skipped++; continue; }

      set.words.push(makeWord(de, ru));
      added++;
    }
    save();
    return { added: added, skipped: skipped, overflow: overflow };
  }

  function updateWord(blockId, setId, wordId, de, ru) {
    var set = getSet(blockId, setId);
    if (!set) return;
    var word = set.words.find(function (w) { return w.id === wordId; });
    if (!word) return;
    word.de = String(de || '').trim() || word.de;
    word.ru = String(ru || '').trim();
    save();
  }

  function removeWord(blockId, setId, wordId) {
    var set = getSet(blockId, setId);
    if (!set) return;
    set.words = set.words.filter(function (w) { return w.id !== wordId; });
    save();
  }

  /* ---------- Повторения ---------- */

  function collectWords(scope) {
    scope = scope || {};
    var result = [];
    state.blocks.forEach(function (block) {
      if (scope.blockId && block.id !== scope.blockId) return;
      block.sets.forEach(function (set) {
        if (scope.setId && set.id !== scope.setId) return;
        set.words.forEach(function (word) {
          result.push({
            word: word, blockId: block.id, setId: set.id,
            blockTitle: block.title, setTitle: set.title
          });
        });
      });
    });
    return result;
  }

  /* Слово готово, когда наступил его час — то есть 6 утра назначенного дня. */
  function isDue(word, at) {
    if (word.mastered) return false;
    /* Вопрос в разборе — это перевод, поэтому без перевода спрашивать нечего. */
    if (!word.ru) return false;
    return word.due <= (at || now());
  }

  function dueCount(scope, at) {
    var moment = at || now();
    return collectWords(scope).filter(function (item) { return isDue(item.word, moment); }).length;
  }

  function stats(scope, at) {
    var moment = at || now();
    var items = collectWords(scope);
    var due = 0, mastered = 0, learning = 0, waiting = 0;
    items.forEach(function (item) {
      var w = item.word;
      if (w.mastered) { mastered++; return; }
      if (isDue(w, moment)) due++;
      else waiting++;
      if (w.level > 0) learning++;
    });
    return { total: items.length, due: due, mastered: mastered, learning: learning, waiting: waiting };
  }

  /* Когда всплывёт ближайшее слово, которое сейчас ещё спит. */
  function nextDueAt(scope, at) {
    var moment = at || now();
    var soonest = null;
    collectWords(scope).forEach(function (item) {
      var w = item.word;
      if (w.mastered || !w.ru) return;
      if (w.due <= moment) return;
      if (soonest === null || w.due < soonest) soonest = w.due;
    });
    return soonest;
  }

  /* Очередь на сейчас: перемешана, чтобы порядок слов не заучивался. */
  function buildQueue(scope, at) {
    var moment = at || now();
    var queue = collectWords(scope).filter(function (item) { return isDue(item.word, moment); });
    for (var i = queue.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = queue[i];
      queue[i] = queue[j];
      queue[j] = tmp;
    }
    return queue;
  }

  /* Сравнение ответа: регистр и лишние пробелы не считаются ошибкой,
     а вот буквы — включая умляуты и ß — должны совпадать. */
  function normalizeAnswer(str) {
    return String(str == null ? '' : str)
      .replace(/\s+/g, ' ')
      .replace(/[.,!?;]+$/, '')
      .trim()
      .toLowerCase();
  }

  function checkAnswer(word, answer) {
    return normalizeAnswer(answer) === normalizeAnswer(word.de);
  }

  function findWord(wordId) {
    var found = null;
    state.blocks.forEach(function (block) {
      block.sets.forEach(function (set) {
        set.words.forEach(function (w) { if (w.id === wordId) found = w; });
      });
    });
    return found;
  }

  /* outcome: 'correct' — верно, 'typo' — засчитать как верное, 'forgot' — сброс. */
  function reviewWord(wordId, outcome, at) {
    var moment = at || now();
    var word = findWord(wordId);
    if (!word) return null;

    word.reps++;
    word.lastReviewed = moment;

    if (outcome === 'forgot') {
      word.level = 0;
      word.lapses++;
      word.mastered = false;
      word.due = moment;
      save();
      return { level: 0, days: 0, due: moment, mastered: false, again: true };
    }

    var previousLevel = word.level;
    word.level = Math.min(word.level + 1, MAX_LEVEL);

    if (previousLevel >= MAX_LEVEL) {
      /* Слово уже отстояло последний интервал в 60 дней и снова названо
         верно — дальше держать его в разборе незачем. */
      word.mastered = true;
      word.due = dueAfterDays(INTERVALS[MAX_LEVEL - 1], moment);
      save();
      return { level: word.level, days: 0, due: word.due, mastered: true, again: false };
    }

    var days = INTERVALS[word.level - 1];
    word.due = dueAfterDays(days, moment);
    save();
    return { level: word.level, days: days, due: word.due, mastered: false, again: false };
  }

  /* Расписание считается учебными днями, а подпись — обычными:
     в 5:59 слово со сроком «сегодня в 6:00» должно читаться как «в 06:00»,
     а не как «завтра», хотя учебный день ещё вчерашний. */
  function calendarDayStart(ts) {
    var d = new Date(ts);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  }

  function calendarDaysBetween(fromTs, toTs) {
    return Math.round((calendarDayStart(toTs) - calendarDayStart(fromTs)) / DAY);
  }

  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  /* Когда слово всплывёт снова — коротко, для списка. */
  function dueLabel(word, at) {
    if (word.mastered) return 'освоено';
    if (!word.ru) return 'нет перевода';
    var moment = at || now();
    if (word.due <= moment) return 'сейчас';

    var diff = calendarDaysBetween(moment, word.due);
    if (diff <= 0) {
      var d = new Date(word.due);
      return 'в ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    }
    if (diff === 1) return 'завтра';
    return 'через ' + diff + ' дн.';
  }

  /* ---------- Архив ---------- */

  function exportJson() {
    return JSON.stringify({
      version: 2, exportedAt: now(), settings: state.settings, blocks: state.blocks
    }, null, 2);
  }

  function importJson(text, mode) {
    var parsed = JSON.parse(text);
    if (!parsed || !Array.isArray(parsed.blocks)) throw new Error('в файле нет блоков');
    var incoming = normalize(parsed);
    if (mode === 'replace') {
      state = incoming;
    } else {
      incoming.blocks.forEach(function (b) {
        b.id = uid();
        b.sets.forEach(function (s) {
          s.id = uid();
          s.words.forEach(function (w) { w.id = uid(); });
        });
        state.blocks.push(b);
      });
    }
    flush();
    return incoming.blocks.length;
  }

  return {
    MAX_WORDS: MAX_WORDS,
    INTERVALS: INTERVALS,
    MAX_LEVEL: MAX_LEVEL,
    DAY_START_HOUR: DAY_START_HOUR,
    DAY: DAY,

    load: load, save: save, flush: flush, subscribe: subscribe, getState: getState,
    isMemoryOnly: isMemoryOnly, clockSuspicious: clockSuspicious, savedAt: savedAt,

    studyDayStart: studyDayStart, dueAfterDays: dueAfterDays, studyDaysBetween: studyDaysBetween,
    calendarDaysBetween: calendarDaysBetween,

    isCollapsed: isCollapsed, toggleCollapsed: toggleCollapsed,

    getBlock: getBlock, getSet: getSet, countWords: countWords,
    addBlock: addBlock, renameBlock: renameBlock, removeBlock: removeBlock,
    addSet: addSet, renameSet: renameSet, removeSet: removeSet,
    addWord: addWord, addWordsBulk: addWordsBulk, updateWord: updateWord, removeWord: removeWord,

    collectWords: collectWords, dueCount: dueCount, stats: stats, buildQueue: buildQueue,
    nextDueAt: nextDueAt, checkAnswer: checkAnswer, reviewWord: reviewWord,
    dueLabel: dueLabel, isDue: isDue,

    exportJson: exportJson, importJson: importJson
  };
})();
