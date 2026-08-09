/* Интерфейс: блоки → списки → слова, плюс разбор по интервалам.
   Переходы пишутся в history, поэтому системная кнопка «назад» работает. */
(function () {
  'use strict';

  var Store = window.Store;
  var MAX = Store.MAX_WORDS;

  var screenEl = document.getElementById('screen');
  var crumbsEl = document.getElementById('crumbs');
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

  /* Возврат — одна крупная стрелка: слово «назад» рядом с ней лишнее. */
  var ICON_BACK = '<svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" ' +
    'stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M15.5 4.5L8 12l7.5 7.5"/></svg>';

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
    pendingImage = null;
    if (replace) history.replaceState(next, '');
    else history.pushState(next, '');
    render(true);
  }

  window.addEventListener('popstate', function (event) {
    /* Открытая во весь экран картинка — первое, что закрывает «назад». */
    if (viewer) { closeViewer(viewer.slideOut); return; }

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
      (options.hint ? '<div class="modal__hint">' + options.hint + '</div>' : '') +
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

  /* Новые слова разбираются внутри своего раздела, поэтому снаружи о них
     говорит значок: сколько ждёт и где именно. */
  function freshChip(count) {
    if (!count) return '';
    return '<span class="chip chip--calm">' + count + ' ' +
      plural(count, 'новое', 'новых', 'новых') + '</span>';
  }

  /* ---------- Картинки ---------- */

  /* Картинки лежат в базе, поэтому в разметку идёт только их номер,
     а сами они подставляются после отрисовки. */
  var imageCache = new Map();
  var pendingImage = null;

  function imageBox(imageId, extraClass) {
    if (!imageId) return '';
    var cached = imageCache.get(imageId);
    return '<span class="picture ' + (extraClass || '') + '" data-image-id="' + imageId + '"' +
      (cached ? ' style="background-image:url(' + cached + ')"' : '') + '></span>';
  }

  function hydrateImages(root) {
    var nodes = (root || document).querySelectorAll('[data-image-id]');
    for (var i = 0; i < nodes.length; i++) {
      (function (node) {
        var id = node.getAttribute('data-image-id');
        if (imageCache.has(id)) {
          node.style.backgroundImage = 'url(' + imageCache.get(id) + ')';
          return;
        }
        Store.imageLoad(id).then(function (url) {
          if (!url) return;
          imageCache.set(id, url);
          node.style.backgroundImage = 'url(' + url + ')';
        });
      })(nodes[i]);
    }
  }

  function applySize(slider) {
    var kind = slider.getAttribute('data-size');
    var scale = Number(slider.value) / 100;
    if (!isFinite(scale)) return;

    var card = screenEl.querySelector('.drill');
    if (card) card.style.setProperty('--drill-' + kind, scale.toFixed(2));

    var value = screenEl.querySelector('[data-size-value="' + kind + '"]');
    if (value) value.textContent = Math.round(scale * 100) + '%';

    Store.setScale(kind, scale);
  }

  /* ---------- Картинка во весь экран ---------- */

  /* Откуда взять саму картинку: у сохранённых есть номер в базе,
     у ещё не привязанной — только то, что лежит в форме. */
  function pictureSource(el) {
    var id = el.getAttribute('data-image-id');
    if (id) {
      if (imageCache.has(id)) return Promise.resolve(imageCache.get(id));
      return Store.imageLoad(id).then(function (url) {
        if (url) imageCache.set(id, url);
        return url;
      });
    }
    if (el.hasAttribute('data-image-pending')) return Promise.resolve(pendingImage);
    return Promise.resolve(null);
  }

  function openPicture(el) {
    pictureSource(el).then(function (url) {
      if (url) openViewer(url);
    });
  }

  var viewer = null;

  /* Картинка на весь экран без единой кнопки: двумя пальцами приближается,
     двойным касанием тоже, вниз — закрывается, долгим нажатием сохраняется. */
  function openViewer(url) {
    if (viewer) return;

    var node = document.createElement('div');
    node.className = 'viewer';
    node.id = 'viewer';

    var image = document.createElement('img');
    image.className = 'viewer__img';
    image.id = 'viewer-img';
    image.alt = '';
    image.src = url;

    var hint = document.createElement('p');
    hint.className = 'viewer__hint';
    hint.textContent = 'потяните вниз — закроется · подержите — сохранится';

    node.appendChild(image);
    node.appendChild(hint);
    document.body.appendChild(node);

    viewer = {
      node: node, image: image, url: url,
      scale: 1, x: 0, y: 0, slideOut: false,
      points: new Map(), start: null, hold: null, lastTap: 0, closing: false
    };

    /* Своя запись в истории: системная кнопка «назад» сначала закрывает
       картинку и только потом уводит с экрана. */
    history.pushState({ name: 'viewer' }, '');

    requestAnimationFrame(function () { node.classList.add('viewer--in'); });
    bindViewer();
  }

  /* Закрытие идёт через историю — чтобы лишняя запись не оставалась. */
  function dismissViewer(slideDown) {
    if (!viewer || viewer.closing) return;
    viewer.slideOut = !!slideDown;
    history.back();
  }

  function closeViewer(slideDown) {
    if (!viewer || viewer.closing) return;
    viewer.closing = true;
    var node = viewer.node;
    var image = viewer.image;
    node.classList.remove('viewer--in');
    if (slideDown) image.style.transform = 'translate3d(0,' + (window.innerHeight * 0.5) + 'px,0) scale(.86)';
    viewer = null;
    setTimeout(function () {
      if (node.parentNode) node.parentNode.removeChild(node);
    }, 420);
  }

  function paintViewer() {
    if (!viewer) return;
    viewer.image.style.transform = 'translate3d(' + viewer.x.toFixed(1) + 'px,' +
      viewer.y.toFixed(1) + 'px,0) scale(' + viewer.scale.toFixed(3) + ')';
  }

  function viewerCenter() {
    var points = [];
    viewer.points.forEach(function (p) { points.push(p); });
    if (!points.length) return { x: 0, y: 0 };
    var sumX = 0, sumY = 0;
    points.forEach(function (p) { sumX += p.x; sumY += p.y; });
    return { x: sumX / points.length, y: sumY / points.length };
  }

  function viewerSpread() {
    var points = [];
    viewer.points.forEach(function (p) { points.push(p); });
    if (points.length < 2) return 0;
    return Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y);
  }

  function cancelHold() {
    if (viewer && viewer.hold) { clearTimeout(viewer.hold); viewer.hold = null; }
  }

  function bindViewer() {
    var node = viewer.node;

    node.addEventListener('pointerdown', function (event) {
      if (!viewer) return;
      event.preventDefault();
      node.setPointerCapture(event.pointerId);
      viewer.points.set(event.pointerId, { x: event.clientX, y: event.clientY });
      viewer.start = {
        spread: viewerSpread(), center: viewerCenter(),
        scale: viewer.scale, x: viewer.x, y: viewer.y, moved: 0
      };

      /* Долгое нажатие сохраняет картинку — кнопка для этого не нужна. */
      cancelHold();
      if (viewer.points.size === 1) {
        var saveUrl = viewer.url;
        viewer.hold = setTimeout(function () {
          if (!viewer) return;
          viewer.hold = null;
          viewer.start = null;
          viewer.points.clear();
          saveImageFile(saveUrl);
        }, 620);
      }
    });

    node.addEventListener('pointermove', function (event) {
      if (!viewer || !viewer.points.has(event.pointerId) || !viewer.start) return;
      viewer.points.set(event.pointerId, { x: event.clientX, y: event.clientY });

      var center = viewerCenter();
      var shiftX = center.x - viewer.start.center.x;
      var shiftY = center.y - viewer.start.center.y;
      viewer.start.moved = Math.max(viewer.start.moved, Math.hypot(shiftX, shiftY));
      if (viewer.start.moved > 9) cancelHold();

      if (viewer.points.size >= 2 && viewer.start.spread > 0) {
        viewer.scale = Math.min(6, Math.max(1, viewer.start.scale * (viewerSpread() / viewer.start.spread)));
        viewer.x = viewer.start.x + shiftX;
        viewer.y = viewer.start.y + shiftY;
        paintViewer();
        return;
      }

      if (viewer.scale > 1.02) {
        viewer.x = viewer.start.x + shiftX;
        viewer.y = viewer.start.y + shiftY;
        paintViewer();
        return;
      }

      /* Не увеличенную картинку тянут вниз — она уходит вместе с экраном. */
      viewer.y = Math.max(0, shiftY);
      viewer.x = shiftX * 0.25;
      viewer.node.style.setProperty('--viewer-veil', String(Math.max(0, 1 - viewer.y / 340)));
      paintViewer();
    });

    function release(event) {
      if (!viewer) return;
      cancelHold();
      viewer.points.delete(event.pointerId);
      if (viewer.points.size > 0) { viewer.start = null; return; }

      var dragged = viewer.y;
      var moved = viewer.start ? viewer.start.moved : 0;
      viewer.start = null;

      if (viewer.scale <= 1.02 && dragged > 108) { dismissViewer(true); return; }

      if (viewer.scale <= 1.02) {
        viewer.x = 0;
        viewer.y = 0;
        viewer.node.style.setProperty('--viewer-veil', '1');
        viewer.image.classList.add('viewer__img--eased');
        paintViewer();
        setTimeout(function () {
          if (viewer) viewer.image.classList.remove('viewer__img--eased');
        }, 380);
      }

      /* Двойное касание приближает и возвращает обратно. */
      if (moved < 9) {
        var now = Date.now();
        if (now - viewer.lastTap < 320) {
          viewer.lastTap = 0;
          viewer.image.classList.add('viewer__img--eased');
          if (viewer.scale > 1.02) { viewer.scale = 1; viewer.x = 0; viewer.y = 0; }
          else viewer.scale = 2.6;
          paintViewer();
          setTimeout(function () {
            if (viewer) viewer.image.classList.remove('viewer__img--eased');
          }, 380);
        } else {
          viewer.lastTap = now;
        }
      }
    }

    node.addEventListener('pointerup', release);
    node.addEventListener('pointercancel', release);

    /* На большом экране колесо тоже приближает. */
    node.addEventListener('wheel', function (event) {
      if (!viewer) return;
      event.preventDefault();
      viewer.scale = Math.min(6, Math.max(1, viewer.scale * (event.deltaY < 0 ? 1.12 : 0.89)));
      if (viewer.scale <= 1.02) { viewer.x = 0; viewer.y = 0; }
      paintViewer();
    }, { passive: false });
  }

  document.addEventListener('keydown', function (event) {
    if (viewer && event.key === 'Escape') dismissViewer(false);
  });

  /* Сохранение картинки на устройство: в приложении её кладёт оболочка,
     в браузере — обычная ссылка на скачивание. */
  function saveImageFile(dataUrl) {
    if (!dataUrl) return;
    var comma = dataUrl.indexOf(',');
    var head = dataUrl.slice(0, comma);
    var mime = (head.split(':')[1] || 'image/jpeg').split(';')[0];
    var name = 'lexis-' + new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-') +
      '.' + (mime.indexOf('png') >= 0 ? 'png' : 'jpg');

    if (window.AndroidBridge && typeof window.AndroidBridge.saveFile === 'function') {
      toast(window.AndroidBridge.saveFile(name, dataUrl.slice(comma + 1), mime) || 'Картинка сохранена');
      return;
    }

    var link = document.createElement('a');
    link.href = dataUrl;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast('Картинка сохранена');
  }

  /* Значки вместо подписей: слова занимали всю строку, а рисунок понятен
     и с одного взгляда. Что означает каждый — написано в настройках. */
  var PHOTO_ACTIONS = [
    { name: 'photo-pick', icon: '📷', title: 'Снимок с устройства',
      note: 'взять готовый снимок из галереи или камеры' },
    { name: 'photo-ai', icon: '🎨', title: 'Нарисовать ИИ',
      note: 'обычный промпт: хорошо справляется с предметами' },
    { name: 'photo-smart', icon: '🧠', title: 'Умный промпт',
      note: 'ИИ сам разберёт слово и напишет промпт — для действий, ' +
        'качеств, чувств и идиом, где обычный промах' },
    { name: 'photo-prompt', icon: '📋', title: 'Скопировать промпт',
      note: 'промпт уходит в буфер: нарисуйте картинку бесплатно где угодно, ' +
        'сохраните её в галерею и возьмите сюда камерой' },
    { name: 'photo-drop', icon: '🗑', title: 'Убрать картинку',
      note: 'отвязать картинку от слова', needsImage: true }
  ];

  function photoButtons(prefix, hasImage, inForm) {
    return PHOTO_ACTIONS.filter(function (action) {
      return hasImage || !action.needsImage;
    }).map(function (action) {
      return '<button' + (inForm ? ' type="button"' : '') + ' class="sym-btn" data-' +
        prefix + action.name +
        ' title="' + esc(action.title) + '" aria-label="' + esc(action.title) + '">' +
        action.icon + '</button>';
    }).join('');
  }

  /* Полоса под формой ввода. Кроме готовой генерации есть путь без затрат:
     скопировать промпт, нарисовать картинку где угодно и вставить обратно. */
  function photoRow() {
    return '<div class="photo-row" id="photo-row">' +
      (pendingImage
        ? '<span class="picture picture--slot" data-image-pending ' +
          'title="Открыть во весь экран" style="background-image:url(' + pendingImage + ')"></span>'
        : '<span class="picture picture--slot picture--empty" aria-hidden="true"></span>') +
      '<div class="photo-row__actions">' +
      photoButtons('', !!pendingImage, false) +
      '</div></div>';
  }

  function copyPromptFor(word, translation) {
    if (!word) { toast('Сначала впишите слово'); return; }
    window.Media.copyPrompt(word, translation).then(function () {
      toast('Промпт скопирован — вставьте его в любой рисующий ИИ');
    }, function (error) { toast(error.message); });
  }

  function summaryBlock(scope) {
    var s = Store.stats(scope);
    return '<div class="summary">' +
      '<div class="glass summary__cell"><div class="summary__value">' + s.total + '</div>' +
      '<div class="summary__label">' + plural(s.total, 'слово', 'слова', 'слов') + '</div></div>' +
      '<div class="glass summary__cell summary__cell--due"><div class="summary__value">' +
      (s.fresh ? s.fresh : s.due) + '</div>' +
      '<div class="summary__label">' + (s.fresh ? 'новых' : 'к разбору') + '</div></div>' +
      '<div class="glass summary__cell summary__cell--mastered"><div class="summary__value">' + s.mastered + '</div>' +
      '<div class="summary__label">освоено</div></div>' +
      '</div>';
  }

  var LEARN_BATCH = 10;

  /* Новые слова сначала показываются, и только потом спрашиваются —
     десятками, чтобы за один заход набиралось ровно столько, сколько
     удерживается в голове. */
  function learnBlock(scope) {
    var fresh = Store.freshCount(scope);
    if (!fresh) return '';
    var portion = Math.min(LEARN_BATCH, fresh);
    return '<section class="glass call">' +
      '<div class="call__text">' +
      '<h2 class="call__title">' + fresh + ' ' + plural(fresh, 'новое слово', 'новых слова', 'новых слов') + '</h2>' +
      '<p class="call__hint">Сначала посмотрите ' + portion + ' ' +
      plural(portion, 'слово', 'слова', 'слов') + ', потом сразу проверка</p>' +
      '</div>' +
      '<button class="btn btn--calm" data-start-learn>Учить</button>' +
      '</section>';
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
    /* Пока есть новые слова, разделу рано подводить итог — рядом уже
       стоит приглашение их выучить. */
    if (stats.fresh) return '';

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
        '<p class="empty__text">Нажмите «+» и создайте первый блок —<br>например, «Блок 1».</p>' +
        '</div>';
      screenEl.innerHTML = html;
      return;
    }

    /* Блоки живут порознь: учат и разбирают внутри блока, а на общем
       экране блок только сообщает, сколько его слов ждёт. Иначе немецкий
       и французский перемешались бы в одном разборе. */
    html += summaryBlock({});
    html += '<h2 class="section-title">Блоки<span class="section-title__note">' +
      blocks.length + '</span></h2>';

    blocks.forEach(function (block) {
      var words = Store.countWords(block);
      var scope = { blockId: block.id };
      var due = Store.dueCount(scope);
      html += '<article class="glass card" data-id="' + block.id + '" data-open-block="' + block.id + '">' +
        '<div class="card__head"><h3 class="card__title">' + esc(block.title) + '</h3>' +
        '<span class="card__chips">' + dueChip(due) + freshChip(Store.freshCount(scope)) + '</span></div>' +
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
    html += learnBlock({ blockId: block.id });
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
      var setScope = { blockId: block.id, setId: set.id };
      var due = Store.dueCount(setScope);
      var full = count >= MAX;
      html += '<article class="glass card" data-id="' + set.id + '" data-open-set="' + set.id + '">' +
        '<div class="card__head"><h3 class="card__title">' + esc(set.title) + '</h3>' +
        '<span class="card__chips">' + dueChip(due) + freshChip(Store.freshCount(setScope)) + '</span></div>' +
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

    html += learnBlock(scope);
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
        photoRow() +
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

      /* Сколько раз каждое слово встречается во всей картотеке —
         считаем разом, чтобы не искать повторы для каждой строки. */
      var repeats = new Map();
      Store.collectWords({}).forEach(function (item) {
        var key = item.word.de.trim().toLowerCase();
        repeats.set(key, (repeats.get(key) || 0) + 1);
      });

      /* Свежая запись стоит первой: только что добавленное слово видно
         сразу, без прокрутки в конец. Номер при этом остаётся настоящим —
         тем же, что в выгруженном файле. */
      var listed = set.words.map(function (word, index) {
        return { word: word, no: index + 1 };
      }).reverse();

      listed.forEach(function (item) {
        var word = item.word;
        if (needle &&
          word.de.toLowerCase().indexOf(needle) < 0 &&
          word.ru.toLowerCase().indexOf(needle) < 0) return;
        shown++;

        var label = Store.dueLabel(word);
        var chipClass = word.mastered ? 'chip chip--calm' : (label === 'сейчас' ? 'chip chip--due' : 'chip');
        var repeated = (repeats.get(word.de.trim().toLowerCase()) || 0) > 1;
        var note = word.ru ? esc(word.ru) : '<span style="opacity:.65">нет перевода — в разбор не попадёт</span>';

        rows += '<div class="ledger__row" data-id="' + word.id + '" data-row="' + word.id + '">' +
          '<div class="ledger__no">' + item.no + '</div>' +
          '<div class="ledger__de">' + esc(word.de) + '</div>' +
          '<div class="ledger__meta">' +
          '<span class="ledger__ru">' + note + '</span>' +
          '<span class="' + chipClass + '">' + label + '</span>' +
          (repeated
            ? '<button class="chip chip--warn" data-show-same="' + word.id + '">повтор</button>'
            : '') +
          '</div>' +
          '<div class="ledger__side">' +
          (word.image ? imageBox(word.image.id, 'picture--thumb') : '') +
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


  /* Панель над карточкой: выход, правка слова и размер. */
  var sizePanelOpen = false;

  function drillBar(wordId) {
    return '<div class="drill__bar">' +
      '<div class="drill__bar-side">' +
      '<button class="round-btn" data-drill-exit title="Назад" aria-label="Назад">' + ICON_BACK + '</button>' +
      '</div>' +
      '<div class="drill__bar-side">' +
      (wordId ? '<button class="mini-btn" data-drill-edit="' + wordId + '">Изменить</button>' : '') +
      '<button class="mini-btn' + (sizePanelOpen ? ' mini-btn--on' : '') + '" data-drill-size ' +
      'title="Размер текста и картинки" aria-label="Размер текста и картинки">Aa</button>' +
      '</div></div>' +
      (sizePanelOpen ? sizePanel() : '');
  }

  /* Каждая часть карточки тянется своим ползунком: картинка, поле ввода,
     само слово и перевод. Удобный размер у каждой из них свой. */
  var SIZE_ROWS = [
    { kind: 'image', label: 'Картинка' },
    { kind: 'input', label: 'Поле ввода' },
    { kind: 'word', label: 'Слово' },
    { kind: 'translation', label: 'Перевод' }
  ];

  function sizePanel() {
    return '<section class="glass sizes" id="size-panel">' +
      SIZE_ROWS.map(function (row) { return sizeRow(row.kind, row.label); }).join('') +
      '</section>';
  }

  function sizeRow(kind, label) {
    var percent = Math.round(Store.getScale(kind) * 100);
    return '<div class="sizes__row">' +
      '<div class="sizes__head"><span>' + label + '</span>' +
      '<span class="sizes__value" data-size-value="' + kind + '">' + percent + '%</span></div>' +
      '<input class="slider" type="range" min="60" max="240" step="4" value="' + percent + '" ' +
      'data-size="' + kind + '" aria-label="Размер: ' + esc(label) + '">' +
      '</div>';
  }

  /* Размеры живут в самой карточке: так их можно менять на ходу,
     не перерисовывая экран под пальцем. */
  function drillAttrs(dataId) {
    var style = Store.SCALE_KINDS.map(function (kind) {
      return '--drill-' + kind + ':' + Store.getScale(kind).toFixed(2);
    }).join(';');
    return 'class="glass drill" style="' + style + '"' +
      (dataId ? ' data-id="' + dataId + '"' : '');
  }

  /* Показ новых слов: немецкое слово, под ним перевод и картинка. */
  function renderLearnCard() {
    var item = session.queue[session.index];
    var word = item.word;
    var total = session.queue.length;
    var percent = ((session.index + 1) / total) * 100;
    var last = session.index === total - 1;

    screenEl.innerHTML = drillBar(word.id) +
      '<section ' + drillAttrs('learn-' + word.id) + '>' +
      '<div class="drill__progress"><div class="drill__progress-fill" style="width:' + percent.toFixed(1) + '%"></div></div>' +
      '<div class="drill__eyebrow">Знакомство · ' + (session.index + 1) + ' из ' + total + '</div>' +
      '<h2 class="drill__prompt drill__word">' + esc(word.de) + '</h2>' +
      '<p class="learn__translation drill__translation">' + esc(word.ru) + '</p>' +
      (word.image ? imageBox(word.image.id, 'picture--card') : '') +
      '<div class="drill__actions">' +
      '<button class="btn btn--accent btn--wide" data-learn-next>' +
      (last ? 'Перейти к проверке' : 'Дальше') + '</button>' +
      '</div></section>';
  }

  function renderDrill() {
    if (!session) { go({ name: 'blocks' }, true); return; }

    if (session.phase === 'show') { renderLearnCard(); return; }

    var total = session.total;
    var done = Math.min(session.index, total);
    var percent = total ? (done / total) * 100 : 100;

    if (session.index >= session.queue.length) {
      var moreFresh = session.mode === 'learn' ? Store.freshCount(session.scope) : 0;
      screenEl.innerHTML = '<section ' + drillAttrs() + '>' +
        '<h2 class="drill__prompt">' +
        (session.mode === 'learn' ? 'Десятка пройдена' : 'Разбор окончен') + '</h2>' +
        '<p class="drill__hint">Верно с первого раза: ' + session.correct + ' из ' + session.asked + '</p>' +
        (session.forgot ? '<p class="drill__hint">Вернулось в начало: ' + session.forgot + '</p>' : '') +
        '<div class="drill__actions">' +
        (moreFresh
          ? '<button class="btn btn--calm btn--wide" data-learn-more>Следующие ' +
            Math.min(LEARN_BATCH, moreFresh) + '</button>'
          : '') +
        '<button class="btn' + (moreFresh ? '' : ' btn--accent') + ' btn--wide" data-drill-exit>Готово</button>' +
        '</div></section>';
      return;
    }

    var item = session.queue[session.index];
    var word = item.word;

    var html = drillBar(word.id) +
      '<section ' + drillAttrs('drill-' + word.id + '-' + session.index) + '>' +
      '<div class="drill__progress"><div class="drill__progress-fill" style="width:' + percent.toFixed(1) + '%"></div></div>' +
      '<div class="drill__eyebrow">Слово ' + (done + 1) + ' из ' + total + '</div>';

    if (session.state === 'ask') {
      /* Поле стоит первым: когда открывается клавиатура, экран сжимается
         снизу — и перевод с картинкой остаются на месте, а не подпрыгивают.

         Буквы принимает скрытое парольное поле — на нём клавиатура не
         предлагает готовые слова. Сам набранный текст рисуется рядом,
         поэтому видно, что печатаешь. */
      html += '<div class="answer" data-answer-shell>' +
        '<span class="answer__text" id="answer-text"></span>' +
        '<span class="answer__caret" aria-hidden="true"></span>' +
        '<span class="answer__hint" id="answer-hint">…</span>' +
        '<input class="answer__field" type="password" id="drill-input" ' +
        'autocomplete="off" autocapitalize="off" autocorrect="off" spellcheck="false" ' +
        'inputmode="text" name="answer" aria-label="Ответ">' +
        '</div>' +
        '<p class="drill__hint drill__hint--under">Напишите это слово по-немецки</p>' +
        '<h2 class="drill__prompt drill__translation">' + esc(word.ru) + '</h2>' +
        (word.image ? imageBox(word.image.id, 'picture--card') : '') +
        '<div class="drill__actions">' +
        '<button class="btn" data-reveal>Забыл</button>' +
        '<button class="btn btn--accent" data-check>Проверить</button>' +
        '</div>';
    } else {
      html += '<h2 class="drill__prompt drill__translation">' + esc(word.ru) + '</h2>' +
        '<div class="drill__verdict">' +
        '<div class="verdict verdict--wrong">' +
        '<div class="verdict__label">' +
        (session.state === 'revealed' ? 'Правильный ответ' : 'Не совпало') + '</div>' +
        '<p class="verdict__word drill__word">' + esc(word.de) + '</p>' +
        (session.state === 'wrong'
          ? '<p class="verdict__yours">Вы написали: <s>' + esc(session.answer) + '</s></p>'
          : '') +
        '<div class="verdict__choice">' +
        (session.state === 'wrong'
          ? '<button class="btn btn--calm" data-outcome="typo">Опечатка</button>' +
            '<button class="btn btn--accent" data-outcome="forgot">Забыл</button>'
          : '<button class="btn btn--accent" data-next>Дальше</button>') +
        '</div></div></div>';
    }

    html += '</section>';
    screenEl.innerHTML = html;
    /* Клавиатура не открывается сама: иначе экран подпрыгивает
       при каждом новом слове. */
    if (session.state === 'ask') bindAnswerField();
  }

  /* Скрытое поле принимает буквы, а видимая строка их показывает. */
  function bindAnswerField() {
    var field = document.getElementById('drill-input');
    var text = document.getElementById('answer-text');
    var placeholder = document.getElementById('answer-hint');
    var shell = screenEl.querySelector('[data-answer-shell]');
    if (!field || !text || !shell) return;

    function paint() {
      text.textContent = field.value;
      if (placeholder) placeholder.hidden = !!field.value;
    }

    field.addEventListener('input', paint);
    field.addEventListener('focus', function () { shell.classList.add('answer--active'); });
    field.addEventListener('blur', function () { shell.classList.remove('answer--active'); });
    shell.addEventListener('click', function () { field.focus(); });
    paint();
  }

  function startLearn(scope) {
    var batch = Store.buildLearnBatch(scope, LEARN_BATCH);
    if (!batch.length) { toast('Новых слов нет'); return; }
    session = {
      mode: 'learn', phase: 'show', scope: scope, queue: batch, total: batch.length,
      index: 0, correct: 0, asked: 0, forgot: 0, state: 'ask', answer: '', nextNote: ''
    };
    go({ name: 'drill', blockId: scope.blockId, setId: scope.setId });
  }

  /* Показ закончился — те же слова тут же спрашиваются. */
  function learnNext() {
    if (!session) return;
    var card = screenEl.querySelector('.drill');
    var move = function () {
      if (session.index + 1 < session.queue.length) {
        session.index++;
      } else {
        session.phase = 'test';
        session.index = 0;
      }
      render();
    };
    evaporate(card, move);
  }

  function startDrill(scope, label) {
    var queue = Store.buildQueue(scope);
    if (!queue.length) { toast('На сегодня слов для разбора нет'); return; }
    session = {
      mode: 'drill', phase: 'test', scope: scope, label: label, queue: queue, total: queue.length,
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
      Store.reviewWord(item.word.id, 'correct');
      session.correct++;
      /* Ответ верный — сразу следующее слово, без разговоров о сроках. */
      toast('Верно');
      nextWord();
    } else {
      session.state = 'wrong';
      render();
    }
  }

  /* «Забыл» до ответа: слово показывается и уходит в начало лестницы. */
  function revealAnswer() {
    if (!session || session.state !== 'ask') return;
    var item = session.queue[session.index];
    session.asked++;
    session.forgot++;
    session.state = 'revealed';
    session.answer = '';
    Store.reviewWord(item.word.id, 'forgot');
    session.queue.push(item);
    render();
  }

  function resolveWrong(outcome) {
    if (!session) return;
    var item = session.queue[session.index];
    var result = Store.reviewWord(item.word.id, outcome);

    if (outcome === 'forgot') {
      session.forgot++;
      /* Слово начинает лестницу заново и вернётся в конце этого разбора. */
      session.queue.push(item);
      toast('Слово вернулось в начало');
    } else {
      toast(result && result.mastered ? 'Засчитано · слово освоено' : 'Засчитано');
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
      /* В разборе стрелка уже стоит над карточкой — второй такой же
         рядом с путём быть не должно. */
      if (route.name !== 'drill') {
        parts.push('<button class="round-btn crumbs__back" data-go-back title="Назад" ' +
          'aria-label="Назад">' + ICON_BACK + '</button>');
      }
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
        var stage = 'Разбор';
        if (session && session.mode === 'learn') {
          stage = session.phase === 'show' ? 'Знакомство' : 'Проверка';
        }
        parts.push('<span class="crumbs__sep">/</span><span class="crumbs__current">' + stage + '</span>');
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
    var fresh = Store.freshCount({});
    if (!total) note = 'Слова наизусть';
    else if (due) note = due + ' ' + plural(due, 'слово', 'слова', 'слов') + ' к разбору';
    else if (fresh) note = fresh + ' ' + plural(fresh, 'новое слово', 'новых слова', 'новых слов');
    else {
      var next = Store.nextDueAt({}, moment);
      note = next ? 'следующие ' + whenText(next, moment) : 'все слова освоены';
    }

    subEl.textContent = clockText(moment) + ' · ' + note;
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

    if (due !== lastDueCount && route.name !== 'drill' && modalRoot.hidden) {
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
    hydrateImages(screenEl);

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
      hint: 'Блок собирает списки слов по теме, уровню или языку.',
      fields: [{
        name: 'title', label: 'Название блока',
        placeholder: 'Блок ' + (Store.getState().blocks.length + 1)
      }],
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

  function refreshPhotoRow() {
    var row = document.getElementById('photo-row');
    if (!row) return;
    row.outerHTML = photoRow();
  }

  /* Общая для формы и правки слова генерация картинки. */
  function generatePicture(word, translation, onReady) {
    if (!window.Media.hasKey()) {
      toast('Укажите ключ Google AI в настройках');
      openSettings();
      return;
    }
    toast('Рисую картинку…');
    window.Media.generate(word, translation).then(function (dataUrl) {
      onReady(dataUrl);
      toast('Картинка готова');
    }, function (error) {
      toast(error.message);
    });
  }

  /* Умный путь: сперва текстовая модель разбирает слово и пишет промпт,
     и только потом по этому промпту рисуется картинка. */
  function smartPicture(word, translation, onReady) {
    if (!window.Media.hasKey()) {
      toast('Укажите ключ Google AI в настройках');
      openSettings();
      return;
    }
    toast('ИИ разбирает слово…');
    window.Media.generateSmart(word, translation, function () {
      toast('Промпт готов — рисую…');
    }).then(function (dataUrl) {
      onReady(dataUrl);
      toast('Картинка готова');
    }, function (error) {
      toast(error.message);
    });
  }

  function startLearnInPlace(scope) {
    var batch = Store.buildLearnBatch(scope, LEARN_BATCH);
    if (!batch.length) { history.back(); return; }
    session = {
      mode: 'learn', phase: 'show', scope: scope, queue: batch, total: batch.length,
      index: 0, correct: 0, asked: 0, forgot: 0, state: 'ask', answer: '', nextNote: ''
    };
    render();
  }

  function addWordFromForm() {
    var deEl = document.getElementById('input-de');
    var ruEl = document.getElementById('input-ru');
    if (!deEl) return;

    /* Такое слово уже может быть записано — показываем, где именно,
       и оставляем выбор за человеком. */
    var same = Store.findSameWords(deEl.value);
    if (same.length) {
      var places = same.slice(0, 4).map(function (item) {
        return '<li>' + esc(item.blockTitle) + ' → ' + esc(item.setTitle) +
          ', №' + item.index + ' <span style="opacity:.7">(' + esc(item.word.ru || 'без перевода') + ')</span></li>';
      }).join('');

      openForm({
        title: 'Такое слово уже есть',
        hint: '«' + esc(deEl.value.trim()) + '» записано ' +
          (same.length > 1 ? same.length + ' раза' : 'здесь') + ':' +
          '<ul style="margin:8px 0 0;padding-left:18px;line-height:1.7">' + places + '</ul>' +
          (same.length > 4 ? '<p style="margin:6px 0 0">…и ещё ' + (same.length - 4) + '</p>' : ''),
        fields: [],
        cancelText: 'Убрать',
        submitText: 'Оставить',
        onSubmit: function () { commitWord(deEl.value, ruEl.value); }
      });
      return;
    }

    commitWord(deEl.value, ruEl.value);
  }

  function commitWord(de, ru) {
    var result = Store.addWord(route.blockId, route.setId, de, ru);
    if (!result.ok) { toast(result.reason); return; }

    /* Картинка, подобранная до нажатия «Добавить», привязывается к слову. */
    if (pendingImage) {
      var imageId = Store.uid();
      var picture = pendingImage;
      pendingImage = null;
      Store.imageSave(imageId, picture).then(function () {
        imageCache.set(imageId, picture);
        Store.setWordImage(result.word.id, imageId, 'photo');
        render();
      }, function () { toast('Картинку сохранить не удалось'); });
    }

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

  /* Ищем слово по всей картотеке: править его можно и во время проверки,
     когда слово пришло из другого списка. */
  function locateWord(wordId) {
    var found = null;
    Store.getState().blocks.forEach(function (block) {
      block.sets.forEach(function (set) {
        set.words.forEach(function (word) {
          if (word.id === wordId) found = { word: word, blockId: block.id, setId: set.id };
        });
      });
    });
    return found;
  }

  function editWord(wordId) { editWordById(wordId); }

  function editWordById(wordId) {
    var place = locateWord(wordId);
    if (!place) return;
    var word = place.word;

    openForm({
      title: 'Изменить слово',
      fields: [
        { name: 'de', label: 'Слово по-немецки', value: word.de },
        { name: 'ru', label: 'Перевод', value: word.ru }
      ],
      extra: '<div class="photo-row" id="edit-photo-row">' +
        (word.image
          ? imageBox(word.image.id, 'picture--slot')
          : '<span class="picture picture--slot picture--empty" aria-hidden="true"></span>') +
        '<div class="photo-row__actions">' +
        photoButtons('edit-', !!word.image, true) +
        '</div></div>',
      submitText: 'Сохранить',
      onSubmit: function (values) {
        Store.updateWord(place.blockId, place.setId, wordId, values.de, values.ru);
        render();
      }
    });

    hydrateImages(modalRoot);

    /* Картинка меняется сразу, не дожидаясь кнопки «Сохранить». */
    function attach(dataUrl, kind) {
      var imageId = Store.uid();
      Store.imageSave(imageId, dataUrl).then(function () {
        imageCache.set(imageId, dataUrl);
        Store.setWordImage(wordId, imageId, kind);
        closeModal();
        render();
        editWordById(wordId);
      }, function () { toast('Картинку сохранить не удалось'); });
    }

    var pick = modalRoot.querySelector('[data-edit-photo-pick]');
    if (pick) pick.addEventListener('click', function () {
      window.Media.pickFromDevice().then(function (dataUrl) {
        if (dataUrl) attach(dataUrl, 'photo');
      }, function (error) { toast(error.message); });
    });

    var ai = modalRoot.querySelector('[data-edit-photo-ai]');
    if (ai) ai.addEventListener('click', function () {
      var deField = document.getElementById('f-de');
      var ruField = document.getElementById('f-ru');
      var germanWord = deField ? deField.value.trim() : word.de;
      if (!germanWord) { toast('Сначала впишите слово'); return; }
      generatePicture(germanWord, ruField ? ruField.value.trim() : word.ru, function (dataUrl) {
        attach(dataUrl, 'ai');
      });
    });

    var smart = modalRoot.querySelector('[data-edit-photo-smart]');
    if (smart) smart.addEventListener('click', function () {
      var deField = document.getElementById('f-de');
      var ruField = document.getElementById('f-ru');
      var germanWord = deField ? deField.value.trim() : word.de;
      if (!germanWord) { toast('Сначала впишите слово'); return; }
      smartPicture(germanWord, ruField ? ruField.value.trim() : word.ru, function (dataUrl) {
        attach(dataUrl, 'ai');
      });
    });

    var promptButton = modalRoot.querySelector('[data-edit-photo-prompt]');
    if (promptButton) promptButton.addEventListener('click', function () {
      var deField = document.getElementById('f-de');
      var ruField = document.getElementById('f-ru');
      copyPromptFor(
        deField ? deField.value.trim() : word.de,
        ruField ? ruField.value.trim() : word.ru
      );
    });

    var drop = modalRoot.querySelector('[data-edit-photo-drop]');
    if (drop) drop.addEventListener('click', function () {
      Store.setWordImage(wordId, null);
      closeModal();
      render();
      toast('Картинка убрана');
    });
  }

  /* ---------- Настройки ---------- */

  /* Список моделей: выбранная отмечена, свою можно добавить и убрать.
     Устроены одинаково и та, что рисует, и та, что пишет промпт. */
  var MODEL_KINDS = {
    image: {
      list: function () { return Store.listModels(); },
      current: function () { return Store.getModel(); },
      choose: function (id) { Store.setModel(id); },
      add: function (id) { Store.addModel(id, id); },
      drop: function (id) { Store.removeModel(id); }
    },
    think: {
      list: function () { return Store.listThinkModels(); },
      current: function () { return Store.getThinkModel(); },
      choose: function (id) { Store.setThinkModel(id); },
      add: function (id) { Store.addThinkModel(id, id); },
      drop: function (id) { Store.removeThinkModel(id); }
    }
  };

  function modelsHtml(kind) {
    var family = MODEL_KINDS[kind];
    var current = family.current();
    return family.list().map(function (model) {
      return '<div class="model' + (model.id === current ? ' model--on' : '') +
        '" data-model="' + esc(model.id) + '" role="button" tabindex="0">' +
        '<span class="model__mark" aria-hidden="true"></span>' +
        '<span class="model__text">' +
        '<span class="model__title">' + esc(model.title) + '</span>' +
        '<span class="model__id">' + esc(model.id) + '</span></span>' +
        (model.custom
          ? '<button type="button" class="icon-btn" data-model-remove="' + esc(model.id) +
            '" title="Убрать" aria-label="Убрать модель">' + ICON_DELETE + '</button>'
          : '') +
        '</div>';
    }).join('');
  }

  /* Раздел настроек с выбором модели: список, «+» и поле для своей. */
  function modelSection(kind, title, note) {
    return '<div class="setting">' +
      '<div class="setting__head"><span class="field__label">' + esc(title) + '</span>' +
      '<button type="button" class="round-btn round-btn--small" data-model-add="' + kind + '" ' +
      'title="Добавить свою модель" aria-label="Добавить свою модель">+</button></div>' +
      '<div class="models" data-models="' + kind + '">' + modelsHtml(kind) + '</div>' +
      '<div class="model-add" data-model-adder="' + kind + '" hidden>' +
      '<input class="input" type="text" data-model-field="' + kind + '" ' +
      'placeholder="имя модели" autocomplete="off" autocapitalize="off" spellcheck="false">' +
      '<button type="button" class="btn btn--quiet" data-model-save="' + kind + '">Добавить</button>' +
      '</div>' +
      (note ? '<p class="setting__note">' + note + '</p>' : '') +
      '</div>';
  }

  /* Длинные разделы свёрнуты: окно настроек иначе растягивается
     на несколько экранов, а нужны они не каждый раз. */
  function fold(title, body) {
    return '<details class="fold"><summary class="fold__head">' + esc(title) + '</summary>' +
      '<div class="fold__body">' + body + '</div></details>';
  }

  function promptSection(id, title, resetAttr, value) {
    return fold(title,
      '<div class="btn-row" style="margin-bottom:9px">' +
      '<button type="button" class="btn btn--quiet btn--wide" ' + resetAttr +
      '>Вернуть стандартный</button></div>' +
      '<textarea class="textarea textarea--tall" id="' + id + '" spellcheck="false">' +
      esc(value) + '</textarea>');
  }

  /* Памятка по значкам: сами кнопки подписей не носят, чтобы не занимать
     место, — расшифровка живёт здесь. */
  function symbolsHtml() {
    return fold('Что означают значки',
      '<div class="legend">' +
      PHOTO_ACTIONS.map(function (action) {
        return '<div class="legend__row">' +
          '<span class="legend__icon" aria-hidden="true">' + action.icon + '</span>' +
          '<span class="legend__text"><b>' + esc(action.title) + '</b><br>' +
          esc(action.note) + '</span></div>';
      }).join('') +
      '</div>');
  }

  function openSettings() {
    var key = Store.getApiKey();
    var prompt = Store.getPromptTemplate() || window.Media.DEFAULT_PROMPT;
    var thinkPrompt = Store.getThinkTemplate() || window.Media.DEFAULT_THINK_PROMPT;

    openForm({
      title: 'Картинки от ИИ',
      hint: 'Картинки рисует Gemini по вашему ключу Google AI. Ключ лежит только ' +
        'на этом устройстве и в резервную копию не попадает — взять его можно ' +
        'на aistudio.google.com/apikey.<br><br>' +
        'На опубликованной пробной странице запросы наружу закрыты: рисование ' +
        'работает в приложении и в файле, открытом с устройства.',
      fields: [{
        name: 'key', label: 'Ключ Google AI',
        value: key, placeholder: 'вставьте ключ'
      }],
      extra: '<div class="btn-row" style="margin-top:2px">' +
        '<button type="button" class="btn btn--quiet btn--wide" data-check-access>Проверить связь</button>' +
        '</div>' +

        modelSection('image', 'Модель рисования 🎨') +
        modelSection('think', 'Модель для умного промпта 🧠',
          'Обычная текстовая модель: она разбирает слово и пишет промпт, ' +
          'а рисует по нему всё равно выбранная выше.') +

        promptSection('f-prompt', 'Промпт для картинок 🎨', 'data-prompt-reset', prompt) +
        promptSection('f-think-prompt', 'Промпт для умного режима 🧠',
          'data-think-reset', thinkPrompt) +
        symbolsHtml() +
        '<p class="setting__note">В обоих промптах вместо ' + esc(window.Media.SLOT_WORD) +
        ' подставится слово, вместо ' + esc(window.Media.SLOT_MEANING) + ' — перевод. ' +
        'Первый уходит прямо в рисующую модель, второй — в ту, что сочиняет промпт.</p>',
      submitText: 'Сохранить',
      onSubmit: function (values) {
        Store.setApiKey(values.key);
        savePrompt('f-prompt', window.Media.DEFAULT_PROMPT, Store.setPromptTemplate);
        savePrompt('f-think-prompt', window.Media.DEFAULT_THINK_PROMPT, Store.setThinkTemplate);
        toast('Сохранено');
      }
    });

    wireModels('image');
    wireModels('think');

    modalRoot.querySelector('[data-prompt-reset]').addEventListener('click', function () {
      resetPrompt('f-prompt', window.Media.DEFAULT_PROMPT, Store.setPromptTemplate);
    });

    modalRoot.querySelector('[data-think-reset]').addEventListener('click', function () {
      resetPrompt('f-think-prompt', window.Media.DEFAULT_THINK_PROMPT, Store.setThinkTemplate);
    });

    modalRoot.querySelector('[data-check-access]').addEventListener('click', function () {
      var field = document.getElementById('f-key');
      if (field) Store.setApiKey(field.value);
      toast('Проверяю связь…');
      window.Media.checkAccess().then(function (message) {
        toast(message);
      }, function (error) {
        toast(error.message);
      });
    });
  }

  /* Нетронутый промпт не запоминаем — тогда он будет обновляться
     вместе с приложением. */
  function savePrompt(id, standard, store) {
    var area = document.getElementById(id);
    if (!area) return;
    var text = area.value.trim();
    store(text === standard.trim() ? '' : text);
  }

  function resetPrompt(id, standard, store) {
    var area = document.getElementById(id);
    if (area) area.value = standard;
    store('');
    toast('Промпт вернулся к стандартному');
  }

  function wireModels(kind) {
    var family = MODEL_KINDS[kind];
    var box = modalRoot.querySelector('[data-models="' + kind + '"]');
    var adder = modalRoot.querySelector('[data-model-adder="' + kind + '"]');
    var field = modalRoot.querySelector('[data-model-field="' + kind + '"]');

    function repaint() { box.innerHTML = modelsHtml(kind); }

    box.addEventListener('click', function (event) {
      var drop = event.target.closest('[data-model-remove]');
      if (drop) {
        family.drop(drop.getAttribute('data-model-remove'));
        repaint();
        return;
      }
      var row = event.target.closest('[data-model]');
      if (!row) return;
      family.choose(row.getAttribute('data-model'));
      repaint();
    });

    modalRoot.querySelector('[data-model-add="' + kind + '"]')
      .addEventListener('click', function () {
        adder.hidden = !adder.hidden;
        if (!adder.hidden) field.focus();
      });

    function saveModel() {
      var id = field.value.trim();
      if (!id) { field.focus(); return; }
      family.add(id);
      field.value = '';
      adder.hidden = true;
      repaint();
      toast('Модель добавлена и выбрана');
    }

    modalRoot.querySelector('[data-model-save="' + kind + '"]')
      .addEventListener('click', saveModel);

    /* Enter в имени модели добавляет её, а не сохраняет всё окно. */
    field.addEventListener('keydown', function (event) {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      saveModel();
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
          footer: 'Lexis'
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
        '</div>' +
        '<div class="btn-row" style="margin-top:9px">' +
        '<button type="button" class="btn btn--quiet btn--wide" data-open-settings>' +
        'Картинки от ИИ · ключ, модель, промпт' +
        '</button></div>',
      onSubmit: function () { }
    });

    modalRoot.querySelector('[data-open-settings]').addEventListener('click', function () {
      closeModal();
      openSettings();
    });

    modalRoot.querySelector('[data-archive-save]').addEventListener('click', function () {
      var name = 'lexis-' + new Date().toISOString().slice(0, 10) + '.json';
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

  /* Картинка в окне правки открывается так же, как и везде. */
  modalRoot.addEventListener('click', function (event) {
    var picture = event.target.closest('.picture[data-image-id], .picture[data-image-pending]');
    if (picture) openPicture(picture);
  });

  crumbsEl.addEventListener('click', function (event) {
    if (event.target.closest('[data-go-back]')) { session = null; history.back(); }
    else if (event.target.closest('[data-go-blocks]')) go({ name: 'blocks' });
    else if (event.target.closest('[data-go-block]')) go({ name: 'block', blockId: route.blockId });
    else if (event.target.closest('[data-go-set]')) go({ name: 'set', blockId: route.blockId, setId: route.setId });
  });

  screenEl.addEventListener('click', function (event) {
    var target = event.target;
    var el;

    /* Любая картинка открывается во весь экран — и в списке, и в проверке. */
    if ((el = target.closest('.picture[data-image-id], .picture[data-image-pending]'))) {
      openPicture(el);
      return;
    }

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

    if (target.closest('[data-start-learn]')) {
      var learnScope = {};
      if (route.name === 'block') learnScope = { blockId: route.blockId };
      if (route.name === 'set') learnScope = { blockId: route.blockId, setId: route.setId };
      startLearn(learnScope);
      return;
    }

    if (target.closest('[data-learn-next]')) { learnNext(); return; }

    if (target.closest('[data-learn-more]')) {
      var scopeAgain = session ? session.scope : {};
      session = null;
      startLearnInPlace(scopeAgain);
      return;
    }

    if (target.closest('[data-check]')) { submitAnswer(); return; }
    if (target.closest('[data-reveal]')) { revealAnswer(); return; }
    if (target.closest('[data-next]')) { nextWord(); return; }

    if (target.closest('[data-drill-size]')) {
      sizePanelOpen = !sizePanelOpen;
      render();
      return;
    }

    if ((el = target.closest('[data-drill-edit]'))) {
      editWordById(el.getAttribute('data-drill-edit'));
      return;
    }

    /* ---------- Картинка к слову ---------- */

    if (target.closest('[data-photo-pick]')) {
      window.Media.pickFromDevice().then(function (dataUrl) {
        if (!dataUrl) return;
        pendingImage = dataUrl;
        refreshPhotoRow();
      }, function (error) { toast(error.message); });
      return;
    }

    if (target.closest('[data-photo-ai]')) {
      var deField = document.getElementById('input-de');
      var ruField = document.getElementById('input-ru');
      if (!deField || !deField.value.trim()) { toast('Сначала впишите слово'); return; }
      generatePicture(deField.value.trim(), ruField ? ruField.value.trim() : '', function (dataUrl) {
        pendingImage = dataUrl;
        refreshPhotoRow();
      });
      return;
    }

    if (target.closest('[data-photo-smart]')) {
      var smartDe = document.getElementById('input-de');
      var smartRu = document.getElementById('input-ru');
      if (!smartDe || !smartDe.value.trim()) { toast('Сначала впишите слово'); return; }
      smartPicture(smartDe.value.trim(), smartRu ? smartRu.value.trim() : '', function (dataUrl) {
        pendingImage = dataUrl;
        refreshPhotoRow();
      });
      return;
    }

    if (target.closest('[data-photo-prompt]')) {
      var promptDe = document.getElementById('input-de');
      var promptRu = document.getElementById('input-ru');
      copyPromptFor(promptDe ? promptDe.value.trim() : '', promptRu ? promptRu.value.trim() : '');
      return;
    }

    if (target.closest('[data-photo-drop]')) {
      pendingImage = null;
      refreshPhotoRow();
      return;
    }

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

    if ((el = target.closest('[data-show-same]'))) {
      var sameId = el.getAttribute('data-show-same');
      var origin = locateWord(sameId);
      if (!origin) return;
      var others = Store.findSameWords(origin.word.de, sameId);
      openForm({
        title: 'Слово записано не один раз',
        hint: '«' + esc(origin.word.de) + '» встречается ещё здесь:' +
          '<ul style="margin:8px 0 0;padding-left:18px;line-height:1.7">' +
          others.map(function (item) {
            return '<li>' + esc(item.blockTitle) + ' → ' + esc(item.setTitle) + ', №' + item.index + '</li>';
          }).join('') + '</ul>',
        fields: [],
        cancelText: 'Закрыть',
        onSubmit: function () { }
      });
      return;
    }

    if ((el = target.closest('[data-delete-word]'))) {
      var wordId = el.getAttribute('data-delete-word');
      var doomed = locateWord(wordId);
      if (!doomed) return;
      confirmAction('Удалить слово?',
        '«' + esc(doomed.word.de) + '» исчезнет вместе с картинкой и всем прогрессом по нему.',
        'Удалить',
        function () {
          evaporate(screenEl.querySelector('[data-row="' + wordId + '"]'), function () {
            seen.delete(wordId);
            Store.removeWord(doomed.blockId, doomed.setId, wordId);
            render();
            toast('Слово удалено');
          });
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

  /* Ползунок размера: карточка меняется прямо под пальцем, без перерисовки. */
  screenEl.addEventListener('input', function (event) {
    var slider = event.target.closest && event.target.closest('[data-size]');
    if (slider) { applySize(slider); return; }

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
