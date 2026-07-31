/* Проверка сохранности прогресса: запись идёт в две ячейки и в базу
   данных, поэтому потеря одной из них не должна стоить пользователю слов. */
import { chromium } from 'playwright';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const page4 = pathToFileURL(join(root, 'dist/index.html')).href;

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
const page = await context.newPage();

const problems = [];
page.on('pageerror', (e) => problems.push('Ошибка страницы: ' + e.message));
const check = (condition, message) => { if (!condition) problems.push(message); };

await page.goto(page4);
await page.waitForTimeout(300);

await page.evaluate(() => {
  const block = window.Store.addBlock('Дальняя память');
  const set = window.Store.addSet(block.id, 'Список 1');
  window.Store.addWord(block.id, set.id, 'die Erinnerung', 'воспоминание');
  window.Store.flush();
});
await page.waitForTimeout(400);

const keys = await page.evaluate(() => Object.keys(localStorage).sort());
check(keys.includes('wortschatz.state'), 'Нет основной ячейки хранения');
check(keys.includes('wortschatz.state.spare'), 'Нет запасной ячейки хранения');

/* Основную ячейку стёрли — данные должны подняться из запасной. */
await page.evaluate(() => localStorage.removeItem('wortschatz.state'));
await page.reload();
await page.waitForTimeout(400);
check((await page.locator('.card__title').first().textContent()).trim() === 'Дальняя память',
  'Данные не восстановились из запасной ячейки');

/* Хранилище браузера очистили целиком — остаётся база данных. */
await page.evaluate(() => localStorage.clear());
await page.reload();
await page.waitForTimeout(1200);

const restored = await page.evaluate(() => {
  const blocks = window.Store.getState().blocks;
  return blocks.length ? blocks[0].title + '/' + blocks[0].sets[0].words[0].de : 'пусто';
});
check(restored === 'Дальняя память/die Erinnerung',
  `После полной очистки хранилища получено «${restored}»`);

/* Восстановленное состояние должно снова лечь в обычные ячейки. */
await page.evaluate(() => window.Store.flush());
await page.waitForTimeout(300);
const rewritten = await page.evaluate(() => !!localStorage.getItem('wortschatz.state'));
check(rewritten, 'Восстановленные данные не записались обратно');

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log('Хранение проверено: две ячейки плюс база данных, прогресс переживает очистку любой из них.');
