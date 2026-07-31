/* Сборка одного самодостаточного index.html: стили, скрипты и шрифты
   встраиваются внутрь файла. Такой файл одинаково работает и как сайт,
   и как содержимое assets в APK — без сети и без внешних зависимостей. */
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

/* Порядок важен: ui.js стартует приложение и опирается на остальные модули. */
const SCRIPTS = [
  'src/js/ttf.js',
  'src/js/zip.js',
  'src/js/pdf.js',
  'src/js/docx.js',
  'src/js/export.js',
  'src/js/store.js',
  'src/js/ui.js'
];

const read = (p) => readFileSync(join(root, p), 'utf8');

/* Закрывающий тег внутри строки в JS оборвал бы <script> раньше времени. */
const guard = (code) => code.replace(/<\/script/gi, '<\\/script');

const html = read('src/index.html');
const css = read('src/styles.css');
const js = SCRIPTS.map((p) => `/* ===== ${p} ===== */\n${read(p)}`).join('\n');

const fontRegular = readFileSync(join(root, 'assets/fonts/serif-regular.ttf')).toString('base64');
const fontBold = readFileSync(join(root, 'assets/fonts/serif-bold.ttf')).toString('base64');

const out = html
  .replace('<!--STYLES-->', `<style>\n${css}\n</style>`)
  .replace('<!--SCRIPTS-->', `<script>\n${guard(js)}\n</script>`)
  .replace('/*FONT_REGULAR*/', fontRegular)
  .replace('/*FONT_BOLD*/', fontBold);

for (const marker of ['<!--STYLES-->', '<!--SCRIPTS-->', '/*FONT_REGULAR*/', '/*FONT_BOLD*/']) {
  if (out.includes(marker)) throw new Error(`Метка ${marker} не заменена — проверьте src/index.html`);
}

mkdirSync(join(root, 'dist'), { recursive: true });
writeFileSync(join(root, 'dist/index.html'), out);

/* Вариант для публикации: страницу оборачивают в собственный каркас,
   поэтому отдаём только заголовок, стили, разметку и скрипты. */
const title = out.match(/<title>([\s\S]*?)<\/title>/)[1];
const bodyInner = out.match(/<body>([\s\S]*)<\/body>/)[1];
writeFileSync(
  join(root, 'dist/artifact.html'),
  `<title>${title}</title>\n<style>\n${css}\n</style>\n${bodyInner.trim()}\n`
);

/* Тот же файл кладём в Android-проект как единственный ресурс WebView. */
const assets = join(root, 'android/app/src/main/assets');
if (existsSync(dirname(assets))) {
  mkdirSync(assets, { recursive: true });
  copyFileSync(join(root, 'dist/index.html'), join(assets, 'index.html'));
}

const kb = (out.length / 1024).toFixed(1);
console.log(`dist/index.html собран: ${kb} КБ (скриптов: ${SCRIPTS.length}, шрифтов: 2)`);
