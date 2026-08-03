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
  permissions: ['clipboard-read', 'clipboard-write']
});
const page = await context.newPage();

const problems = [];
page.on('pageerror', (e) => problems.push('Ошибка страницы: ' + e.message));
const check = (condition, message) => { if (!condition) problems.push(message); };

/* Перехватываем обращение к Gemini и смотрим, что именно уходит. */
let sentBody = null;
let sentHeaders = null;
await page.route('**generativelanguage.googleapis.com/**', async (route) => {
  sentBody = JSON.parse(route.request().postData() || '{}');
  sentHeaders = route.request().headers();
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      candidates: [{ content: { parts: [{ inlineData: { mimeType: 'image/png', data: REDDOT } }] } }]
    })
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
const prompt = sentBody && sentBody.contents[0].parts[0].text;
check(/schlafen/.test(prompt), 'В запросе нет самого слова');
check(/may not appear: any readable word/i.test(prompt), 'В запросе нет запрета на слова');
check(/leave its\s+writing areas blank/i.test(prompt), 'В запросе нет правила о календарях и вывесках');
check(/what is allowed: digits, arrows/i.test(prompt), 'В запросе не разрешены цифры и знаки');
for (const kind of ['adjective', 'adverb', 'idiom', 'separable or phrasal verb', 'preposition']) {
  check(new RegExp(kind, 'i').test(prompt), `В запросе нет разбора для «${kind}»`);
}
check(sentBody.generationConfig.responseModalities[0] === 'IMAGE', 'Не запрошена картинка');
check(sentBody.generationConfig.imageConfig.aspectRatio === '1:1', 'Не запрошен квадрат');

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
check(/may not appear: any readable word/i.test(copied), 'В скопированном промпте нет запрета на слова');
check(/adjective/i.test(copied) && /idiom/i.test(copied),
  'В скопированном промпте нет разбора частей речи');
check(copied.length > 1500, `Промпт подозрительно короткий: ${copied.length}`);

/* 3c. Готовая картинка возвращается обратно вставкой */
await page.click('[data-photo-paste]');
await page.waitForTimeout(600);
check(await page.locator('#paste-zone').count() === 1, 'Нет поля для вставки картинки');

await page.evaluate(async (base64) => {
  const blob = await (await fetch('data:image/png;base64,' + base64)).blob();
  const transfer = new DataTransfer();
  transfer.items.add(new File([blob], 'drawn.png', { type: 'image/png' }));
  document.getElementById('paste-zone').dispatchEvent(
    new ClipboardEvent('paste', { clipboardData: transfer, bubbles: true, cancelable: true })
  );
}, REDDOT);
await page.waitForTimeout(900);

check(await page.locator('#paste-zone').count() === 0, 'Окно вставки не закрылось');
check(await page.locator('#photo-row .picture:not(.picture--empty)').count() === 1,
  'Вставленная картинка не показана в форме');

await page.click('[data-add-word]');
await page.waitForTimeout(700);
const pasted = await page.evaluate(() => {
  const words = window.Store.getState().blocks[0].sets[0].words;
  return !!words[words.length - 1].image;
});
check(pasted, 'Слово со вставленной картинкой не сохранилось');

/* 4. Картинку видно на карточке знакомства */
await page.click('[data-start-learn]');
await page.waitForTimeout(500);
check(await page.locator('.picture--card').count() === 1, 'На карточке знакомства нет картинки');
await page.screenshot({ path: join(outDir, 'learn-card.png') });

/* 5. Картинку можно убрать, и она исчезает из базы */
await page.goBack();
await page.waitForTimeout(400);
await page.click('.ledger__row [data-edit-word]');
await page.waitForTimeout(400);
check(await page.locator('.modal .picture--slot').count() === 1, 'В правке слова нет картинки');
check(await page.locator('[data-edit-photo-prompt]').count() === 1, 'В правке слова нет кнопки промпта');
check(await page.locator('[data-edit-photo-paste]').count() === 1, 'В правке слова нет кнопки вставки');
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

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log('Картинки проверены: снимок, рисунок ИИ, копирование промпта, вставка готовой картинки, показ, удаление, сохранность.');
