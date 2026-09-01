/* Проверка картинок: снимок с устройства и рисунок от ИИ.
   Ответ Gemini подменяется, чтобы проверка не тратила запросы и
   не зависела от сети. */
import { chromium } from 'playwright';
import { mkdirSync, existsSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || join(root, 'dist/e2e-images');
mkdirSync(outDir, { recursive: true });

/* Крошечная картинка-заглушка: сплошной красный квадрат 2×2. */
const REDDOT = 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEklEQVR4nGP8z4AATAxQxlAWAgCQrgEjvOTsBwAAAABJRU5ErkJggg==';
const photoPath = join(outDir, 'photo.png');
writeFileSync(photoPath, Buffer.from(REDDOT, 'base64'));

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  permissions: ['clipboard-read', 'clipboard-write'],
  acceptDownloads: true
});
const page = await context.newPage();

const problems = [];
page.on('pageerror', (e) => problems.push('Ошибка страницы: ' + e.message));
const check = (condition, message) => { if (!condition) problems.push(message); };

/* Перехватываем обращение к Gemini и смотрим, что именно уходит. */
/* Рисующая и текстовая модели отвечают по-разному, поэтому и подмена
   у них разная: одной — картинка, другой — сочинённый промпт. */
const THOUGHT = 'A calm scene: one person quietly letting another pass ahead. ' +
  'Flat vector illustration, warm palette, square 1:1.';

let sentBody = null;
let sentHeaders = null;
let sentUrl = '';
let thinkBody = null;
let thinkUrl = '';
let drawCalls = 0;

await page.route('**generativelanguage.googleapis.com/**', async (route) => {
  const request = route.request();
  const url = request.url();
  sentHeaders = request.headers();

  if (request.method() !== 'POST') {
    await route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ models: [{ name: 'models/gemini-3.1-flash-image' }] })
    });
    return;
  }

  const body = JSON.parse(request.postData() || '{}');

  if (/-image[^/]*:generateContent/.test(url)) {
    sentBody = body;
    sentUrl = url;
    drawCalls++;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        candidates: [{ content: { parts: [{ inlineData: { mimeType: 'image/png', data: REDDOT } }] } }]
      })
    });
    return;
  }

  thinkBody = body;
  thinkUrl = url;
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ candidates: [{ content: { parts: [{ text: THOUGHT }] } }] })
  });
});

await page.goto(pathToFileURL(join(root, 'dist/index.html')).href);
await page.waitForTimeout(300);

