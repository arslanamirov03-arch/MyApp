/* Снимки для глаз: первая строка списка и окно поверх экрана. */
import { chromium } from 'playwright';
import { mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || join(root, 'dist/shots');
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({
  executablePath: existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined
});
const page = await (await browser.newContext({
  viewport: { width: 390, height: 844 }, deviceScaleFactor: 2
})).newPage();

await page.goto(pathToFileURL(join(root, 'dist/index.html')).href);
await page.waitForTimeout(300);
await page.click('#fab');
await page.fill('#f-title', 'Блок 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(250);
await page.click('#fab');
await page.fill('#f-title', 'Список 1');
await page.click('#modal-form button[type="submit"]');
await page.waitForTimeout(250);

for (const [de, ru] of [['die Ordnung','порядок'],['die Straße','улица'],['das Haus','дом']]) {
  await page.fill('#input-de', de);
  await page.fill('#input-ru', ru);
  await page.press('#input-ru', 'Enter');
  await page.waitForTimeout(200);
}
await page.waitForTimeout(500);
await page.locator('.ledger').screenshot({ path: join(outDir, 'ledger.png') });

/* Окно настроек поверх экрана */
await page.click('#btn-archive');
await page.waitForTimeout(600);
await page.screenshot({ path: join(outDir, 'modal.png') });

await browser.close();
console.log('снимки готовы:', outDir);
