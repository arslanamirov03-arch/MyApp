/* Проверка расписания на подменённых часах: слово, выученное вечером,
   должно всплыть ровно в 6:00 следующего дня — и появиться на экране само,
   без перезапуска приложения. */
import { chromium } from 'playwright';
import { mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || join(root, 'dist/e2e-time');
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  timezoneId: 'Europe/Berlin'
});
const page = await context.newPage();

const problems = [];
page.on('pageerror', (e) => problems.push('Ошибка страницы: ' + e.message));
const check = (condition, message) => { if (!condition) problems.push(message); };

/* Вечер: 31 июля 2026, 20:00 по местному времени. */
await page.clock.install({ time: new Date('2026-07-31T20:00:00+02:00') });
await page.goto(pathToFileURL(join(root, 'dist/index.html')).href);
await page.waitForTimeout(300);

const sub = await page.locator('#masthead-sub').textContent();
check(sub.startsWith('20:00'), `Часы в шапке показывают «${sub}»`);

/* Блок, список и слово, добавленные вечером */
await page.click('#fab');
await page.fill('#f-title', 'Abendliste');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(200);
await page.click('#fab');
await page.fill('#f-title', 'Список 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(200);
await page.fill('#input-de', 'die Ordnung');
await page.fill('#input-ru', 'порядок');
await page.press('#input-ru', 'Enter');
await page.waitForTimeout(250);

/* Разбираем его тем же вечером — слово уходит на завтра, на 6 утра */
await page.click('[data-start-drill]');
await page.waitForTimeout(300);
await page.fill('#drill-input', 'die Ordnung');
await page.press('#drill-input', 'Enter');
await page.waitForTimeout(300);

const note = await page.locator('.verdict__next').textContent();
check(/1 августа/.test(note) && /06:00/.test(note), `Подпись о следующем разборе: «${note}»`);
await page.screenshot({ path: join(outDir, 'evening.png') });

const due = await page.evaluate(() => {
  const w = window.Store.getState().blocks[0].sets[0].words[0];
  const d = new Date(w.due);
  return { hour: d.getHours(), minute: d.getMinutes(), date: d.getDate(), month: d.getMonth() + 1 };
});
check(due.hour === 6 && due.minute === 0, `Слово назначено на ${due.hour}:${due.minute}, ожидалось 6:00`);
check(due.date === 1 && due.month === 8, `Слово назначено на ${due.date}.${due.month}, ожидалось 1.08`);

await page.goBack();
await page.waitForTimeout(300);

/* Ночь того же захода: 5:59 — слово ещё спит */
await page.clock.setSystemTime(new Date('2026-08-01T05:59:00+02:00'));
await page.clock.runFor('00:20');
await page.waitForTimeout(250);

check(await page.locator('[data-start-drill]').count() === 0, 'В 5:59 слово уже предлагается к разбору');
const beforeSix = await page.locator('#masthead-sub').textContent();
check(beforeSix.includes('05:59'), `В 5:59 часы показывают «${beforeSix}»`);
check(beforeSix.includes('следующие в 06:00'), `Нет подсказки о 6 утра: «${beforeSix}»`);
await page.screenshot({ path: join(outDir, 'before-six.png') });

/* 6:00 — приложение само поднимает слово, без перезагрузки */
await page.clock.setSystemTime(new Date('2026-08-01T06:00:05+02:00'));
await page.clock.runFor('00:20');
await page.waitForTimeout(300);

check(await page.locator('[data-start-drill]').count() === 1, 'В 6:00 слово не появилось само');
const afterSix = await page.locator('.call__title').textContent();
check(/1 слово ждёт разбора/.test(afterSix), `После 6:00 показано «${afterSix}»`);
await page.screenshot({ path: join(outDir, 'after-six.png') });

/* Ночная запись принадлежит прошедшему дню: слово, добавленное в 02:00,
   всплывает уже в 6:00 этого же утра. */
await page.clock.setSystemTime(new Date('2026-08-02T02:00:00+02:00'));
const nightDue = await page.evaluate(() => {
  const state = window.Store.getState();
  const block = state.blocks[0];
  window.Store.addWord(block.id, block.sets[0].id, 'die Nacht', 'ночь');
  const word = block.sets[0].words.find((w) => w.de === 'die Nacht');
  const next = window.Store.reviewWord(word.id, 'correct');
  const d = new Date(next.due);
  return { date: d.getDate(), hour: d.getHours() };
});
check(nightDue.date === 2 && nightDue.hour === 6,
  `Ночное слово назначено на ${nightDue.date} число ${nightDue.hour}:00, ожидалось 2-е число 6:00`);

/* Вся лестница держит шаг в днях и час появления */
const ladder = await page.evaluate(() => {
  const block = window.Store.getState().blocks[0];
  window.Store.addWord(block.id, block.sets[0].id, 'die Treppe', 'лестница');
  const word = block.sets[0].words.find((w) => w.de === 'die Treppe');
  const steps = [];
  for (let i = 0; i < 7; i++) {
    const r = window.Store.reviewWord(word.id, 'correct');
    steps.push(r.mastered ? 'освоено' : [r.days, new Date(r.due).getHours()]);
  }
  return steps;
});
check(JSON.stringify(ladder) === JSON.stringify([[1, 6], [3, 6], [7, 6], [14, 6], [30, 6], [60, 6], 'освоено']),
  `Лестница со временем появления неверна: ${JSON.stringify(ladder)}`);

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log('Расписание проверено: вечернее слово ждёт до 6:00, в 6:00 появляется само, ночь относится к прошедшему дню.');
