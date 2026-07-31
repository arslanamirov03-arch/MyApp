/* Сквозная проверка в настоящем браузере: путь пользователя
   (блок → список → слова → выгрузка → разбор) со снимками экранов. */
import { chromium } from 'playwright';
import { mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || join(root, 'dist/e2e');
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  acceptDownloads: true
});
const page = await context.newPage();

const problems = [];
page.on('pageerror', (e) => problems.push('Ошибка страницы: ' + e.message));
page.on('console', (m) => { if (m.type() === 'error') problems.push('console.error: ' + m.text()); });

const check = (condition, message) => { if (!condition) problems.push(message); };

await page.goto(pathToFileURL(join(root, 'dist/index.html')).href);

/* 1. Блок и список */
await page.click('#fab');
await page.fill('#f-title', 'German Words B1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(250);

await page.click('#fab');
await page.fill('#f-title', 'Список 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(250);

/* 2. Слова: по одному и списком */
for (const [de, ru] of [['die Ordnung', 'порядок'], ['der Fünfjahresplan', 'пятилетний план']]) {
  await page.fill('#input-de', de);
  await page.fill('#input-ru', ru);
  await page.press('#input-ru', 'Enter');
  await page.waitForTimeout(150);
}

await page.click('[data-bulk]');
await page.fill('#f-text', [
  'die Straße — улица',
  'überprüfen; проверять',
  'das Bewusstsein\tсознание',
  '6. der Beschluss — постановление'
].join('\n'));
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(300);

check(await page.locator('.ledger__row').count() === 6, 'В ведомости должно быть 6 слов');
check((await page.locator('.gauge__value').textContent()).includes('6 / 500'), 'Счётчик заполнения неверен');
check((await page.locator('.ledger__de').last().textContent()).trim() === 'der Beschluss',
  'Нумерация из вставленной строки не отброшена');

await page.screenshot({ path: join(outDir, '03-words.png'), fullPage: true });

/* 3. Выгрузки */
for (const [selector, name] of [['[data-export="pdf"]', 'spisok.pdf'], ['[data-export="docx"]', 'spisok.docx']]) {
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 15000 }),
    page.click(selector)
  ]);
  await download.saveAs(join(outDir, name));
}

/* 4. Новые слова: сначала знакомство, потом сразу проверка */
check(await page.locator('[data-start-learn]').count() === 1, 'Нет кнопки «Учить»');
check(await page.locator('[data-start-drill]').count() === 0, 'Новые слова попали сразу в разбор');
await page.click('[data-start-learn]');
await page.waitForTimeout(400);

check((await page.locator('.drill__eyebrow').textContent()).includes('1 из 6'),
  'Знакомство начинается не с первого слова');
check(await page.locator('.learn__translation').count() === 1, 'На карточке знакомства нет перевода');
await page.screenshot({ path: join(outDir, '05-learn.png'), fullPage: false });

/* Шесть карточек знакомства, последняя ведёт к проверке */
for (let i = 0; i < 6; i++) {
  await page.click('[data-learn-next]');
  await page.waitForTimeout(850);
}

const prompt = (await page.locator('.drill__prompt').textContent()).trim();
check(await page.locator('#drill-input').count() === 1, 'После знакомства не началась проверка');
await page.screenshot({ path: join(outDir, '06-drill.png'), fullPage: false });

/* Верный ответ по показанному переводу */
const pairs = {
  'порядок': 'die Ordnung',
  'пятилетний план': 'der Fünfjahresplan',
  'улица': 'die Straße',
  'проверять': 'überprüfen',
  'сознание': 'das Bewusstsein',
  'постановление': 'der Beschluss'
};
await page.fill('#drill-input', pairs[prompt]);
await page.press('#drill-input', 'Enter');
await page.waitForTimeout(350);

check(await page.locator('.verdict--right').count() === 1, 'Верный ответ не засчитан');
const nextNote = await page.locator('.verdict__next').textContent();
check(/через 1 день/i.test(nextNote), `Ожидался срок «через 1 день», получено «${nextNote}»`);
await page.screenshot({ path: join(outDir, '07-right.png'), fullPage: false });

await page.click('[data-next]');
await page.waitForTimeout(900);

/* Неверный ответ: должны появиться две кнопки */
const prompt2 = (await page.locator('.drill__prompt').textContent()).trim();
await page.fill('#drill-input', 'ghhen');
await page.press('#drill-input', 'Enter');
await page.waitForTimeout(350);

check(await page.locator('.verdict--wrong').count() === 1, 'Ошибка не показана');
check(await page.locator('[data-outcome="typo"]').count() === 1, 'Нет кнопки «это опечатка»');
check(await page.locator('[data-outcome="forgot"]').count() === 1, 'Нет кнопки «забыл слово»');
check((await page.locator('.verdict__word').textContent()).trim() === pairs[prompt2],
  'Показано не то правильное слово');
await page.screenshot({ path: join(outDir, '08-wrong.png'), fullPage: false });

/* «Забыл» возвращает слово в начало лестницы и в конец очереди */
const queueBefore = await page.evaluate(() => document.querySelector('.drill__eyebrow').textContent);
await page.click('[data-outcome="forgot"]');
await page.waitForTimeout(900);

const forgotten = await page.evaluate((ru) => {
  const state = window.Store.getState();
  const word = state.blocks[0].sets[0].words.find((w) => w.ru === ru);
  return { level: word.level, lapses: word.lapses, mastered: word.mastered };
}, prompt2);
check(forgotten.level === 0, `После «забыл» уровень должен быть 0, получено ${forgotten.level}`);
check(forgotten.lapses === 1, 'Не засчитан срыв');

