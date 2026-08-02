/* Картинки к словам: снимок с устройства и рисунок, сделанный Gemini.
   Картинка запоминается вместе со словом — по переводу и образу слово
   вспоминается заметно легче, чем по одному переводу. */
window.Media = (function () {
  'use strict';

  var ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models/';
  var DEFAULT_MODEL = 'gemini-3.1-flash-lite-image';
  var MAX_SIDE = 640;
  var QUALITY = 0.82;

  /* Промпт держит две вещи: картинка должна объяснять смысл слова
     и не должна содержать ни единой буквы — иначе это подсказка,
     и проверка теряет смысл. Отдельно оговорены надписи на предметах:
     без этого модель охотно рисует текст на книгах, бумагах и экранах. */
  function buildPrompt(word, translation) {
    return 'Draw one clear picture that teaches the meaning of a single vocabulary word.\n\n' +
      'Word: "' + word + '"\n' +
      (translation ? 'Meaning: ' + translation + '\n' : '') +
      '\nShow exactly this meaning. If the word has several senses, take only the one ' +
      'that matches the meaning given above. If it is an action, show a person performing it, ' +
      'caught mid-movement. If it is a thing, show that one thing. If it is a feeling or an ' +
      'abstract idea, use one simple, unmistakable visual metaphor. Nothing else in the ' +
      'picture may suggest a different word.\n\n' +
      'Style: clean flat vector illustration, simple shapes, soft warm palette of terracotta, ' +
      'sage green, sand and cream, plain light background, one centred subject, generous ' +
      'empty space, no clutter, no border or frame. Square image, 1:1.\n\n' +
      'Hard rule — the picture must contain no writing of any kind: no letters, no words, ' +
      'no numbers, no captions, no labels, no signs, no logos, no watermarks, no text on ' +
      'books, screens, packages or clothing, and no marks that merely look like writing, ' +
      'in any language or alphabet. A learner has to guess the word from the drawing alone, ' +
      'so any written hint ruins it.';
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
