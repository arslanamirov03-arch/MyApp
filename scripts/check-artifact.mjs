/* Проверка dist/artifact.html в том виде, в каком его увидит просмотрщик:
   собственного каркаса у файла нет, его оборачивают при публикации. */
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || join(root, 'dist/artifact-check');
mkdirSync(outDir, { recursive: true });

const inner = readFileSync(join(root, 'dist/artifact.html'), 'utf8');
for (const tag of ['<!doctype', '<html', '<head>', '<body>']) {
  if (inner.toLowerCase().includes(tag)) throw new Error(`В artifact.html не должно быть ${tag}`);
}

const wrapped = join(outDir, 'wrapped.html');
writeFileSync(wrapped,
  '<!doctype html><html lang="ru"><head><meta charset="utf-8">' +
  '<meta name="viewport" content="width=device-width, initial-scale=1"></head><body>' +
  inner + '</body></html>');

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const page = await (await browser.newContext({
  viewport: { width: 420, height: 900 },
  deviceScaleFactor: 2
})).newPage();

const errors = [];
page.on('pageerror', (e) => errors.push(e.message));

await page.goto(pathToFileURL(wrapped).href);
await page.waitForTimeout(400);

await page.click('#fab');
await page.fill('#f-title', 'German Words B1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(300);
await page.click('#fab');
await page.fill('#f-title', 'Список 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(300);
await page.fill('#input-de', 'die Ordnung');
await page.fill('#input-ru', 'порядок');
await page.press('#input-ru', 'Enter');
await page.waitForTimeout(300);

const words = await page.locator('.ledger__row').count();
await page.screenshot({ path: join(outDir, 'light.png') });

/* Переключатель темы в просмотрщике ставит атрибут на корневой элемент. */
await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
await page.waitForTimeout(350);
await page.screenshot({ path: join(outDir, 'dark.png') });

await browser.close();

if (errors.length) {
  console.error('Ошибки страницы:', errors.join(' | '));
  process.exit(1);
}
if (words !== 1) {
  console.error('Слово не добавилось в опубликованной сборке');
  process.exit(1);
}
console.log('artifact.html работает: каркаса нет, приложение запускается, обе темы отрисованы.');
