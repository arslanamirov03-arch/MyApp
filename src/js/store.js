/* Хранилище: блоки -> списки (миниблоки) -> слова.
   Здесь же живёт расписание повторений. Всё лежит в памяти устройства,
   интернет не нужен. */
window.Store = (function () {
  'use strict';

  var KEY = 'wortkabinett.v1';
  var MAX_WORDS = 500;

  /* Лестница повторений в днях. Слово поднимается на ступень после каждого
     верного ответа; пройденная последняя ступень означает, что слово освоено. */
  var INTERVALS = [1, 3, 7, 14, 30, 60];
  var MAX_LEVEL = INTERVALS.length;

  var DAY = 24 * 60 * 60 * 1000;

  var state = { version: 1, blocks: [] };
  var listeners = [];
  var memoryOnly = false;

  function uid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }

  /* Конец сегодняшнего дня: слово со сроком «сегодня» должно попасть
     в разбор целиком, независимо от времени добавления. */
  function endOfToday() {
    var d = new Date();
    d.setHours(23, 59, 59, 999);
    return d.getTime();
  }

  function startOfDay(ts) {
    var d = new Date(ts);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  }

  function load() {
    try {
      var raw = localStorage.getItem(KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && Array.isArray(parsed.blocks)) state = normalize(parsed);
      }
    } catch (e) {
      /* Приватный режим или запрет на хранение — работаем без сохранения. */
      memoryOnly = true;
      console.warn('Сохранение недоступно, данные будут жить только до закрытия вкладки', e);
    }
    return state;
  }

  function normalize(data) {
    return {
      version: 1,
      blocks: (data.blocks || []).filter(Boolean).map(function (b) {
        return {
          id: b.id || uid(),
          title: String(b.title || 'Без названия'),
          createdAt: b.createdAt || Date.now(),
          sets: (b.sets || []).filter(Boolean).map(function (s) {
            return {
              id: s.id || uid(),
              title: String(s.title || 'Список'),
              createdAt: s.createdAt || Date.now(),
              words: (s.words || []).filter(Boolean).map(normalizeWord)
            };
          })
        };
      })
    };
  }

  function normalizeWord(w) {
    var created = w.createdAt || Date.now();
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

  var saveTimer = null;
  function save() {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      if (memoryOnly) return;
      try {
        localStorage.setItem(KEY, JSON.stringify(state));
      } catch (e) {
        memoryOnly = true;
        console.warn('Не удалось сохранить данные', e);
      }
    }, 60);
    listeners.forEach(function (fn) { fn(state); });
  }

  function subscribe(fn) { listeners.push(fn); }
  function getState() { return state; }
  function isMemoryOnly() { return memoryOnly; }

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
      createdAt: Date.now(),
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
      createdAt: Date.now(),
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
    save();
  }

  /* ---------- Слова ---------- */

  function makeWord(de, ru) {
    var now = Date.now();
    return {
      id: uid(), de: de, ru: ru, createdAt: now,
      level: 0, due: now, reps: 0, lapses: 0, lastReviewed: null, mastered: false
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

  /* Все слова с указанием, где они лежат. scope: {} | {blockId} | {blockId,setId} */
  function collectWords(scope) {
    scope = scope || {};
    var result = [];
    state.blocks.forEach(function (block) {
      if (scope.blockId && block.id !== scope.blockId) return;
      block.sets.forEach(function (set) {
        if (scope.setId && set.id !== scope.setId) return;
        set.words.forEach(function (word) {
          result.push({ word: word, blockId: block.id, setId: set.id, blockTitle: block.title, setTitle: set.title });
        });
      });
    });
    return result;
  }

  function isDue(word, at) {
    if (word.mastered) return false;
    /* Вопрос в разборе — это перевод, поэтому без перевода спрашивать нечего. */
    if (!word.ru) return false;
    return word.due <= (at || endOfToday());
  }

  function dueCount(scope) {
    var limit = endOfToday();
    return collectWords(scope).filter(function (item) { return isDue(item.word, limit); }).length;
  }

  function stats(scope) {
    var items = collectWords(scope);
    var limit = endOfToday();
    var due = 0, mastered = 0, learning = 0, fresh = 0;
    items.forEach(function (item) {
      var w = item.word;
      if (w.mastered) mastered++;
      else if (isDue(w, limit)) due++;
      if (!w.mastered && w.level > 0) learning++;
      if (w.level === 0 && !w.reps) fresh++;
    });
    return { total: items.length, due: due, mastered: mastered, learning: learning, fresh: fresh };
  }

  /* Очередь на сегодня: перемешана, чтобы порядок слов не заучивался. */
  function buildQueue(scope) {
    var limit = endOfToday();
    var queue = collectWords(scope).filter(function (item) { return isDue(item.word, limit); });
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

  /* outcome: 'correct' — верно, 'typo' — засчитать как верное, 'forgot' — сброс.
     Возвращает описание нового срока для показа пользователю. */
  function reviewWord(wordId, outcome) {
    var found = null;
    state.blocks.forEach(function (block) {
      block.sets.forEach(function (set) {
        set.words.forEach(function (w) { if (w.id === wordId) found = w; });
      });
    });
    if (!found) return null;

    found.reps++;
    found.lastReviewed = Date.now();

    if (outcome === 'forgot') {
      found.level = 0;
      found.lapses++;
      found.mastered = false;
      found.due = Date.now();
      save();
      return { level: 0, days: 0, mastered: false, again: true };
    }

    var previousLevel = found.level;
    found.level = Math.min(found.level + 1, MAX_LEVEL);

    if (previousLevel >= MAX_LEVEL) {
      /* Слово уже отстояло последний интервал в 60 дней и снова названо
         верно — дальше держать его в разборе незачем. */
      found.mastered = true;
      found.due = startOfDay(Date.now()) + INTERVALS[MAX_LEVEL - 1] * DAY;
      save();
      return { level: found.level, days: 0, mastered: true, again: false };
    }

    var days = INTERVALS[found.level - 1];
    found.due = startOfDay(Date.now()) + days * DAY;
    save();
    return { level: found.level, days: days, mastered: false, again: false };
  }

  /* Через сколько дней слово всплывёт снова (для показа в ведомости). */
  function dueLabel(word) {
    if (word.mastered) return 'освоено';
    var today = startOfDay(Date.now());
    var due = startOfDay(word.due);
    var diff = Math.round((due - today) / DAY);
    if (diff <= 0) return 'сегодня';
    if (diff === 1) return 'завтра';
    return 'через ' + diff + ' дн.';
  }

  /* ---------- Архив ---------- */

  function exportJson() {
    return JSON.stringify({ version: 1, exportedAt: Date.now(), blocks: state.blocks }, null, 2);
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
    save();
    return incoming.blocks.length;
  }

  return {
    MAX_WORDS: MAX_WORDS,
    INTERVALS: INTERVALS,
    MAX_LEVEL: MAX_LEVEL,
    load: load, save: save, subscribe: subscribe, getState: getState, isMemoryOnly: isMemoryOnly,
    getBlock: getBlock, getSet: getSet, countWords: countWords,
    addBlock: addBlock, renameBlock: renameBlock, removeBlock: removeBlock,
    addSet: addSet, renameSet: renameSet, removeSet: removeSet,
    addWord: addWord, addWordsBulk: addWordsBulk, updateWord: updateWord, removeWord: removeWord,
    collectWords: collectWords, dueCount: dueCount, stats: stats, buildQueue: buildQueue,
    checkAnswer: checkAnswer, reviewWord: reviewWord, dueLabel: dueLabel, isDue: isDue,
    exportJson: exportJson, importJson: importJson
  };
})();
