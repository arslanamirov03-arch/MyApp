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
     и проверка теряет смысл. */
  function buildPrompt(word, translation) {
    return 'Illustrate the meaning of a single vocabulary word for a language learner.\n\n' +
      'Word: "' + word + '"\n' +
      (translation ? 'Meaning: ' + translation + '\n' : '') +
      '\nShow only what the word means, through one clear visual idea. ' +
      'If it is an action, show a person performing it. ' +
      'If it is an object, show that object alone. ' +
      'If it is abstract, use one simple, unmistakable visual metaphor. ' +
      'The picture must be specific enough that a learner can guess this exact word from it.\n\n' +
      'Style: clean flat vector illustration, simple shapes, soft warm palette, ' +
      'plain light background, one centred subject, generous empty space, no clutter. ' +
      'The illustration fills the whole frame edge to edge, without borders or framing.\n\n' +
      'CRITICAL: the image must contain NO text whatsoever — no letters, no words, ' +
      'no numbers, no captions, no labels, no signs, no logos, no watermarks, ' +
      'no writing of any kind, in any language or alphabet. Nothing written anywhere ' +
      'in the picture. The meaning must come across through the drawing alone, ' +
      'with no written hints.';
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
    pickFromDevice: pickFromDevice,
    shrink: shrink,
    hasKey: hasKey,
    generate: generate,
    checkAccess: checkAccess
  };
})();
