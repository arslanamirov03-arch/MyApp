/* Интерфейс: блоки → списки → слова, плюс разбор по интервалам.
   Переходы пишутся в history, поэтому системная кнопка «назад» работает. */
(function () {
  'use strict';

  var Store = window.Store;
  var MAX = Store.MAX_WORDS;

  var screenEl = document.getElementById('screen');
  var crumbsEl = document.getElementById('crumbs');
  var footerEl = document.getElementById('footer-stats');
  var subEl = document.getElementById('masthead-sub');
  var modalRoot = document.getElementById('modal-root');
  var toastEl = document.getElementById('toast');
  var fab = document.getElementById('fab');

  var route = { name: 'blocks' };
  var filter = '';
  var session = null;
  var advanceTimer = null;

  /* Элементы, которые уже показывались: только новые въезжают с анимацией. */
  var seen = new Set();

  /* Значки рисуем сами: символы вроде ✎ в системных шрифтах выглядят
     по-разному, а то и подменяются на посторонний глиф. */
  var ICON_EDIT = '<svg viewBox="0 0 20 20" width="15" height="15" fill="none" stroke="currentColor" ' +
    'stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M13 3.4l3.6 3.6L7.2 16.4l-4 .4.4-4z"/></svg>';

  var ICON_DELETE = '<svg viewBox="0 0 20 20" width="15" height="15" fill="none" stroke="currentColor" ' +
    'stroke-width="1.5" stroke-linecap="round" aria-hidden="true">' +
    '<path d="M5.5 5.5l9 9M14.5 5.5l-9 9"/></svg>';

  function esc(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  var toastTimer = null;
  function toast(message) {
    toastEl.textContent = message;
    toastEl.classList.add('toast--visible');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toastEl.classList.remove('toast--visible');
    }, 2800);
  }

  function plural(n, one, few, many) {
    var mod10 = n % 10, mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) return one;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
    return many;
  }

  function dayWord(n) { return plural(n, 'день', 'дня', 'дней'); }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  var MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
    'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];

  function clockText(ts) {
    var d = new Date(ts);
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function dayText(ts) {
    var d = new Date(ts);
    return d.getDate() + ' ' + MONTHS[d.getMonth()];
  }

  /* «в 06:00», «завтра в 06:00», «3 августа, 06:00» — когда откроется партия. */
  function whenText(ts, from) {
    var diff = Store.calendarDaysBetween(from, ts);
    if (diff <= 0) return 'в ' + clockText(ts);
    if (diff === 1) return 'завтра в ' + clockText(ts);
    return dayText(ts) + ', ' + clockText(ts);
  }

  /* «через 9 ч 28 мин» — сколько ждать до ближайшей партии слов. */
  function waitText(from, to) {
    var left = Math.max(0, to - from);
    var hours = Math.floor(left / 3600000);
    var minutes = Math.floor((left % 3600000) / 60000);
    if (hours >= 24) {
      var days = Math.floor(hours / 24);
      return 'через ' + days + ' ' + dayWord(days);
    }
    if (hours > 0) return 'через ' + hours + ' ч ' + minutes + ' мин';
    if (minutes > 0) return 'через ' + minutes + ' мин';
    return 'меньше минуты';
  }

  /* Медленное исчезновение: элемент растворяется, место за ним смыкается. */
  function evaporate(el, done) {
    if (!el) { done(); return; }
    el.classList.add('is-evaporating');
    setTimeout(done, 700);
  }

  function markFresh() {
    var nodes = screenEl.querySelectorAll('[data-id]');
    for (var i = 0; i < nodes.length; i++) {
      var id = nodes[i].getAttribute('data-id');
      if (!seen.has(id)) {
        seen.add(id);
        nodes[i].classList.add('is-new');
      }
    }
  }

  /* ---------- Навигация ---------- */

  function go(next, replace) {
    if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null; }
    route = next;
    filter = '';
    if (replace) history.replaceState(next, '');
    else history.pushState(next, '');
    render(true);
  }

  window.addEventListener('popstate', function (event) {
    if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null; }
    route = event.state || { name: 'blocks' };
    filter = '';
    closeModal();
    render(true);
  });

  /* ---------- Окна ---------- */

  function closeModal() {
    modalRoot.hidden = true;
    modalRoot.innerHTML = '';
  }

  function openForm(options) {
    var fieldsHtml = (options.fields || []).map(function (f) {
      var common = 'id="f-' + esc(f.name) + '" name="' + esc(f.name) + '" placeholder="' +
        esc(f.placeholder || '') + '"';
      var control = f.textarea
        ? '<textarea class="textarea" ' + common + '>' + esc(f.value || '') + '</textarea>'
        : '<input class="input" type="text" ' + common + ' value="' + esc(f.value || '') + '" autocomplete="off">';
      return '<label class="field"><span class="field__label">' + esc(f.label) + '</span>' + control + '</label>';
    }).join('');

    modalRoot.innerHTML =
      '<form class="glass modal" id="modal-form">' +
      '<h2 class="modal__title">' + esc(options.title) + '</h2>' +
      (options.hint ? '<p class="modal__hint">' + options.hint + '</p>' : '') +
      fieldsHtml +
      (options.extra || '') +
      '<div class="modal__actions">' +
      '<button type="button" class="btn" data-close>' + esc(options.cancelText || 'Отмена') + '</button>' +
      (options.submitText
        ? '<button type="submit" class="btn btn--accent">' + esc(options.submitText) + '</button>'
        : '') +
      '</div></form>';
    modalRoot.hidden = false;

    var form = document.getElementById('modal-form');
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      var values = {};
      (options.fields || []).forEach(function (f) {
        var el = document.getElementById('f-' + f.name);
        values[f.name] = el ? el.value : '';
      });
      if (!options.onSubmit(values)) closeModal();
    });

    modalRoot.querySelectorAll('[data-close]').forEach(function (el) {
      el.addEventListener('click', closeModal);
    });
    modalRoot.addEventListener('click', function (event) {
      if (event.target === modalRoot) closeModal();
    });

    var first = modalRoot.querySelector('.input, .textarea');
    if (first) setTimeout(function () { first.focus(); }, 60);
  }

  function confirmAction(title, text, confirmText, onConfirm) {
    openForm({
      title: title,
      hint: text,
      fields: [],
      submitText: confirmText,
      onSubmit: function () { onConfirm(); }
    });
  }

  /* ---------- Общие куски разметки ---------- */

  function dueChip(count) {
    if (!count) return '';
    return '<span class="chip chip--due"><span class="chip__dot"></span>' + count + ' к разбору</span>';
  }

  function summaryBlock(scope) {
    var s = Store.stats(scope);
    return '<div class="summary">' +
      '<div class="glass summary__cell"><div class="summary__value">' + s.total + '</div>' +
      '<div class="summary__label">' + plural(s.total, 'слово', 'слова', 'слов') + '</div></div>' +
      '<div class="glass summary__cell summary__cell--due"><div class="summary__value">' + s.due + '</div>' +
      '<div class="summary__label">к разбору</div></div>' +
      '<div class="glass summary__cell summary__cell--mastered"><div class="summary__value">' + s.mastered + '</div>' +
      '<div class="summary__label">освоено</div></div>' +
      '</div>';
  }

  function callBlock(scope) {
    var moment = Date.now();
    var count = Store.dueCount(scope, moment);
    if (count > 0) {
      return '<section class="glass call">' +
        '<div class="call__text">' +
        '<h2 class="call__title">' + count + ' ' + plural(count, 'слово ждёт', 'слова ждут', 'слов ждут') + ' разбора</h2>' +
        '<p class="call__hint">Вы пишете слово по-немецки</p>' +
        '</div>' +
        '<button class="btn btn--accent" data-start-drill>Начать</button>' +
        '</section>';
    }

    var stats = Store.stats(scope, moment);
    if (!stats.total) return '';

    /* Слов на сейчас нет — показываем, когда откроется следующая партия. */
    var next = Store.nextDueAt(scope, moment);
    var hint = next
      ? whenText(next, moment) +
        ' · <span data-countdown="' + next + '">' + waitText(moment, next) + '</span>'
      : 'Все слова этого раздела освоены';

    return '<section class="glass call">' +
      '<div class="call__text">' +
      '<h2 class="call__title">' + (next ? 'Разбор на сейчас окончен' : 'Раздел пройден') + '</h2>' +
      '<p class="call__hint">' + hint + '</p>' +
      '</div></section>';
  }

  /* ---------- Экран: блоки ---------- */

  function renderBlocks() {
    var blocks = Store.getState().blocks;
    var html = '';

    if (!blocks.length) {
      html += '<div class="empty">' +
        '<h3 class="empty__title">Здесь пока пусто</h3>' +
        '<p class="empty__text">Нажмите «+» и создайте первый блок —<br>например, «German Words B1».</p>' +
        '</div>';
      screenEl.innerHTML = html;
      return;
    }

    html += summaryBlock({});
    html += callBlock({});
    html += '<h2 class="section-title">Блоки<span class="section-title__note">' +
      blocks.length + '</span></h2>';

    blocks.forEach(function (block) {
      var words = Store.countWords(block);
      var due = Store.dueCount({ blockId: block.id });
      html += '<article class="glass card" data-id="' + block.id + '" data-open-block="' + block.id + '">' +
        '<div class="card__head"><h3 class="card__title">' + esc(block.title) + '</h3>' + dueChip(due) + '</div>' +
        '<div class="card__meta">' +
        '<span>' + block.sets.length + ' ' + plural(block.sets.length, 'список', 'списка', 'списков') + '</span>' +
        '<span>' + words + ' ' + plural(words, 'слово', 'слова', 'слов') + '</span>' +
        '</div>' +
        '<div class="card__actions">' +
        '<button class="btn btn--quiet" data-rename-block="' + block.id + '">Переименовать</button>' +
        '<button class="btn btn--quiet" data-delete-block="' + block.id + '">Удалить</button>' +
        '</div></article>';
    });

    screenEl.innerHTML = html;
  }

  /* ---------- Экран: списки блока ---------- */

  function renderBlock() {
    var block = Store.getBlock(route.blockId);
    if (!block) { go({ name: 'blocks' }, true); return; }

    var html = summaryBlock({ blockId: block.id });
    html += callBlock({ blockId: block.id });

    if (!block.sets.length) {
      html += '<div class="empty">' +
        '<h3 class="empty__title">В блоке нет списков</h3>' +
        '<p class="empty__text">Нажмите «+», чтобы создать список.<br>В один список помещается до ' + MAX + ' слов.</p>' +
        '</div>';
      screenEl.innerHTML = html;
      return;
    }

    html += '<h2 class="section-title">Списки<span class="section-title__note">' +
      block.sets.length + '</span></h2>';

    block.sets.forEach(function (set) {
      var count = set.words.length;
      var due = Store.dueCount({ blockId: block.id, setId: set.id });
      var full = count >= MAX;
      html += '<article class="glass card" data-id="' + set.id + '" data-open-set="' + set.id + '">' +
        '<div class="card__head"><h3 class="card__title">' + esc(set.title) + '</h3>' + dueChip(due) + '</div>' +
        '<div class="card__meta">' +
        '<span>' + count + ' / ' + MAX + '</span>' +
        '<span>' + (full ? 'список заполнен' : 'свободно ' + (MAX - count)) + '</span>' +
        '</div>' +
        '<div class="card__actions">' +
        '<button class="btn btn--quiet" data-rename-set="' + set.id + '">Переименовать</button>' +
        '<button class="btn btn--quiet" data-delete-set="' + set.id + '">Удалить</button>' +
        '</div></article>';
    });

    screenEl.innerHTML = html;
  }

  /* ---------- Экран: слова списка ---------- */

  function renderSet() {
    var block = Store.getBlock(route.blockId);
    var set = Store.getSet(route.blockId, route.setId);
    if (!block || !set) { go({ name: 'blocks' }, true); return; }

    var count = set.words.length;
    var full = count >= MAX;
    var percent = Math.min(100, (count / MAX) * 100);
    var scope = { blockId: block.id, setId: set.id };

    var html = '<section class="glass gauge">' +
      '<div class="gauge__top"><span>Заполнение списка</span>' +
      '<span class="gauge__value">' + count + ' / ' + MAX + '</span></div>' +
      '<div class="gauge__track"><div class="gauge__fill' + (full ? ' gauge__fill--full' : '') +
      '" style="width:' + percent.toFixed(1) + '%"></div></div></section>';

    html += callBlock(scope);

    if (full) {
      html += '<section class="glass call">' +
        '<div class="call__text">' +
        '<h2 class="call__title">Список заполнен</h2>' +
        '<p class="call__hint">В одном списке ровно ' + MAX + ' слов. Создайте следующий и продолжайте в нём.</p>' +
        '</div>' +
        '<button class="btn btn--accent" data-new-set>Создать</button>' +
        '</section>';
    } else {
      html += '<section class="glass entry">' +
        '<div class="entry__grid">' +
        '<label class="field"><span class="field__label">Слово по-немецки</span>' +
        '<input class="input" type="text" id="input-de" placeholder="die Ordnung" autocomplete="off" autocapitalize="off" spellcheck="false"></label>' +
        '<label class="field"><span class="field__label">Перевод</span>' +
        '<input class="input" type="text" id="input-ru" placeholder="порядок" autocomplete="off"></label>' +
        '</div>' +
        '<div class="btn-row">' +
        '<button class="btn btn--accent" data-add-word>Добавить</button>' +
        '<button class="btn" data-bulk>Вставить списком</button>' +
        '</div></section>';
    }

    html += '<h2 class="section-title">Выгрузка<span class="section-title__note">для отправки ИИ</span></h2>' +
      '<div class="btn-row">' +
      '<button class="btn" data-export="pdf">PDF</button>' +
      '<button class="btn" data-export="docx">Word</button>' +
      '<button class="btn" data-export="txt">Текст</button>' +
      '</div>';

    var collapsed = Store.isCollapsed(set.id);

    html += '<h2 class="section-title">Слова' +
      (count
        ? '<button class="btn btn--quiet" data-toggle-words>' +
          (collapsed ? 'Открыть · ' + count : 'Завернуть') + '</button>'
        : '<span class="section-title__note">0</span>') +
      '</h2>';

    if (!count) {
      html += '<div class="empty">' +
        '<h3 class="empty__title">Слов пока нет</h3>' +
        '<p class="empty__text">Добавьте слово с переводом в форму выше.<br>' +
        '«Вставить списком» принимает сразу много строк.</p></div>';
    } else if (collapsed) {
      html += '<section class="glass folded" data-open-words>' +
        '<span class="folded__count">' + count + ' ' + plural(count, 'слово', 'слова', 'слов') + '</span>' +
        '<span class="folded__hint">свёрнуто — нажмите, чтобы открыть</span>' +
        '</section>';
    } else {
      html += '<div class="search"><input class="input" type="search" id="input-search" ' +
        'placeholder="Поиск по словам" value="' + esc(filter) + '"></div>';

      var needle = filter.trim().toLowerCase();
      var rows = '';
      var shown = 0;

      set.words.forEach(function (word, index) {
        if (needle &&
          word.de.toLowerCase().indexOf(needle) < 0 &&
          word.ru.toLowerCase().indexOf(needle) < 0) return;
        shown++;

        var label = Store.dueLabel(word);
        var chipClass = word.mastered ? 'chip chip--calm' : (label === 'сейчас' ? 'chip chip--due' : 'chip');
        var note = word.ru ? esc(word.ru) : '<span style="opacity:.65">нет перевода — в разбор не попадёт</span>';

        rows += '<div class="ledger__row" data-id="' + word.id + '" data-row="' + word.id + '">' +
          '<div class="ledger__no">' + (index + 1) + '</div>' +
          '<div class="ledger__de">' + esc(word.de) + '</div>' +
          '<div class="ledger__meta">' +
          '<span class="ledger__ru">' + note + '</span>' +
          '<span class="' + chipClass + '">' + label + '</span>' +
          '</div>' +
          '<div class="ledger__side">' +
          '<button class="icon-btn" data-edit-word="' + word.id + '" title="Изменить" aria-label="Изменить">' + ICON_EDIT + '</button>' +
          '<button class="icon-btn" data-delete-word="' + word.id + '" title="Удалить" aria-label="Удалить">' + ICON_DELETE + '</button>' +
          '</div></div>';
      });

      if (!shown) {
        rows = '<div class="ledger__row"><div class="ledger__no">—</div>' +
          '<div class="ledger__de" style="font-weight:400">Ничего не найдено</div></div>';
      }

      html += '<div class="glass ledger">' + rows + '</div>';
    }

    screenEl.innerHTML = html;
  }

  /* ---------- Экран: разбор ---------- */

  function ladderHtml(level) {
    var steps = Store.INTERVALS.map(function (days, i) {
      var cls = 'ladder__step';
      if (i < level - 1) cls += ' ladder__step--done';
      else if (i === level - 1) cls += ' ladder__step--current';
      return '<span class="' + cls + '">' + days + '</span>';
    }).join('');
    return '<div class="ladder">' + steps + '</div>';
  }

  function renderDrill() {
    if (!session) { go({ name: 'blocks' }, true); return; }

    var total = session.total;
    var done = Math.min(session.index, total);
    var percent = total ? (done / total) * 100 : 100;

    if (session.index >= session.queue.length) {
      screenEl.innerHTML = '<section class="glass drill">' +
        '<h2 class="drill__prompt">Разбор окончен</h2>' +
        '<p class="drill__hint">Верно с первого раза: ' + session.correct + ' из ' + session.asked + '</p>' +
        (session.forgot ? '<p class="drill__hint">Вернулось в начало: ' + session.forgot + '</p>' : '') +
        '<div class="drill__actions"><button class="btn btn--accent btn--wide" data-drill-exit>Готово</button></div>' +
        '</section>';
      return;
    }

    var item = session.queue[session.index];
    var word = item.word;

    var html = '<section class="glass drill" data-id="drill-' + word.id + '-' + session.index + '">' +
      '<div class="drill__progress"><div class="drill__progress-fill" style="width:' + percent.toFixed(1) + '%"></div></div>' +
      '<div class="drill__eyebrow">Слово ' + (done + 1) + ' из ' + total + '</div>' +
      '<h2 class="drill__prompt">' + esc(word.ru) + '</h2>';

    if (session.state === 'ask') {
      html += '<p class="drill__hint">Напишите это слово по-немецки</p>' +
        '<input class="input drill__input" type="text" id="drill-input" autocomplete="off" ' +
        'autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="…">' +
        '<div class="drill__actions"><button class="btn btn--accent btn--wide" data-check>Проверить</button></div>';
    } else {
      html += '<p class="drill__hint">' + esc(item.setTitle) + '</p><div class="drill__verdict">';

      if (session.state === 'right') {
        html += '<div class="verdict verdict--right">' +
          '<div class="verdict__label">Верно</div>' +
          '<p class="verdict__word">' + esc(word.de) + '</p>' +
          '<p class="verdict__next">' + esc(session.nextNote) + '</p>' +
          ladderHtml(word.level) +
          '</div>' +
          '<div class="drill__actions"><button class="btn btn--wide" data-next>Дальше</button></div>';
      } else {
        html += '<div class="verdict verdict--wrong">' +
          '<div class="verdict__label">Не совпало</div>' +
          '<p class="verdict__word">' + esc(word.de) + '</p>' +
          '<p class="verdict__yours">Вы написали: <s>' + esc(session.answer) + '</s></p>' +
          '<div class="verdict__choice">' +
          '<button class="btn btn--calm" data-outcome="typo">Опечатка</button>' +
          '<button class="btn btn--accent" data-outcome="forgot">Забыл</button>' +
          '</div></div>';
      }
      html += '</div>';
    }

    html += '</section>';
    screenEl.innerHTML = html;

    if (session.state === 'ask') {
      var input = document.getElementById('drill-input');
      if (input) setTimeout(function () { input.focus(); }, 80);
    }
  }

  function startDrill(scope, label) {
    var queue = Store.buildQueue(scope);
    if (!queue.length) { toast('На сегодня слов для разбора нет'); return; }
    session = {
      scope: scope, label: label, queue: queue, total: queue.length,
      index: 0, correct: 0, asked: 0, forgot: 0, state: 'ask', answer: '', nextNote: ''
    };
    go({ name: 'drill', blockId: scope.blockId, setId: scope.setId });
  }

  function submitAnswer() {
    if (!session || session.state !== 'ask') return;
    var input = document.getElementById('drill-input');
    if (!input) return;

    var answer = input.value.trim();
    if (!answer) { input.focus(); return; }

    var item = session.queue[session.index];
    session.answer = answer;
    session.asked++;

    if (Store.checkAnswer(item.word, answer)) {
      var result = Store.reviewWord(item.word.id, 'correct');
      session.correct++;
      session.state = 'right';
      session.nextNote = result && result.mastered
        ? 'Слово освоено — лестница пройдена полностью'
        : 'Через ' + result.days + ' ' + dayWord(result.days) + ' · ' +
          dayText(result.due) + ', с ' + clockText(result.due);
      render();
      /* Небольшая пауза, чтобы увидеть правильное написание. */
      advanceTimer = setTimeout(nextWord, 1600);
    } else {
      session.state = 'wrong';
      render();
    }
  }

  function resolveWrong(outcome) {
    if (!session) return;
    var item = session.queue[session.index];
    var result = Store.reviewWord(item.word.id, outcome);

    if (outcome === 'forgot') {
      session.forgot++;
      /* Слово начинает лестницу заново и вернётся в конце этого разбора. */
      session.queue.push(item);
      toast('Слово вернулось в начало лестницы');
    } else if (result) {
      toast(result.mastered
        ? 'Засчитано · слово освоено'
        : 'Засчитано · дальше ' + dayText(result.due) + ', с ' + clockText(result.due));
    }
    nextWord();
  }

  function nextWord() {
    if (advanceTimer) { clearTimeout(advanceTimer); advanceTimer = null; }
    if (!session) return;
    var card = screenEl.querySelector('.drill');
    var move = function () {
      session.index++;
      session.state = 'ask';
      session.answer = '';
      render();
    };
    /* Карточка растворяется, и только потом появляется следующее слово. */
    if (card && session.index + 1 <= session.queue.length) evaporate(card, move);
    else move();
  }

  /* ---------- Отрисовка ---------- */

  function renderCrumbs() {
    var parts = [];
    if (route.name === 'blocks') {
      parts.push('<span class="crumbs__current">Все блоки</span>');
    } else {
      parts.push('<button class="crumbs__link" data-go-blocks>Все блоки</button>');
      var block = Store.getBlock(route.blockId);
      if (block) {
        var isLeaf = route.name === 'block';
        parts.push('<span class="crumbs__sep">/</span>' + (isLeaf
          ? '<span class="crumbs__current">' + esc(block.title) + '</span>'
          : '<button class="crumbs__link" data-go-block>' + esc(block.title) + '</button>'));

        var set = route.setId ? Store.getSet(route.blockId, route.setId) : null;
        if (set) {
          parts.push('<span class="crumbs__sep">/</span>' + (route.name === 'set'
            ? '<span class="crumbs__current">' + esc(set.title) + '</span>'
            : '<button class="crumbs__link" data-go-set>' + esc(set.title) + '</button>'));
        }
      }
      if (route.name === 'drill') {
        parts.push('<span class="crumbs__sep">/</span><span class="crumbs__current">Разбор</span>');
      }
    }
    crumbsEl.innerHTML = parts.join('');
  }

  var lastDueCount = null;

  /* Шапка всегда показывает текущее время и то, чего ждать дальше. */
  function renderChrome(moment) {
    moment = moment || Date.now();
    var blocks = Store.getState().blocks;
    var total = blocks.reduce(function (sum, b) { return sum + Store.countWords(b); }, 0);
    var due = Store.dueCount({}, moment);
    lastDueCount = due;

    var note;
    if (!total) note = 'Немецкие слова';
    else if (due) note = due + ' ' + plural(due, 'слово', 'слова', 'слов') + ' к разбору';
    else {
      var next = Store.nextDueAt({}, moment);
      note = next ? 'следующие ' + whenText(next, moment) : 'все слова освоены';
    }

    subEl.textContent = clockText(moment) + ' · ' + note;
    footerEl.textContent = total ? 'Блоков: ' + blocks.length + ' · слов: ' + total : '';
  }

  function refreshCountdowns(moment) {
    var nodes = screenEl.querySelectorAll('[data-countdown]');
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].textContent = waitText(moment, Number(nodes[i].getAttribute('data-countdown')));
    }
  }

  /* Приложение само следит за временем: когда в 6 утра открывается новая
     партия слов, экран обновляется без перезапуска. */
  function tick() {
    var moment = Date.now();
    var due = Store.dueCount({}, moment);

    if (due !== lastDueCount && route.name !== 'drill') {
      var appeared = due > (lastDueCount || 0);
      render();
      if (appeared) toast('Слов к разбору: ' + due);
      return;
    }

    renderChrome(moment);
    refreshCountdowns(moment);
  }

  function render(routeChanged) {
    if (route.name === 'blocks') renderBlocks();
    else if (route.name === 'block') renderBlock();
    else if (route.name === 'set') renderSet();
    else renderDrill();

    renderCrumbs();
    renderChrome();
    markFresh();

    if (routeChanged) {
      screenEl.classList.remove('screen--enter');
      void screenEl.offsetWidth;
      screenEl.classList.add('screen--enter');
    }

    fab.hidden = route.name === 'drill' ||
      (route.name === 'set' && (function () {
        var set = Store.getSet(route.blockId, route.setId);
        return !!(set && set.words.length >= MAX);
      })());
  }

  /* ---------- Действия ---------- */

  function createBlock() {
    openForm({
      title: 'Новый блок',
      hint: 'Блок собирает списки слов по теме или уровню.',
      fields: [{ name: 'title', label: 'Название блока', placeholder: 'German Words B1' }],
      submitText: 'Создать',
      onSubmit: function (values) {
        var block = Store.addBlock(values.title);
        toast('Блок создан');
        go({ name: 'block', blockId: block.id });
      }
    });
  }

  function createSet(blockId, andOpen) {
    var block = Store.getBlock(blockId);
    if (!block) return;
    var suggested = 'Список ' + (block.sets.length + 1);
    openForm({
      title: 'Новый список',
      hint: 'В список помещается до ' + MAX + ' слов. Когда он заполнится, создайте следующий.',
      fields: [{ name: 'title', label: 'Название списка', placeholder: suggested, value: suggested }],
      submitText: 'Создать',
      onSubmit: function (values) {
        var set = Store.addSet(blockId, values.title);
        toast('Список создан');
        if (andOpen) go({ name: 'set', blockId: blockId, setId: set.id });
        else render();
      }
    });
  }

  function addWordFromForm() {
    var deEl = document.getElementById('input-de');
    var ruEl = document.getElementById('input-ru');
    if (!deEl) return;

    var result = Store.addWord(route.blockId, route.setId, deEl.value, ruEl.value);
    if (!result.ok) { toast(result.reason); return; }

    var remaining = result.remaining;
    render();

    var freshDe = document.getElementById('input-de');
    if (freshDe) freshDe.focus();
    if (remaining === 0) toast('Список заполнен: ' + MAX + ' слов');
    else if (remaining <= 10) toast('Свободных мест: ' + remaining);
  }

  function openBulk() {
    var set = Store.getSet(route.blockId, route.setId);
    if (!set) return;
    var free = MAX - set.words.length;
    openForm({
      title: 'Вставить списком',
      hint: 'По одной паре в строке. Разделитель — тире, точка с запятой или табуляция:<br>' +
        '<b>der Vertrag — договор</b><br>Свободных мест: <b>' + free + '</b>.',
      fields: [{
        name: 'text', label: 'Слова', textarea: true,
        placeholder: 'die Ordnung — порядок\nder Plan — план\ndie Arbeit; работа'
      }],
      submitText: 'Добавить',
      onSubmit: function (values) {
        var result = Store.addWordsBulk(route.blockId, route.setId, values.text);
        var message = 'Добавлено: ' + result.added;
        if (result.overflow) message += ' · не поместилось: ' + result.overflow;
        toast(message);
        render();
      }
    });
  }

  function editWord(wordId) {
    var set = Store.getSet(route.blockId, route.setId);
    if (!set) return;
    var word = set.words.find(function (w) { return w.id === wordId; });
    if (!word) return;
    openForm({
      title: 'Изменить слово',
      fields: [
        { name: 'de', label: 'Слово по-немецки', value: word.de },
        { name: 'ru', label: 'Перевод', value: word.ru }
      ],
      submitText: 'Сохранить',
      onSubmit: function (values) {
        Store.updateWord(route.blockId, route.setId, wordId, values.de, values.ru);
        render();
      }
    });
  }

  function exportSet(format) {
    var block = Store.getBlock(route.blockId);
    var set = Store.getSet(route.blockId, route.setId);
    if (!block || !set) return;
    if (!set.words.length) { toast('В списке нет слов'); return; }

    try {
      var result = window.Exporter.exportSet(format, {
        blockTitle: block.title,
        setTitle: set.title,
        title: block.title + ' — ' + set.title,
        words: set.words,
        meta: {
          line: 'Слов: ' + set.words.length + '  ·  ' + window.Exporter.formatDate(Date.now()),
          footer: 'Wortschatz'
        }
      });
      if (result && result.native) toast(result.message || 'Файл сохранён');
      else toast('Файл выгружен: ' + format.toUpperCase());
    } catch (error) {
      console.error(error);
      toast('Не удалось выгрузить: ' + error.message);
    }
  }

  function saveArchive(name, bytes) {
    if (window.AndroidBridge && typeof window.AndroidBridge.saveFile === 'function') {
      toast(window.AndroidBridge.saveFile(name, window.Exporter.bytesToBase64(bytes), 'application/json') || 'Копия сохранена');
      return;
    }
    var blob = new Blob([bytes], { type: 'application/json' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
    toast('Копия сохранена');
  }

  function openArchive() {
    var saved = Store.savedAt();
    var storageNote = Store.isMemoryOnly()
      ? '<b>Внимание:</b> устройство запретило сохранение, данные живут только до закрытия.'
      : 'Прогресс хранится на устройстве в двух местах сразу и записывается после ' +
        'каждого изменения' + (saved ? ' (последняя запись в ' + clockText(saved) + ')' : '') + '.';

    openForm({
      title: 'Резервная копия',
      hint: storageNote + '<br><br>Копия — это один файл со всеми блоками, списками, ' +
        'словами и расписанием разбора. Сохраните его, чтобы не потерять картотеку ' +
        'при смене устройства.',
      fields: [],
      cancelText: 'Закрыть',
      extra: '<div class="btn-row" style="margin-top:4px">' +
        '<button type="button" class="btn btn--accent" data-archive-save>Сохранить копию</button>' +
        '<button type="button" class="btn" data-archive-load>Загрузить копию</button>' +
        '</div>',
      onSubmit: function () { }
    });

    modalRoot.querySelector('[data-archive-save]').addEventListener('click', function () {
      var name = 'wortschatz-' + new Date().toISOString().slice(0, 10) + '.json';
      saveArchive(name, new TextEncoder().encode(Store.exportJson()));
    });

    modalRoot.querySelector('[data-archive-load]').addEventListener('click', function () {
      var input = document.createElement('input');
      input.type = 'file';
      input.accept = '.json,application/json';
      input.addEventListener('change', function () {
        var file = input.files && input.files[0];
        if (!file) return;
        var reader = new FileReader();
        reader.onload = function () {
          try {
            var added = Store.importJson(String(reader.result), 'merge');
            closeModal();
            go({ name: 'blocks' }, true);
            toast('Загружено блоков: ' + added);
          } catch (error) {
            toast('Файл не подошёл: ' + error.message);
          }
        };
        reader.readAsText(file);
      });
      input.click();
    });
  }

  /* ---------- События ---------- */

  fab.addEventListener('click', function () {
    if (route.name === 'blocks') createBlock();
    else if (route.name === 'block') createSet(route.blockId, true);
    else if (route.name === 'set') {
      var input = document.getElementById('input-de');
      if (input) { input.focus(); input.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
    }
  });

  document.getElementById('btn-archive').addEventListener('click', openArchive);

  crumbsEl.addEventListener('click', function (event) {
    if (event.target.closest('[data-go-blocks]')) go({ name: 'blocks' });
    else if (event.target.closest('[data-go-block]')) go({ name: 'block', blockId: route.blockId });
    else if (event.target.closest('[data-go-set]')) go({ name: 'set', blockId: route.blockId, setId: route.setId });
  });

  screenEl.addEventListener('click', function (event) {
    var target = event.target;
    var el;

    if ((el = target.closest('[data-rename-block]'))) {
      var blockId = el.getAttribute('data-rename-block');
      var block = Store.getBlock(blockId);
      openForm({
        title: 'Переименовать блок',
        fields: [{ name: 'title', label: 'Название блока', value: block ? block.title : '' }],
        submitText: 'Сохранить',
        onSubmit: function (values) { Store.renameBlock(blockId, values.title); render(); }
      });
      return;
    }

    if ((el = target.closest('[data-delete-block]'))) {
      var deleteBlockId = el.getAttribute('data-delete-block');
      var doomedBlock = Store.getBlock(deleteBlockId);
      confirmAction('Удалить блок?',
        'Блок «' + esc(doomedBlock ? doomedBlock.title : '') + '» исчезнет вместе со всеми списками и словами.',
        'Удалить',
        function () {
          evaporate(screenEl.querySelector('[data-id="' + deleteBlockId + '"]'), function () {
            Store.removeBlock(deleteBlockId);
            render();
            toast('Блок удалён');
          });
        });
      return;
    }

    if ((el = target.closest('[data-rename-set]'))) {
      var setId = el.getAttribute('data-rename-set');
      var set = Store.getSet(route.blockId, setId);
      openForm({
        title: 'Переименовать список',
        fields: [{ name: 'title', label: 'Название списка', value: set ? set.title : '' }],
        submitText: 'Сохранить',
        onSubmit: function (values) { Store.renameSet(route.blockId, setId, values.title); render(); }
      });
      return;
    }

    if ((el = target.closest('[data-delete-set]'))) {
      var deleteSetId = el.getAttribute('data-delete-set');
      var doomedSet = Store.getSet(route.blockId, deleteSetId);
      confirmAction('Удалить список?',
        'Список «' + esc(doomedSet ? doomedSet.title : '') + '» исчезнет вместе со словами.',
        'Удалить',
        function () {
          evaporate(screenEl.querySelector('[data-id="' + deleteSetId + '"]'), function () {
            Store.removeSet(route.blockId, deleteSetId);
            render();
            toast('Список удалён');
          });
        });
      return;
    }

    if ((el = target.closest('[data-open-block]'))) {
      go({ name: 'block', blockId: el.getAttribute('data-open-block') });
      return;
    }

    if ((el = target.closest('[data-open-set]'))) {
      go({ name: 'set', blockId: route.blockId, setId: el.getAttribute('data-open-set') });
      return;
    }

    if (target.closest('[data-start-drill]')) {
      var scope = {};
      var label = 'Все блоки';
      if (route.name === 'block') { scope = { blockId: route.blockId }; label = Store.getBlock(route.blockId).title; }
      if (route.name === 'set') {
        scope = { blockId: route.blockId, setId: route.setId };
        label = Store.getSet(route.blockId, route.setId).title;
      }
      startDrill(scope, label);
      return;
    }

    if (target.closest('[data-check]')) { submitAnswer(); return; }
    if (target.closest('[data-next]')) { nextWord(); return; }

    if ((el = target.closest('[data-outcome]'))) { resolveWrong(el.getAttribute('data-outcome')); return; }

    if (target.closest('[data-drill-exit]')) {
      session = null;
      history.back();
      return;
    }

    if (target.closest('[data-toggle-words]')) {
      var ledgerEl = screenEl.querySelector('.ledger');
      if (ledgerEl && !Store.isCollapsed(route.setId)) {
        /* Список не пропадает рывком — он растворяется, как и всё здесь. */
        evaporate(ledgerEl, function () { Store.toggleCollapsed(route.setId); render(); });
      } else {
        Store.toggleCollapsed(route.setId);
        render();
      }
      return;
    }

    if (target.closest('[data-open-words]')) {
      Store.toggleCollapsed(route.setId);
      render();
      return;
    }

    if (target.closest('[data-add-word]')) { addWordFromForm(); return; }
    if (target.closest('[data-bulk]')) { openBulk(); return; }
    if (target.closest('[data-new-set]')) { createSet(route.blockId, true); return; }
    if ((el = target.closest('[data-export]'))) { exportSet(el.getAttribute('data-export')); return; }
    if ((el = target.closest('[data-edit-word]'))) { editWord(el.getAttribute('data-edit-word')); return; }

    if ((el = target.closest('[data-delete-word]'))) {
      var wordId = el.getAttribute('data-delete-word');
      evaporate(screenEl.querySelector('[data-row="' + wordId + '"]'), function () {
        seen.delete(wordId);
        Store.removeWord(route.blockId, route.setId, wordId);
        render();
      });
      return;
    }
  });

  screenEl.addEventListener('keydown', function (event) {
    if (event.key !== 'Enter') return;
    var id = event.target.id;
    if (id === 'input-de') {
      event.preventDefault();
      var ru = document.getElementById('input-ru');
      if (ru) ru.focus();
    } else if (id === 'input-ru') {
      event.preventDefault();
      addWordFromForm();
    } else if (id === 'drill-input') {
      event.preventDefault();
      submitAnswer();
    }
  });

  screenEl.addEventListener('input', function (event) {
    if (event.target.id !== 'input-search') return;
    filter = event.target.value;
    var caret = event.target.selectionStart;
    render();
    var search = document.getElementById('input-search');
    if (search) {
      search.focus();
      try { search.setSelectionRange(caret, caret); } catch (e) { /* не все поля это умеют */ }
    }
  });

  /* ---------- Запуск ---------- */

  Store.load();
  history.replaceState(route, '');
  render(true);

  /* Часы идут всё время работы приложения. */
  setInterval(tick, 15000);

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') tick();
    else Store.flush();
  });

  /* Перед уходом со страницы дописываем всё несохранённое. */
  window.addEventListener('pagehide', function () { Store.flush(); });
  window.addEventListener('beforeunload', function () { Store.flush(); });

  /* Данные могли обновиться из запасной копии в базе — перерисуем. */
  Store.subscribe(function () {
    if (route.name !== 'drill' && modalRoot.hidden) render();
  });

  if (Store.isMemoryOnly()) {
    toast('Браузер запретил сохранение — слова не переживут закрытие вкладки');
  } else if (Store.clockSuspicious()) {
    toast('Часы устройства показывают более раннее время, чем при прошлом запуске');
  }
})();
