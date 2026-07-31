/* Проверка выгрузок вне браузера: собирает PDF, DOCX и TXT из тестового
   набора слов и кладёт их в указанную папку, чтобы файлы можно было
   открыть сторонними программами и убедиться, что текст читается. */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runInThisContext } from 'node:vm';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = process.argv[2] || join(root, 'dist/verify');

const fonts = {
  'font-serif-regular': readFileSync(join(root, 'assets/fonts/serif-regular.ttf')).toString('base64'),
  'font-serif-bold': readFileSync(join(root, 'assets/fonts/serif-bold.ttf')).toString('base64')
};

globalThis.window = globalThis;
globalThis.document = {
  getElementById: (id) => (fonts[id] ? { textContent: fonts[id] } : null)
};

for (const file of ['src/js/ttf.js', 'src/js/zip.js', 'src/js/pdf.js', 'src/js/docx.js', 'src/js/export.js']) {
  runInThisContext(readFileSync(join(root, file), 'utf8'), { filename: file });
}

/* Набор специально недобрый: умляуты, эсцет, кириллица, кавычки, тире,
   очень длинное составное слово и достаточное количество строк,
   чтобы документ занял несколько страниц. */
const words = [
  { de: 'die Ordnung', ru: 'порядок' },
  { de: 'der Fünfjahresplan', ru: 'пятилетний план' },
  { de: 'die Straße', ru: 'улица' },
  { de: 'das Bewusstsein', ru: 'сознание' },
  { de: 'überprüfen', ru: 'проверять, контролировать' },
  { de: 'die Rechenschaftspflicht', ru: 'обязанность отчитываться' },
  { de: 'die Donaudampfschifffahrtsgesellschaft', ru: 'общество дунайского пароходства (проверка переноса)' },
  { de: 'gewährleisten', ru: 'обеспечивать — гарантировать «результат»' },
  { de: 'der Beschluss', ru: 'постановление; решение' },
  { de: 'zäh', ru: 'упорный' }
];

const many = [];
for (let i = 0; i < 60; i++) {
  for (const w of words) many.push({ de: w.de, ru: w.ru });
}

const data = {
  blockTitle: 'German Words B1',
  setTitle: 'Список 1',
  title: 'German Words B1 — Список 1',
  words: many,
  meta: {
    line: 'Слов: ' + many.length + '  ·  составлено ' + window.Exporter.formatDate(Date.now()),
    footer: 'Кабинет немецкого языка'
  }
};

mkdirSync(outDir, { recursive: true });

const pdf = window.Exporter.buildPdf(data);
writeFileSync(join(outDir, 'proba.pdf'), pdf);

const docx = window.DocxWriter.build(data);
writeFileSync(join(outDir, 'proba.docx'), docx);

writeFileSync(join(outDir, 'proba.txt'), window.Exporter.buildTxt(data), 'utf8');

/* Небольшой список — на нём проверяется одностраничная вёрстка. */
const small = { ...data, setTitle: 'Список 2', words: words };
writeFileSync(join(outDir, 'proba-small.pdf'), window.Exporter.buildPdf(small));

console.log('слов в наборе:', many.length);
console.log('PDF:', pdf.length, 'байт →', join(outDir, 'proba.pdf'));
console.log('DOCX:', docx.length, 'байт →', join(outDir, 'proba.docx'));