/* 5. Лестница интервалов на данных: 1 → 3 → 7 → 14 → 30 → 60 → освоено */
const ladder = await page.evaluate(() => {
  /* Лестницу проверяем на отдельном слове: остальные уже прошли проверку. */
  const block = window.Store.getState().blocks[0];
  window.Store.addWord(block.id, block.sets[0].id, 'die Leiter', 'стремянка');
  const word = block.sets[0].words.find((w) => w.de === 'die Leiter');
  const steps = [];
  for (let i = 0; i < 7; i++) {
    const r = window.Store.reviewWord(word.id, 'correct');
    steps.push(r.mastered ? 'освоено' : window.Store.studyDaysBetween(Date.now(), word.due));
  }
  return steps;
});
check(JSON.stringify(ladder) === JSON.stringify([1, 3, 7, 14, 30, 60, 'освоено']),
  `Лестница интервалов неверна: ${JSON.stringify(ladder)}`);

/* Слово со сбросом после 60 дней уходит в самое начало */
const reset = await page.evaluate(() => {
  const word = window.Store.getState().blocks[0].sets[0].words.find((w) => w.de === 'die Leiter');
  window.Store.reviewWord(word.id, 'forgot');
  return { level: word.level, mastered: word.mastered };
});
check(reset.level === 0 && reset.mastered === false, 'Сброс после освоения не вернул слово в начало');

/* 6. Данные переживают перезагрузку */
await page.reload();
await page.waitForTimeout(300);
check((await page.locator('.card__title').first().textContent()).trim() === 'German Words B1',
  'Данные не пережили перезагрузку');
await page.screenshot({ path: join(outDir, '01-blocks.png'), fullPage: true });

await page.click('.card');
await page.waitForTimeout(350);
await page.screenshot({ path: join(outDir, '02-sets.png'), fullPage: true });

/* 7. Предел в 500 слов */
await page.evaluate(() => {
  const set = window.Store.getState().blocks[0].sets[0];
  while (set.words.length < 500) {
    set.words.push({
      id: 'x' + set.words.length, de: 'Wort' + set.words.length, ru: 'слово',
      createdAt: Date.now(), level: 1, due: Date.now() + 9e9, reps: 1, lapses: 0, mastered: false
    });
  }
  window.Store.save();
});
await page.click('.card');
await page.waitForTimeout(350);

check(await page.locator('#input-de').count() === 0, 'Форма ввода доступна в заполненном списке');
const overflow = await page.evaluate(() => {
  const s = window.Store.getState().blocks[0];
  return window.Store.addWord(s.id, s.sets[0].id, 'Überschuss', 'излишек').ok;
});
check(overflow === false, 'Слово добавилось сверх предела в 500');

/* 8. Сворачивание списка слов */
await page.evaluate(() => {
  const state = window.Store.getState();
  state.blocks[0].sets[0].words.length = 5;
  window.Store.save();
});
await page.reload();
await page.waitForTimeout(300);
await page.click('.card');
await page.waitForTimeout(250);
await page.click('.card');
await page.waitForTimeout(300);

check(await page.locator('.ledger__row').count() === 5, 'Ведомость не показывает слова');
await page.click('[data-toggle-words]');
await page.waitForTimeout(900);
check(await page.locator('.ledger').count() === 0, 'Список не свернулся');
check(await page.locator('.folded').count() === 1, 'Нет свёрнутой полосы');

await page.reload();
await page.waitForTimeout(300);
await page.click('.card');
await page.waitForTimeout(250);
await page.click('.card');
await page.waitForTimeout(300);
check(await page.locator('.folded').count() === 1, 'Свёрнутое состояние не пережило перезагрузку');

await page.click('[data-open-words]');
await page.waitForTimeout(400);
check(await page.locator('.ledger__row').count() === 5, 'Список не развернулся обратно');
await page.screenshot({ path: join(outDir, '04-full.png'), fullPage: false });

/* 9. Вёрстка на телефоне: страница не должна ехать вбок,
      а растворение должно быть именно плавным. */
const layout = await page.evaluate(() => {
  const doc = document.documentElement;
  const wide = [...document.querySelectorAll('body *')]
    .filter((el) => el.getBoundingClientRect().right > window.innerWidth + 1)
    .map((el) => el.className || el.tagName);
  return { scrollWidth: doc.scrollWidth, innerWidth: window.innerWidth, overflowing: wide.slice(0, 5) };
});
check(layout.scrollWidth <= layout.innerWidth + 1,
  `Страница шире экрана: ${layout.scrollWidth} против ${layout.innerWidth}`);
check(layout.overflowing.length === 0, `Вылезают за экран: ${layout.overflowing.join(', ')}`);

const motion = await page.evaluate(() => {
  const probe = document.createElement('div');
  probe.className = 'is-evaporating';
  document.body.appendChild(probe);
  const style = getComputedStyle(probe);
  const result = { name: style.animationName, duration: style.animationDuration };
  probe.remove();
  return result;
});
check(motion.name === 'evaporate', `Растворение не подключено: ${motion.name}`);
check(parseFloat(motion.duration) >= 0.6, `Растворение слишком быстрое: ${motion.duration}`);

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log('Проверено: ввод, выгрузка, разбор (верно/опечатка/забыл), лестница 1-3-7-14-30-60, предел 500.');
