/* Картинки к словам: снимок с устройства и рисунок, сделанный Gemini.
   Картинка запоминается вместе со словом — по переводу и образу слово
   вспоминается заметно легче, чем по одному переводу. */
window.Media = (function () {
  'use strict';

  var ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models/';
  var DEFAULT_MODEL = 'gemini-3.1-flash-lite-image';
  var DEFAULT_THINK_MODEL = 'gemini-3.6-flash';
  var MAX_SIDE = 640;
  var QUALITY = 0.82;

  var SLOT_WORD = '{слово}';
  var SLOT_MEANING = '{перевод}';

  /* Промпт устроен просто: модель сама определяет, что это за слово, и берёт
     подходящую строчку из списка. Главное правило — рисовать как можно меньше:
     стрелки и раскадровки только мешали, а конкретному предмету не нужно
     ничего, кроме самого предмета. Абстрактное показывается через человека
     в обычной житейской сцене — так понятнее, чем через символы.
     Запрещено ровно одно: само слово и его перевод в кадре.

     Текст лежит здесь целиком и в готовом виде: его можно переписать
     в настройках, и меняется ровно то, что уходит модели. Подстановки
     всего две — {слово} и {перевод}. */
  var DEFAULT_PROMPT =
    'Draw a picture that instantly shows what a foreign word means. ' +
    'A learner should understand it in one second, without thinking.\n\n' +
    'WORD: "' + SLOT_WORD + '"\n' +
    'MEANING: ' + SLOT_MEANING + '\n' +
    '\nWork out for yourself what kind of word this is, and draw it accordingly:\n' +
    '- a thing: just that thing, large, whole, centred, seen from its most recognisable ' +
    'angle, and nothing else in the frame\n' +
    '- an action: one person doing it, plainly\n' +
    '- a quality: one object or person that obviously has this quality; only if the ' +
    'quality makes no sense alone, put it beside its opposite\n' +
    '- a manner: one scene in which the manner is unmistakable\n' +
    '- a feeling: one face and posture that carry it\n' +
    '- an abstract idea or idiom: the simplest everyday situation where the idea is obvious\n' +
    '- a number or amount: exactly that many identical objects\n' +
    '- a position word: two plain objects placed so their relation is the whole picture\n\n' +
    'For anything abstract — a quality, a manner, a feeling, an idea, an idiom — show a ' +
    'person in an ordinary everyday situation where the meaning is plain to see. People ' +
    'and familiar scenes are remembered far better than symbols or abstract shapes.\n\n' +
    'SIMPLICITY IS THE RULE. Use as few elements as you can. No arrows, no motion lines, ' +
    'no split screens, no before-and-after pairs, no decorative extras, unless the meaning ' +
    'truly cannot be shown without them. When in doubt, draw less. A calm obvious picture ' +
    'is better than a clever one.\n\n' +
    'STYLE: flat vector illustration, clean simple shapes, warm palette of terracotta, ' +
    'sage green, sand and cream, plain light background, one clear subject, plenty of ' +
    'empty space. Square, 1:1.\n\n' +
    'THE ONLY RULE ABOUT TEXT: the picture must not show the word "' + SLOT_WORD + '"' +
    ' or its translation "' + SLOT_MEANING + '"' +
    ', or any form of either, anywhere. Other incidental text is acceptable, ' +
    'but keep it to a minimum.';

  /* Умный режим. Обычный промпт хорошо справляется с предметом, но на
     действии, качестве, чувстве или идиоме часто промахивается. Тогда за
     дело берётся текстовая модель: она разбирает слово и сама сочиняет
     описание сцены, которую нельзя понять неправильно, — а рисует по нему
     всё та же Nano Banana. Стиль и запрет на надписи остаются прежними. */
  var DEFAULT_THINK_PROMPT =
    'You write prompts for an image model that draws one picture for a vocabulary card.\n\n' +
    'WORD: "' + SLOT_WORD + '"\n' +
    'MEANING: ' + SLOT_MEANING + '\n\n' +
    'Think first. What kind of word is this — a thing, an action, a quality, a manner, ' +
    'a feeling, an abstract idea, an idiom? Is it concrete or abstract? What single ' +
    'everyday scene would make its meaning obvious in one second to someone who does not ' +
    'know the word and cannot read a caption? A plain prompt copes with things, but fails ' +
    'on actions, qualities, feelings and idioms — your work is to find the one scene that ' +
    'cannot be misread.\n\n' +
    'Then write a single image prompt in English describing that scene concretely: who is ' +
    'in the frame, what they are doing, what surrounds them, and what exactly makes the ' +
    'meaning unmistakable. Prefer people in ordinary situations over symbols.\n\n' +
    'Your prompt must keep these rules word for word:\n' +
    '- SIMPLICITY: as few elements as possible. No arrows, no motion lines, no split ' +
    'screens, no before-and-after pairs, no decorative extras.\n' +
    '- STYLE: flat vector illustration, clean simple shapes, warm palette of terracotta, ' +
    'sage green, sand and cream, plain light background, one clear subject, plenty of ' +
    'empty space. Square, 1:1.\n' +
    '- NO TEXT: the picture must not show the word "' + SLOT_WORD + '" or its translation "' +
    SLOT_MEANING + '", or any form of either, anywhere.\n\n' +
    'Answer with the prompt itself and nothing else — no explanation, no quotes, no headings.';

  function escapeSlot(slot) {
    return slot.replace(/[{}]/g, function (ch) { return '\\' + ch; });
  }

  /* Подстановка. Перевода может не быть — тогда куски, которые без него
     теряют смысл, из промпта уходят целиком. */
  function fillPrompt(template, word, translation) {
    var text = String(template == null ? '' : template);
    if (!translation) {
      text = text
        /* Строка вида «MEANING: {перевод}» без перевода не нужна вовсе. */
        .replace(new RegExp('^[^\\n]*:[ \\t]*' + escapeSlot(SLOT_MEANING) + '[ \\t]*\\n?', 'gm'), '')
        /* Оговорка про перевод внутри строки — тоже. */
        .replace(new RegExp('[ \\t]*or its translation "' + escapeSlot(SLOT_MEANING) + '"', 'g'), '');
    }
    return text
      .split(SLOT_WORD).join(word == null ? '' : word)
      .split(SLOT_MEANING).join(translation == null ? '' : translation);
  }

  /* Промпт берётся из настроек, если человек его переписал. */
  function template() {
    var own = window.Store.getPromptTemplate && window.Store.getPromptTemplate();
    return own || DEFAULT_PROMPT;
  }

  function buildPrompt(word, translation) {
    return fillPrompt(template(), word, translation);
  }

  function thinkTemplate() {
    var own = window.Store.getThinkTemplate && window.Store.getThinkTemplate();
    return own || DEFAULT_THINK_PROMPT;
  }

  function buildThinkPrompt(word, translation) {
    return fillPrompt(thinkTemplate(), word, translation);
  }

  /* ---------- Обмен через буфер ---------- */

  /* Готовый промпт уходит в буфер: картинку можно нарисовать где угодно,
     а потом вернуть её сюда — это ничего не стоит. */
  function copyPrompt(word, translation) {
    var text = buildPrompt(word, translation);

    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).catch(function () { return legacyCopy(text); });
    }
    return legacyCopy(text);
  }

  function legacyCopy(text) {
    return new Promise(function (resolve, reject) {
      var area = document.createElement('textarea');
      area.value = text;
      area.setAttribute('readonly', '');
      area.style.position = 'fixed';
      area.style.top = '-1000px';
      document.body.appendChild(area);
      area.select();
      area.setSelectionRange(0, text.length);
      var ok = false;
      try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
      document.body.removeChild(area);
      ok ? resolve() : reject(new Error('Скопировать не удалось — выделите промпт вручную'));
    });
  }

  /* Ужимаем картинку: полноразмерная не нужна, а места занимает много. */
  function shrink(dataUrl) {
    return new Promise(function (resolve, reject) {
      var image = new Image();
      image.onload = function () {
        var scale = Math.min(1, MAX_SIDE / Math.max(image.width, image.height));
        var width = Math.max(1, Math.round(image.width * scale));
        var height = Math.max(1, Math.round(image.height * scale));

        var canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        var context = canvas.getContext('2d');
        context.fillStyle = '#ffffff';
        context.fillRect(0, 0, width, height);
        context.drawImage(image, 0, 0, width, height);

        try {
          resolve(canvas.toDataURL('image/jpeg', QUALITY));
        } catch (e) {
          reject(new Error('не удалось обработать картинку'));
        }
      };
      image.onerror = function () { reject(new Error('файл не похож на картинку')); };
      image.src = dataUrl;
    });
  }

  /* Выбор снимка из галереи или камеры устройства. */
  function pickFromDevice() {
    return new Promise(function (resolve, reject) {
      var input = document.createElement('input');
      input.type = 'file';
      input.accept = 'image/*';
      input.style.position = 'fixed';
      input.style.left = '-1000px';
      document.body.appendChild(input);

      input.addEventListener('change', function () {
        var file = input.files && input.files[0];
        document.body.removeChild(input);
        if (!file) { resolve(null); return; }

        var reader = new FileReader();
        reader.onload = function () { shrink(String(reader.result)).then(resolve, reject); };
        reader.onerror = function () { reject(new Error('не удалось прочитать файл')); };
        reader.readAsDataURL(file);
      });

      input.click();
    });
  }

  function hasKey() { return !!window.Store.getApiKey(); }

  /* Один разговор с Gemini: тело запроса уходит, ответ разбирается,
     невнятные ошибки переводятся на человеческий. */
  function ask(model, body) {
    var key = window.Store.getApiKey();
    if (!key) return Promise.reject(new Error('Сначала укажите ключ Google AI в настройках'));

    return fetch(ENDPOINT + model + ':generateContent', {
      method: 'POST',
      headers: { 'x-goog-api-key': key, 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (response) {
      return response.json().then(function (data) {
        if (!response.ok) {
          var message = (data && data.error && data.error.message) || ('код ' + response.status);
          if (response.status === 400 && /API key/i.test(message)) {
            throw new Error('Ключ не принят — проверьте его в настройках');
          }
          if (response.status === 429) {
            throw new Error('Слишком часто — подождите немного и попробуйте снова');
          }
          if (response.status === 404) {
            throw new Error('Модель «' + model + '» недоступна — выберите другую в настройках');
          }
          throw new Error(message);
        }
        return data;
      });
    }, function (error) {
      /* Страница без доступа в сеть (например, опубликованная копия
         с ограничениями) сообщает об этом невнятно — переводим. */
      if (error instanceof TypeError) {
        throw new Error('Нет доступа к серверу Google с этой страницы');
      }
      throw error;
    });
  }

  function partsOf(data) {
    return (data.candidates && data.candidates[0] &&
      data.candidates[0].content && data.candidates[0].content.parts) || [];
  }

  /* Рисунок по готовому промпту. Ключ хранится на устройстве и в копию не попадает. */
  function draw(promptText, model) {
    return ask(model || window.Store.getModel() || DEFAULT_MODEL, {
      contents: [{ parts: [{ text: promptText }] }],
      generationConfig: {
        responseModalities: ['IMAGE'],
        imageConfig: { aspectRatio: '1:1' }
      }
    }).then(function (data) {
      var parts = partsOf(data);
      var picture = null;
      for (var i = 0; i < parts.length; i++) {
        var inline = parts[i].inlineData || parts[i].inline_data;
        if (inline && inline.data) {
          picture = 'data:' + (inline.mimeType || inline.mime_type || 'image/png') +
            ';base64,' + inline.data;
          break;
        }
      }
      if (!picture) throw new Error('Модель не вернула картинку — попробуйте ещё раз');
      return shrink(picture);
    });
  }

  function generate(word, translation, model) {
    return draw(buildPrompt(word, translation), model);
  }

  /* Модель отвечает текстом — иногда обрамляя его кавычками или разметкой. */
  function plainText(data) {
    var parts = partsOf(data);
    var text = '';
    for (var i = 0; i < parts.length; i++) {
      if (typeof parts[i].text === 'string') text += parts[i].text;
    }
    text = text.trim()
      .replace(/^```[a-z]*\s*/i, '')
      .replace(/```$/, '')
      .trim();
    if (text.length > 1 && /^["«'][\s\S]*["»']$/.test(text)) text = text.slice(1, -1).trim();
    return text;
  }

  /* Умный промпт: текстовая модель разбирает слово и пишет описание сама. */
  function think(word, translation, model) {
    return ask(model || window.Store.getThinkModel() || DEFAULT_THINK_MODEL, {
      contents: [{ parts: [{ text: buildThinkPrompt(word, translation) }] }]
    }).then(function (data) {
      var text = plainText(data);
      if (!text) throw new Error('Модель не написала промпт — попробуйте ещё раз');
      return guardText(text, word, translation);
    });
  }

  /* Подстраховка: если сочинённый промпт забыл запретить надписи,
     дописываем запрет сами — слово на картинке сводит проверку на нет. */
  function guardText(promptText, word, translation) {
    if (/must not show|no text|without any text/i.test(promptText)) return promptText;
    return promptText + '\n\nNO TEXT: the picture must not show the word "' + word + '"' +
      (translation ? ' or its translation "' + translation + '"' : '') +
      ', or any form of either, anywhere.';
  }

  /* Весь умный путь целиком: разбор слова, свой промпт, рисунок по нему. */
  function generateSmart(word, translation, onPrompt) {
    return think(word, translation).then(function (promptText) {
      if (onPrompt) onPrompt(promptText);
      return draw(promptText);
    });
  }

  /* Быстрая проверка связи: запрашивается список моделей, картинка не рисуется
     и деньги не тратятся. Нужна, чтобы сразу видеть, дело в ключе,
     в сети или в самом приложении. */
  function checkAccess() {
    var key = window.Store.getApiKey();
    if (!key) return Promise.reject(new Error('Ключ не указан'));

    return fetch('https://generativelanguage.googleapis.com/v1beta/models', {
      headers: { 'x-goog-api-key': key }
    }).then(function (response) {
      return response.json().then(function (data) {
        if (!response.ok) {
          throw new Error((data && data.error && data.error.message) || ('код ' + response.status));
        }
        var models = (data.models || []).filter(function (m) {
          return /image/.test(m.name || '');
        });
        return 'Связь есть · доступно моделей с картинками: ' + models.length;
      });
    }, function (error) {
      if (error instanceof TypeError) throw new Error('Нет доступа к серверу Google с этой страницы');
      throw error;
    });
  }

  return {
    DEFAULT_MODEL: DEFAULT_MODEL,
    DEFAULT_PROMPT: DEFAULT_PROMPT,
    SLOT_WORD: SLOT_WORD,
    SLOT_MEANING: SLOT_MEANING,
    fillPrompt: fillPrompt,
    buildPrompt: buildPrompt,
    DEFAULT_THINK_PROMPT: DEFAULT_THINK_PROMPT,
    DEFAULT_THINK_MODEL: DEFAULT_THINK_MODEL,
    buildThinkPrompt: buildThinkPrompt,
    think: think,
    draw: draw,
    generateSmart: generateSmart,
    copyPrompt: copyPrompt,
    pickFromDevice: pickFromDevice,
    shrink: shrink,
    hasKey: hasKey,
    generate: generate,
    checkAccess: checkAccess
  };
})();