await page.click('#fab');
await page.fill('#f-title', 'Блок 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(250);
check((await page.locator('#f-title').count()) === 0, 'Окно не закрылось');

await page.click('#fab');
await page.fill('#f-title', 'Список 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(250);

/* 1. Снимок с устройства прикрепляется к новому слову */
await page.fill('#input-de', 'gehen');
await page.fill('#input-ru', 'идти');

const [chooser] = await Promise.all([
  page.waitForEvent('filechooser'),
  page.click('[data-photo-pick]')
]);
await chooser.setFiles(photoPath);
await page.waitForTimeout(500);
check(await page.locator('#photo-row .picture:not(.picture--empty)').count() === 1,
  'Выбранный снимок не показан в форме');

/* Кнопки картинок — значки без подписей, но с понятным названием */
const symbols = await page.evaluate(() => [...document.querySelectorAll('#photo-row .sym-btn')]
  .map((el) => ({
    icon: el.textContent.trim(),
    title: el.getAttribute('title') || '',
    width: el.getBoundingClientRect().width
  })));
check(symbols.length === 5, `Значков в полосе картинок: ${symbols.length}, ожидалось 5`);
check(symbols.every((s) => [...s.icon].length <= 2 && s.icon.length > 0),
  'На кнопке картинок осталась подпись словами: ' + symbols.map((s) => s.icon).join(' '));
check(symbols.every((s) => s.title.length > 3), 'У значка нет пояснения при наведении');
check(symbols.every((s) => s.width <= 56), 'Значок занимает слишком много места');
check(await page.locator('#photo-row').evaluate((el) => el.getBoundingClientRect().height) < 130,
  'Полоса кнопок занимает слишком много места');

/* Ещё не привязанная картинка тоже открывается во весь экран */
await page.click('#photo-row .picture');
await page.waitForTimeout(500);
check(await page.locator('#viewer').count() === 1, 'Картинка в форме не открылась во весь экран');
await page.keyboard.press('Escape');
await page.waitForTimeout(600);

await page.click('[data-add-word]');
await page.waitForTimeout(600);

const withPhoto = await page.evaluate(() => {
  const w = window.Store.getState().blocks[0].sets[0].words[0];
  return { hasImage: !!w.image, kind: w.image && w.image.kind };
});
check(withPhoto.hasImage, 'Картинка не привязалась к слову');
check(await page.locator('.ledger .picture--thumb').count() === 1, 'В списке нет миниатюры');

/* 2. Без ключа генерация не идёт, а зовёт настройки */
await page.fill('#input-de', 'schlafen');
await page.fill('#input-ru', 'спать');
await page.click('[data-photo-ai]');
await page.waitForTimeout(400);
check(await page.locator('.modal__title').count() === 1, 'Без ключа не предложены настройки');
check((await page.locator('.modal__title').textContent()).includes('Картинки от ИИ'), 'Открылось не то окно');

await page.fill('#f-key', 'test-key-123');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(300);

/* 3. С ключом рисунок запрашивается и прикрепляется */
await page.fill('#input-de', 'schlafen');
await page.fill('#input-ru', 'спать');
await page.click('[data-photo-ai]');
await page.waitForTimeout(1200);

check(sentHeaders && sentHeaders['x-goog-api-key'] === 'test-key-123', 'Ключ не ушёл в заголовке');
check(/gemini-3\.1-flash-lite-image:generateContent/.test(sentUrl),
  'Запрос ушёл не к модели по умолчанию: ' + sentUrl);
const prompt = sentBody && sentBody.contents[0].parts[0].text;
check(/schlafen/.test(prompt), 'В запросе нет самого слова');
check(/must not show the word "schlafen"/i.test(prompt), 'В запросе не запрещено само слово');
check(/or its translation "спать"/i.test(prompt), 'В запросе не запрещён перевод');
check(/simplicity is the rule/i.test(prompt), 'В запросе нет требования простоты');
check(/no arrows, no motion lines/i.test(prompt), 'В запросе не отключены стрелки и линии движения');
check(/work out for yourself what kind of word/i.test(prompt),
  'В запросе нет самостоятельного разбора части речи');
for (const kind of ['a thing', 'an action', 'a quality', 'a manner', 'an idiom', 'a position word']) {
  check(new RegExp(kind, 'i').test(prompt), `В запросе нет строки для «${kind}»`);
}
check(sentBody.generationConfig.responseModalities[0] === 'IMAGE', 'Не запрошена картинка');
check(sentBody.generationConfig.imageConfig.aspectRatio === '1:1', 'Не запрошен квадрат');

/* 3a. Умный режим: сперва текстовая модель пишет промпт, потом по нему
       рисует Nano Banana. Обычный промпт при этом не участвует. */
const drawsBefore = drawCalls;
await page.click('[data-photo-smart]');
await page.waitForTimeout(1500);

check(thinkUrl.includes('gemini-3.6-flash:generateContent'),
  'Разбор слова ушёл не к текстовой модели: ' + thinkUrl);
check(!/responseModalities/.test(JSON.stringify(thinkBody || {})),
  'У текстовой модели запрошена картинка');
const asked = thinkBody && thinkBody.contents[0].parts[0].text;
check(/schlafen/.test(asked) && /спать/.test(asked), 'В разбор не попали слово и перевод');
check(/Think first/i.test(asked), 'Текстовую модель не просят разобрать слово');
check(/no explanation, no quotes/i.test(asked), 'Модель не просят ответить одним промптом');

check(drawCalls === drawsBefore + 1, 'Рисующая модель вызвана не один раз');
const drawnBy = sentBody && sentBody.contents[0].parts[0].text;
check(drawnBy.indexOf(THOUGHT) === 0, 'Рисуют не по сочинённому промпту: ' + drawnBy.slice(0, 60));
check(/must not show the word "schlafen"/i.test(drawnBy),
  'В сочинённый промпт не дописан запрет на надписи');
check(await page.locator('#photo-row .picture:not(.picture--empty)').count() === 1,
  'Умный режим не принёс картинку');

await page.click('[data-add-word]');
await page.waitForTimeout(700);

const aiWord = await page.evaluate(() => {
  const words = window.Store.getState().blocks[0].sets[0].words;
  return { count: words.length, hasImage: !!words[1].image };
});
check(aiWord.count === 2 && aiWord.hasImage, 'Слово с рисунком ИИ не сохранилось');
await page.screenshot({ path: join(outDir, 'with-pictures.png'), fullPage: true });

/* 3b. Промпт копируется в буфер — им можно нарисовать картинку где угодно */
await page.fill('#input-de', 'die Geduld');
await page.fill('#input-ru', 'терпение');
await page.click('[data-photo-prompt]');
await page.waitForTimeout(500);

const copied = await page.evaluate(() => navigator.clipboard.readText());
check(/die Geduld/.test(copied), 'В скопированном промпте нет слова');
check(/терпение/.test(copied), 'В скопированном промпте нет перевода');
check(/must not show the word "die Geduld"/i.test(copied),
  'В скопированном промпте не запрещено само слово');
check(/simplicity is the rule/i.test(copied), 'В скопированном промпте нет требования простоты');
check(copied.length > 1200, `Промпт подозрительно короткий: ${copied.length}`);

/* Отдельной кнопки вставки нет: нарисованную по этому промпту картинку
   сохраняют в галерею и берут сюда тем же снимком. */
check(await page.locator('[data-photo-paste]').count() === 0, 'Кнопка вставки осталась');

const [drawnChooser] = await Promise.all([
  page.waitForEvent('filechooser'),
  page.click('[data-photo-pick]')
]);
await drawnChooser.setFiles(photoPath);
await page.waitForTimeout(500);
check(await page.locator('#photo-row .picture:not(.picture--empty)').count() === 1,
  'Нарисованная где-то картинка не взялась снимком');

await page.click('[data-add-word]');
await page.waitForTimeout(700);
const brought = await page.evaluate(() => {
  const words = window.Store.getState().blocks[0].sets[0].words;
  return { count: words.length, hasImage: !!words[words.length - 1].image };
});
check(brought.count === 3 && brought.hasImage, 'Слово с принесённой картинкой не сохранилось');

/* 4. Картинку видно на карточке знакомства */
await page.click('[data-start-learn]');
await page.waitForTimeout(500);
check(await page.locator('.picture--card').count() === 1, 'На карточке знакомства нет картинки');
await page.screenshot({ path: join(outDir, 'learn-card.png') });

/* Картинка тянется своим ползунком и на самом крупном размере
   остаётся в пределах экрана. */
await page.click('[data-drill-size]');
await page.waitForTimeout(400);
const cardWidth = () => page.evaluate(() =>
  document.querySelector('.picture--card').getBoundingClientRect().width);

const before = await cardWidth();
await page.evaluate(() => {
  const slider = document.querySelector('[data-size="image"]');
  slider.value = '240';
  slider.dispatchEvent(new Event('input', { bubbles: true }));
});
await page.waitForTimeout(250);
const after = await cardWidth();
check(after > before * 1.3, `Картинка не выросла: ${before} → ${after}`);
check(after <= 390, `Картинка вылезла за экран: ${after}`);

const promptFont = await page.evaluate(() =>
  parseFloat(getComputedStyle(document.querySelector('.drill__prompt')).fontSize));
check(promptFont < 40, `Текст вырос вместе с картинкой: ${promptFont}`);

await page.evaluate(() => {
  const slider = document.querySelector('[data-size="image"]');
  slider.value = '100';
  slider.dispatchEvent(new Event('input', { bubbles: true }));
});
await page.click('[data-drill-size]');
await page.waitForTimeout(300);

/* 5. Картинку можно убрать, и она исчезает из базы */
await page.goBack();
await page.waitForTimeout(400);

/* Список идёт от нового к старому, поэтому самое первое слово — внизу */
check((await page.locator('.ledger__de').last().textContent()).trim() === 'gehen',
  'Внизу списка не самое первое слово');
await page.click('.ledger__row:last-child [data-edit-word]');
await page.waitForTimeout(400);
check(await page.locator('.modal .picture--slot').count() === 1, 'В правке слова нет картинки');
check(await page.locator('[data-edit-photo-prompt]').count() === 1, 'В правке слова нет кнопки промпта');
check(await page.locator('[data-edit-photo-smart]').count() === 1, 'В правке слова нет умного промпта');

/* И в окне правки картинка открывается касанием */
await page.click('.modal .picture--slot');
await page.waitForTimeout(500);
check(await page.locator('#viewer').count() === 1, 'Картинка в правке слова не открылась во весь экран');
check(await page.locator('.modal').count() === 1, 'Окно правки закрылось вместе с картинкой');
await page.keyboard.press('Escape');
await page.waitForTimeout(600);

await page.click('[data-edit-photo-drop]');
await page.waitForTimeout(500);

const dropped = await page.evaluate(() => !window.Store.getState().blocks[0].sets[0].words[0].image);
check(dropped, 'Картинка не убралась');

/* 6. Картинки переживают перезагрузку */
await page.reload();
await page.waitForTimeout(600);
await page.click('.card');
await page.waitForTimeout(300);
await page.click('.card');
await page.waitForTimeout(600);
const thumbs = await page.locator('.ledger .picture--thumb').count();
check(thumbs === 2, `После перезагрузки миниатюр: ${thumbs}, ожидалось 2`);

/* 7. Картинка открывается во весь экран: без кнопок, с увеличением,
      закрывается движением вниз, сохраняется долгим нажатием. */
await page.click('.ledger .picture--thumb');
await page.waitForTimeout(500);
check(await page.locator('#viewer').count() === 1, 'Картинка не открылась во весь экран');
check(await page.locator('#viewer .viewer__img').count() === 1, 'В окне нет самой картинки');
check(await page.locator('#viewer button').count() === 0, 'В окне картинки появились кнопки');

const covers = await page.evaluate(() => {
  const box = document.getElementById('viewer').getBoundingClientRect();
  return box.width >= innerWidth - 1 && box.height >= innerHeight - 1;
});
check(covers, 'Окно картинки занимает не весь экран');

/* Увеличение */
const zoomed = await page.evaluate(() => {
  const node = document.getElementById('viewer');
  for (let i = 0; i < 4; i++) {
    node.dispatchEvent(new WheelEvent('wheel', { deltaY: -240, bubbles: true, cancelable: true }));
  }
  const matrix = new DOMMatrixReadOnly(getComputedStyle(document.getElementById('viewer-img')).transform);
  return matrix.a;
});
check(zoomed > 1.2, `Картинка не увеличилась: ${zoomed}`);
await page.screenshot({ path: join(outDir, 'viewer.png') });

await page.keyboard.press('Escape');
await page.waitForTimeout(600);
check(await page.locator('#viewer').count() === 0, 'Картинка не закрылась');

/* Системная «назад» закрывает картинку и оставляет на том же экране */
await page.click('.ledger .picture--thumb');
await page.waitForTimeout(450);
await page.goBack();
await page.waitForTimeout(600);
check(await page.locator('#viewer').count() === 0, 'Кнопка «назад» не закрыла картинку');
check(await page.locator('.ledger').count() === 1, '«Назад» из картинки увела с экрана');

/* Сохранение долгим нажатием — кнопки для этого нет */
await page.click('.ledger .picture--thumb');
await page.waitForTimeout(450);
const downloading = page.waitForEvent('download', { timeout: 8000 });
await page.mouse.move(195, 420);
await page.mouse.down();
await page.waitForTimeout(900);
await page.mouse.up();
const saved = await downloading;
check(/\.(jpg|png)$/.test(saved.suggestedFilename()),
  'Картинка сохранилась под непонятным именем: ' + saved.suggestedFilename());

/* Закрытие движением вниз */
await page.mouse.move(195, 380);
await page.mouse.down();
await page.mouse.move(195, 460, { steps: 4 });
await page.mouse.move(195, 620, { steps: 6 });
await page.mouse.up();
await page.waitForTimeout(700);
check(await page.locator('#viewer').count() === 0, 'Картинка не закрылась движением вниз');

/* 8. Модель рисования выбирается в настройках */
await page.click('#btn-archive');
await page.waitForTimeout(300);
await page.click('[data-open-settings]');
await page.waitForTimeout(300);

const models = await page.locator('[data-models="image"] .model__title').allTextContents();
check(models.join(' · ') === 'Nano Banana 2 · Nano Banana 2 Lite · Nano Banana Pro',
  'В настройках не те модели: ' + models.join(' · '));

await page.locator('[data-models="image"] .model').nth(0).click();
await page.waitForTimeout(200);
check(await page.evaluate(() => window.Store.getModel()) === 'gemini-3.1-flash-image',
  'Выбранная модель не запомнилась');
check(await page.locator('[data-models="image"] .model--on').count() === 1,
  'В рисующих отмечена не одна модель');

/* Своя модель добавляется кнопкой «+» и так же убирается */
await page.click('[data-model-add="image"]');
await page.fill('[data-model-field="image"]', 'gemini-own-image');
await page.click('[data-model-save="image"]');
await page.waitForTimeout(300);
check(await page.locator('[data-models="image"] .model').count() === 4, 'Своя модель не добавилась');
check(await page.evaluate(() => window.Store.getModel()) === 'gemini-own-image',
  'Добавленная модель не выбралась');

await page.click('[data-model-remove="gemini-own-image"]');
await page.waitForTimeout(300);
check(await page.locator('[data-models="image"] .model').count() === 3, 'Своя модель не убралась');

await page.locator('[data-models="image"] .model').nth(2).click();
await page.waitForTimeout(200);

/* 8b. Модель, пишущая промпт, выбирается отдельно */
const thinkModels = await page.locator('[data-models="think"] .model__title').allTextContents();
check(thinkModels.join(' · ') === 'Gemini 3.6 Flash · Gemini 3.5 Flash · Gemini 3.5 Flash Lite',
  'Не те модели для умного промпта: ' + thinkModels.join(' · '));
check(!thinkModels.some((t) => /pro/i.test(t)), 'В умный режим попала тяжёлая Pro');

await page.locator('[data-models="think"] .model').nth(2).click();
await page.waitForTimeout(200);
check(await page.evaluate(() => window.Store.getThinkModel()) === 'gemini-3.5-flash-lite',
  'Текстовая модель не выбралась');
const bothModels = await page.evaluate(() => ({
  image: window.Store.getModel(), think: window.Store.getThinkModel()
}));
check(bothModels.image !== bothModels.think, 'Выбор текстовой модели задел рисующую');

await page.click('[data-model-add="think"]');
await page.fill('[data-model-field="think"]', 'my-text-model');
await page.click('[data-model-save="think"]');
await page.waitForTimeout(300);
check(await page.locator('[data-models="think"] .model').count() === 4,
  'Своя текстовая модель не добавилась');
check(await page.locator('[data-models="image"] .model').count() === 3,
  'Своя текстовая модель попала в список рисующих');
await page.click('[data-model-remove="my-text-model"]');
await page.waitForTimeout(300);
await page.locator('[data-models="think"] .model').nth(0).click();
await page.waitForTimeout(200);

/* 8c. Памятка по значкам — в свёрнутом разделе */
check(!(await page.locator('.legend__row').first().isVisible()),
  'Памятка развёрнута и растягивает окно');
await page.click('.fold__head:has-text("Что означают значки")');
await page.waitForTimeout(300);
const legend = await page.locator('.legend__row').count();
check(legend === 5, `В памятке значков: ${legend}, ожидалось 5`);
check(await page.locator('.legend__row').first().isVisible(), 'Памятка не раскрылась');
const legendText = await page.locator('.legend').textContent();
check(/Умный промпт/.test(legendText), 'В памятке нет умного промпта');

/* 9. Промпт правится прямо в настройках */
await page.click('.fold__head:has-text("Промпт для картинок")');
await page.waitForTimeout(300);
const shownPrompt = await page.inputValue('#f-prompt');
check(/SIMPLICITY IS THE RULE/.test(shownPrompt), 'В настройках не показан промпт');
check(shownPrompt.includes('{слово}') && shownPrompt.includes('{перевод}'),
  'В промпте нет подстановок для слова и перевода');

await page.fill('#f-prompt', 'Нарисуй «{слово}» — это {перевод}. Никаких надписей.');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(400);
await page.screenshot({ path: join(outDir, 'settings.png'), fullPage: true });

await page.fill('#input-de', 'der Löffel');
await page.fill('#input-ru', 'ложка');
await page.click('[data-photo-ai]');
await page.waitForTimeout(1200);

check(/gemini-3-pro-image:generateContent/.test(sentUrl),
  'Запрос ушёл не к выбранной модели: ' + sentUrl);
check(sentBody.contents[0].parts[0].text === 'Нарисуй «der Löffel» — это ложка. Никаких надписей.',
  'Свой промпт не подставился: ' + sentBody.contents[0].parts[0].text);

/* Кнопка «Промпт» кладёт в буфер тот же самый текст */
await page.click('[data-photo-prompt]');
await page.waitForTimeout(400);
check(await page.evaluate(() => navigator.clipboard.readText()) ===
  'Нарисуй «der Löffel» — это ложка. Никаких надписей.', 'В буфер ушёл не свой промпт');

/* Возврат к стандартному промпту */
await page.click('#btn-archive');
await page.waitForTimeout(300);
await page.click('[data-open-settings]');
await page.waitForTimeout(300);
await page.click('.fold__head:has-text("Промпт для картинок")');
await page.click('.fold__head:has-text("Промпт для умного режима")');
await page.waitForTimeout(300);
await page.click('[data-prompt-reset]');
await page.waitForTimeout(200);
check(/SIMPLICITY IS THE RULE/.test(await page.inputValue('#f-prompt')),
  'Стандартный промпт не вернулся в поле');
check(/Think first/.test(await page.inputValue('#f-think-prompt')),
  'Промпта умного режима нет в настройках');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(300);
check(await page.evaluate(() => window.Store.getPromptTemplate()) === '',
  'Стандартный промпт запомнился как свой');
const backToDefault = await page.evaluate(() => window.Media.buildPrompt('gehen', 'идти'));
check(/SIMPLICITY IS THE RULE/.test(backToDefault) && /must not show the word "gehen"/.test(backToDefault),
  'После сброса промпт собрался неверно');

/* Настройки переживают перезагрузку */
await page.reload();
await page.waitForTimeout(600);
const kept = await page.evaluate(() => ({
  model: window.Store.getModel(),
  prompt: window.Store.getPromptTemplate()
}));
check(kept.model === 'gemini-3-pro-image' && kept.prompt === '',
  'Настройки не пережили перезагрузку: ' + JSON.stringify(kept));

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log('Картинки проверены: снимок, рисунок ИИ, умный промпт через текстовую модель, ' +
  'кнопки-значки, копирование промпта и возврат картинки снимком, показ во весь экран ' +
  'с увеличением и сохранением, выбор обеих моделей, свои промпты, удаление, сохранность.');
