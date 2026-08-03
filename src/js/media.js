/* Картинки к словам: снимок с устройства и рисунок, сделанный Gemini.
   Картинка запоминается вместе со словом — по переводу и образу слово
   вспоминается заметно легче, чем по одному переводу. */
window.Media = (function () {
  'use strict';

  var ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models/';
  var DEFAULT_MODEL = 'gemini-3.1-flash-lite-image';
  var MAX_SIDE = 640;
  var QUALITY = 0.82;

  /* Промпт делает две вещи. Во-первых, разбирает, какая это часть речи:
     существительное рисуется иначе, чем наречие или идиома, и без этого
     разбора модель сводит всё к предмету на столе. Во-вторых, оставляет
     в кадре цифры, стрелки и отдельные знаки — они помогают понять смысл, —
     но не пускает туда ни одного читаемого слова: слово в кадре означало бы
     готовый ответ. */
  function buildPrompt(word, translation) {
    return 'Create one picture that makes the meaning of a foreign word stick ' +
      'in a learner\'s memory.\n\n' +
      'WORD: "' + word + '"\n' +
      (translation ? 'MEANING: ' + translation + '\n' : '') +
      '\nFirst decide what kind of word this is, then show it accordingly:\n' +
      '- concrete noun: the object itself, large and clear, in a setting that hints ' +
      'at how it is used\n' +
      '- verb: a person caught mid-action, with motion lines or an arrow that carries ' +
      'the movement\n' +
      '- separable or phrasal verb: the whole action the phrase really describes, ' +
      'not its parts shown separately\n' +
      '- adjective: make the quality unmistakable through contrast — put the thing beside ' +
      'its opposite, or push the quality far past normal\n' +
      '- adverb: keep the action ordinary and make the MANNER obvious — speed lines, ' +
      'repetition, a clock, a face\n' +
      '- emotion or abstract idea: body language and facial expression carrying the feeling, ' +
      'plus one simple metaphor\n' +
      '- idiom or set expression: draw the situation the idiom actually means, and you may ' +
      'add a small nod to its literal image if that helps it stick\n' +
      '- number, quantity or time: show the exact amount by counting real objects\n' +
      '- preposition or position word: two simple objects whose relative position is the ' +
      'entire point, with an arrow if needed\n\n' +
      'Memory devices to use: one clear focal point, slight exaggeration, a readable facial ' +
      'expression, arrows or motion lines, a before-and-after pair when it clarifies, and one ' +
      'surprising detail that gives the mind a hook.\n\n' +
      'STYLE: clean flat vector illustration, simple shapes, warm palette of terracotta, ' +
      'sage green, sand and cream, plain light background, generous empty space, no frame ' +
      'or border. Square, 1:1.\n\n' +
      'WHAT MAY NOT APPEAR: any readable word, in any language or script. Not the word "' +
      word + '", not its translation, and not helper captions either — no "before", "after", ' +
      'month names, weekday names, brand names, titles, labels, signatures or watermarks. ' +
      'If you draw a calendar, clock face, book, screen, poster or shop sign, leave its ' +
      'writing areas blank or fill them with plain marks. When a caption feels necessary, ' +
      'replace it with a drawing.\n\n' +
      'WHAT IS ALLOWED: digits, arrows, motion lines, mathematical and musical signs, ' +
      'and single standalone letters — use them freely whenever they make the meaning ' +
      'clearer. The learner must be unable to simply read the answer.';
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

  function blobToDataUrl(blob) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result)); };
      reader.onerror = function () { reject(new Error('не удалось прочитать картинку')); };
      reader.readAsDataURL(blob);
    });
  }

  /* Картинка из буфера. Прямое чтение доступно не везде, поэтому
     запасным путём остаётся обычная вставка в поле. */
  function readClipboardImage() {
    if (!navigator.clipboard || !navigator.clipboard.read) {
      return Promise.reject(new Error('Буфер напрямую недоступен'));
    }
    return navigator.clipboard.read().then(function (items) {
      for (var i = 0; i < items.length; i++) {
        var types = items[i].types || [];
        for (var t = 0; t < types.length; t++) {
          if (types[t].indexOf('image/') === 0) {
            return items[i].getType(types[t]).then(blobToDataUrl).then(shrink);
          }
        }
      }
      throw new Error('В буфере нет картинки');
    });
  }

  /* Картинка из события вставки — работает даже там, где чтение буфера закрыто. */
  function imageFromPaste(event) {
    var data = event.clipboardData || window.clipboardData;
    if (!data) return null;

    var items = data.items || [];
    for (var i = 0; i < items.length; i++) {
      if (items[i].kind === 'file' && items[i].type.indexOf('image/') === 0) {
        var file = items[i].getAsFile();
        if (file) return blobToDataUrl(file).then(shrink);
      }
    }

    var files = data.files || [];
    if (files.length && files[0].type.indexOf('image/') === 0) {
      return blobToDataUrl(files[0]).then(shrink);
    }
    return null;
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

  /* Рисунок от Gemini. Ключ хранится на устройстве и в копию не попадает. */
  function generate(word, translation, model) {
    var key = window.Store.getApiKey();
    if (!key) return Promise.reject(new Error('Сначала укажите ключ Google AI в настройках'));

    var url = ENDPOINT + (model || DEFAULT_MODEL) + ':generateContent';
    var body = {
      contents: [{ parts: [{ text: buildPrompt(word, translation) }] }],
      generationConfig: {
        responseModalities: ['IMAGE'],
        imageConfig: { aspectRatio: '1:1' }
      }
    };

    return fetch(url, {
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
          throw new Error(message);
        }
        return data;
      });
    }).then(function (data) {
      var parts = (data.candidates && data.candidates[0] &&
        data.candidates[0].content && data.candidates[0].content.parts) || [];
      var picture = null;
      for (var i = 0; i < parts.length; i++) {
        var inline = parts[i].inlineData || parts[i].inline_data;
        if (inline && inline.data) {
          picture = 'data:' + (inline.mimeType || inline.mime_type || 'image/png') + ';base64,' + inline.data;
          break;
        }
      }
      if (!picture) throw new Error('Модель не вернула картинку — попробуйте ещё раз');
      return shrink(picture);
    }, function (error) {
      /* Страница без доступа в сеть (например, опубликованная копия
         с ограничениями) сообщает об этом невнятно — переводим. */
      if (error instanceof TypeError) {
        throw new Error('Нет доступа к серверу Google с этой страницы');
      }
      throw error;
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
    buildPrompt: buildPrompt,
    copyPrompt: copyPrompt,
    readClipboardImage: readClipboardImage,
    imageFromPaste: imageFromPaste,
    pickFromDevice: pickFromDevice,
    shrink: shrink,
    hasKey: hasKey,
    generate: generate,
    checkAccess: checkAccess
  };
})();
