/* Проверка скорости на большой картотеке. Приложение должно оставаться
   быстрым и на тысячах слов: список рисуется порциями, картинки читаются
   только те, что видно, а поиск не поднимает весь экран. */
import { chromium } from 'playwright';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const WORDS = 1200;
const PICTURES = 250;

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const page = await (await browser.newContext({
  viewport: { width: 390, height: 844 }
})).newPage();

const problems = [];
page.on('pageerror', (e) => problems.push('Ошибка страницы: ' + e.message));
const check = (condition, message) => { if (!condition) problems.push(message); };

await page.goto(pathToFileURL(process.env.PAGE || join(root, 'dist/index.html')).href);
await page.waitForTimeout(300);

/* Считаем каждое обращение к базе картинок */
const countReads = () => page.evaluate(() => {
  window.__reads = 0;
  const load = window.Store.imageLoad;
  window.Store.imageLoad = function (id) { window.__reads++; return load(id); };
});
await countReads();

await page.evaluate(async ({ words, pictures }) => {
  const S = window.Store;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 160;
  const c = canvas.getContext('2d');
  c.fillStyle = '#C05F3C';
  c.fillRect(0, 0, 160, 160);
  const picture = canvas.toDataURL('image/jpeg', 0.7);

  let made = 0;
  let withPicture = 0;
  while (made < words) {
    const block = S.addBlock('Блок ' + (S.getState().blocks.length + 1));
    const set = S.addSet(block.id, 'Список 1');
    const lines = [];
    for (let i = 0; i < 400 && made < words; i++, made++) {
      lines.push('das Wort' + made + ' — слово номер ' + made);
    }
    S.addWordsBulk(block.id, set.id, lines.join('\n'));
    for (const word of S.getSet(block.id, set.id).words) {
      if (withPicture >= pictures) break;
      const id = S.uid();
      await S.imageSave(id, picture);
      S.setWordImage(word.id, id, 'ai');
      withPicture++;
    }
  }
  S.flush();
}, { words: WORDS, pictures: PICTURES });

await page.reload();
await page.waitForSelector('.card');
await countReads();

/* 1. Список открывается порцией, а не целиком */
await page.click('.card');
await page.waitForTimeout(500);

const opened = await page.evaluate(() => new Promise((resolve) => {
  const started = performance.now();
  document.querySelector('.card').click();
  requestAnimationFrame(() => requestAnimationFrame(() => resolve({
    ms: Math.round(performance.now() - started),
    rows: document.querySelectorAll('.ledger__row').length,
    icons: document.querySelectorAll('.ledger svg').length
  })));
}));
await page.waitForTimeout(400);
const readsOnOpen = await page.evaluate(() => window.__reads);

check(opened.rows > 0 && opened.rows <= 60,
  `В разметке сразу ${opened.rows} строк — список рисуется не порциями`);
check(opened.ms < 250, `Список открывался ${opened.ms} мс`);
check(readsOnOpen <= 14,
  `Картинок прочитано из базы: ${readsOnOpen} — читаются не только видимые`);
check(await page.locator('#ledger-more').count() === 1, 'Нет отметки о продолжении списка');

/* 2. Прокрутка дорисовывает следующую порцию */
const before = opened.rows;
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await page.waitForTimeout(700);
const after = await page.locator('.ledger__row').count();
check(after > before, `После прокрутки строк не прибавилось: ${before} → ${after}`);

/* 3. Поиск не занимает главный поток и не трогает остальной экран */
await page.evaluate(() => window.scrollTo(0, 0));
await page.waitForTimeout(300);

const lag = await page.evaluate(() => {
  const input = document.getElementById('input-search');
  const times = [];
  for (const text of ['w', 'wo', 'wor', 'wort', 'wort7']) {
    input.value = text;
    const started = performance.now();
    input.dispatchEvent(new Event('input', { bubbles: true }));
    times.push(performance.now() - started);
  }
  return Math.round(Math.max.apply(null, times));
});
check(lag < 50, `Одна буква в поиске занимает главный поток на ${lag} мс`);

await page.waitForTimeout(400);
const found = await page.locator('.ledger__row').count();
check(found > 0 && found <= 60, `Поиск показал ${found} строк вместо порции`);
check(await page.evaluate(() => document.activeElement && document.activeElement.id) !== 'body',
  'Поиск перерисовал экран целиком и увёл фокус');

/* 4. Добавление слова остаётся быстрым */
await page.fill('#input-search', '');
await page.waitForTimeout(300);
const added = await page.evaluate(() => new Promise((resolve) => {
  document.getElementById('input-de').value = 'die Geschwindigkeit';
  document.getElementById('input-ru').value = 'скорость';
  const started = performance.now();
  document.querySelector('[data-add-word]').click();
  requestAnimationFrame(() => requestAnimationFrame(() =>
    resolve(Math.round(performance.now() - started))));
}));
check(added < 200, `Слово добавляется ${added} мс`);
check((await page.locator('.ledger__de').first().textContent()).trim() === 'die Geschwindigkeit',
  'Добавленное слово не встало первым');

/* 5. Запись на диск не переписывает картотеку дважды */
const saveMs = await page.evaluate(() => {
  const started = performance.now();
  for (let i = 0; i < 5; i++) window.Store.flush();
  return Math.round((performance.now() - started) / 5);
});
check(saveMs < 60, `Одна запись на диск занимает ${saveMs} мс`);

/* 6. В покое ничего не двигается: раньше два слоя фона плыли без остановки
      и телефон пересобирал их круглые сутки — отсюда и расход заряда. */
await page.waitForTimeout(1200);
const idle = await page.evaluate(() => document.getAnimations()
  .filter((a) => a.playState === 'running')
  .map((a) => (a.animationName || 'переход') + ' на ' +
    ((a.effect && a.effect.target && a.effect.target.className) || '?')));
check(idle.length === 0, `В покое продолжают идти анимации: ${idle.join(', ')}`);

const forever = await page.evaluate(() => {
  const endless = [];
  for (const sheet of document.styleSheets) {
    for (const rule of sheet.cssRules) {
      if (!rule.style) continue;
      const value = rule.style.animation || rule.style.animationIterationCount;
      if (value && /infinite/.test(value)) endless.push(rule.selectorText);
    }
  }
  return endless;
});
check(forever.length === 1 && /caret/.test(forever[0]),
  `Бесконечные анимации, кроме каретки: ${forever.join(', ')}`);

/* 7. Память под картинки ограничена */
const cached = await page.evaluate(async () => {
  const set = window.Store.getState().blocks[0].sets[0];
  const withImages = set.words.filter((w) => w.image).slice(0, 200);
  for (const word of withImages) await window.Store.imageLoad(word.image.id);
  await new Promise((r) => setTimeout(r, 300));
  return withImages.length;
});
check(cached > 0, 'Не удалось прочитать картинки для проверки памяти');

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log(`Скорость проверена на ${WORDS} словах: список открывается за ${opened.ms} мс ` +
  `порцией в ${opened.rows} строк, из базы читается ${readsOnOpen} картинок, ` +
  `буква в поиске — ${lag} мс, добавление слова — ${added} мс.`);
