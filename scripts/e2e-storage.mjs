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

/* 4. Полная копия переносит и слова, и картинки — как при переустановке
      приложения, когда прежнее хранилище стирается целиком. */
const picture = await page.evaluate(async () => {
  const S = window.Store;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 64;
  const c = canvas.getContext('2d');
  c.fillStyle = '#C05F3C';
  c.fillRect(0, 0, 64, 64);
  const url = canvas.toDataURL('image/jpeg', 0.8);

  const block = S.getState().blocks[0];
  const set = block.sets[0];
  const id = S.uid();
  await S.imageSave(id, url);
  S.setWordImage(set.words[0].id, id, 'ai');
  S.flush();
  return { url: url, word: set.words[0].de };
});

const archive = await page.evaluate(() => window.Store.exportArchive());
const parsedArchive = JSON.parse(archive);
check(Object.keys(parsedArchive.images || {}).length === 1,
  'В копии нет самих картинок');
check(parsedArchive.images[Object.keys(parsedArchive.images)[0]] === picture.url,
  'В копию попала не та картинка');

/* Переустановка: свежее приложение с пустым хранилищем — ровно то,
   что видит человек после переустановки APK. Копия должна поднять
   и слова, и картинки. */
const freshPage = await (await browser.newContext()).newPage();
await freshPage.goto(pathToFileURL(join(root, 'dist/index.html')).href);
await freshPage.waitForTimeout(600);

const empty = await freshPage.evaluate(() => window.Store.getState().blocks.length);
check(empty === 0, `Свежее приложение не пустое: блоков ${empty}`);

const revived = await freshPage.evaluate(async (text) => {
  await window.Store.importArchive(text, 'replace');
  const S = window.Store;
  const word = S.getState().blocks[0].sets[0].words[0];
  return {
    blocks: S.getState().blocks.length,
    de: word.de,
    hasImage: !!word.image,
    picture: word.image ? await S.imageLoad(word.image.id) : null
  };
}, archive);

check(revived.blocks > 0, 'Копия не восстановила блоки');
check(revived.de === picture.word, `После восстановления не то слово: ${revived.de}`);
check(revived.hasImage, 'После восстановления слово осталось без картинки');
check(revived.picture === picture.url, 'Картинка восстановилась не та');

/* И переживает перезапуск уже на новом месте */
await freshPage.reload();
await freshPage.waitForTimeout(600);
const afterRestart = await freshPage.evaluate(async () => {
  const word = window.Store.getState().blocks[0].sets[0].words[0];
  return word.image ? !!(await window.Store.imageLoad(word.image.id)) : false;
});
check(afterRestart, 'После перезапуска восстановленная картинка пропала');

/* Копия, снятая старой сборкой, картинок не содержит. Если самой картинки
   в базе тоже нет, ссылка на неё должна уйти — иначе в списке останется
   пустая рамка. */
const oldStyle = await freshPage.evaluate(async () => {
  const S = window.Store;
  const parsed = JSON.parse(await S.exportArchive());
  delete parsed.images;
  parsed.blocks[0].sets[0].words[0].image = { id: 'нет-такой-картинки', kind: 'ai' };
  await S.importArchive(JSON.stringify(parsed), 'replace');
  return S.getState().blocks[0].sets[0].words.filter((w) => w.image).length;
});
check(oldStyle === 0, `После копии без картинок остались ссылки на них: ${oldStyle}`);

await browser.close();

if (problems.length) {
  console.error('\nНАЙДЕНЫ ПРОБЛЕМЫ:');
  problems.forEach((p) => console.error(' · ' + p));
  process.exit(1);
}
console.log('Хранение проверено: две ячейки плюс база данных, прогресс переживает очистку любой ' +
  'из них, а полная копия переносит слова вместе с картинками через начисто стёртое хранилище.');
