/* Формирование выгрузок: PDF, Word (.docx) и простой текст.
   Файлы задуманы как «правильные»: текст в них настоящий, извлекаемый,
   с одинаковой структурой «номер / слово / перевод». */
window.Exporter = (function () {
  'use strict';

  var COLORS = {
    accent: [0.753, 0.373, 0.235],
    accentDeep: [0.620, 0.271, 0.153],
    ink: [0.200, 0.161, 0.122],
    muted: [0.443, 0.373, 0.298],
    stripe: [0.969, 0.949, 0.918],
    rule: [0.863, 0.812, 0.741]
  };

  var fontCache = null;

  /* Шрифты приложены к странице в base64 (см. сборку dist/index.html). */
  function loadFonts() {
    if (fontCache) return fontCache;
    var regular = document.getElementById('font-serif-regular');
    var bold = document.getElementById('font-serif-bold');
    if (!regular || !bold) throw new Error('Шрифты для PDF не найдены в документе');
    fontCache = {
      regular: window.TTF.parse(base64ToBytes(regular.textContent.trim())),
      bold: window.TTF.parse(base64ToBytes(bold.textContent.trim()))
    };
    return fontCache;
  }

  function base64ToBytes(b64) {
    var binary = atob(b64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  function bytesToBase64(bytes) {
    var chunk = 0x8000;
    var parts = [];
    for (var i = 0; i < bytes.length; i += chunk) {
      parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + chunk)));
    }
    return btoa(parts.join(''));
  }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function formatDate(ts) {
    var d = new Date(ts || Date.now());
    return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '.' + d.getFullYear();
  }

  function sanitizeFileName(name) {
    return String(name || 'liste')
      .replace(/[\\/:*?"<>|\x00-\x1F]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 70) || 'liste';
  }

  /* ---------- PDF ---------- */

  function buildPdf(data) {
    var fonts = loadFonts();
    var doc = new window.PDFDoc.Doc({
      info: {
        title: data.setTitle,
        subject: data.blockTitle,
        author: 'Wortschatz',
        creator: 'Wortschatz'
      }
    });

    var regular = doc.addFont('F1', fonts.regular, 'DejaVuSerif');
    var bold = doc.addFont('F2', fonts.bold, 'DejaVuSerif-Bold');

    var W = doc.pageSize.width;
    var H = doc.pageSize.height;
    var margin = 48;
    var contentLeft = margin;
    var contentRight = W - margin;
    var contentWidth = contentRight - contentLeft;

    var colNo = 32;
    var colDe = 200;
    var colRu = contentWidth - colNo - colDe;
    var cellPad = 6;
    var bodySize = 10.5;
    var lineHeight = 13;

    var words = data.words || [];
    var pages = [];
    var page = null;
    var y = 0;

    function drawPageFrame(p) {
      doc.strokeRect(p, 26, 26, W - 52, H - 52, 0.7, COLORS.rule);
    }

    function drawTableHead() {
      var rowH = 20;
      doc.rect(page, contentLeft, y - rowH, contentWidth, rowH, COLORS.accent);
      doc.text(page, '№', contentLeft + 10, y - rowH + 6.5, bold, 9, [1, 1, 1]);
      doc.textTracked(page, 'DEUTSCH', contentLeft + colNo + cellPad, y - rowH + 6.5, bold, 9, 0.8, [1, 1, 1]);
      doc.textTracked(page, 'ÜBERSETZUNG', contentLeft + colNo + colDe + cellPad, y - rowH + 6.5, bold, 9, 0.8, [1, 1, 1]);
      y -= rowH;
    }

    function newPage(isFirst) {
      page = doc.addPage();
      pages.push(page);
      drawPageFrame(page);
      y = H - margin - 6;

      if (isFirst) {
        doc.rect(page, W / 2 - 22, y - 10, 44, 1.6, COLORS.accent);
        y -= 34;

        var titleSize = 19;
        var title = (data.setTitle || 'СПИСОК СЛОВ').toUpperCase();
        var titleWidth = doc.trackedWidth(title, bold, titleSize, 2.2);
        /* Слишком длинный заголовок ужимаем, чтобы он не вылез за поля. */
        while (titleWidth > contentWidth && titleSize > 11) {
          titleSize -= 0.5;
          titleWidth = doc.trackedWidth(title, bold, titleSize, 2.2);
        }
        doc.textTracked(page, title, W / 2 - titleWidth / 2, y, bold, titleSize, 2.2, COLORS.accent);
        y -= 20;

        var sub = (data.blockTitle || '').toUpperCase();
        if (sub) {
          var subWidth = doc.trackedWidth(sub, regular, 10, 1.6);
          if (subWidth <= contentWidth) {
            doc.textTracked(page, sub, W / 2 - subWidth / 2, y, regular, 10, 1.6, COLORS.accentDeep);
          } else {
            doc.textCentered(page, sub, W / 2, y, regular, 10, COLORS.accentDeep);
          }
          y -= 16;
        }

        doc.line(page, contentLeft, y, contentRight, y, 0.8, COLORS.rule);
        y -= 15;

        var stamp = 'СЛОВ В СПИСКЕ: ' + words.length + '   ·   СОСТАВЛЕНО: ' + formatDate(Date.now());
        doc.textCentered(page, stamp, W / 2, y, regular, 8.5, COLORS.muted);
        y -= 20;
      } else {
        doc.textTracked(page, (data.setTitle || '').toUpperCase(), contentLeft, y - 4, regular, 8, 1.2, COLORS.muted);
        doc.line(page, contentLeft, y - 12, contentRight, y - 12, 0.6, COLORS.rule);
        y -= 26;
      }

      drawTableHead();
    }

    newPage(true);

    var bottomLimit = margin + 26;

    /* Длинные немецкие композиты не разрываем посередине: сначала
       подбираем кегль так, чтобы самое длинное слово влезло в колонку.
       Разорванное слово мешало бы и чтению, и разбору файла программой. */
    function fitCell(text, font, maxWidth) {
      var size = bodySize;
      var chunks = String(text == null ? '' : text).split(/\s+/);
      while (size > 7) {
        var longest = 0;
        for (var c = 0; c < chunks.length; c++) {
          longest = Math.max(longest, font.ttf.widthOf(chunks[c], size));
        }
        if (longest <= maxWidth) break;
        size -= 0.5;
      }
      return { size: size, lines: doc.wrap(text, font, size, maxWidth) };
    }

    for (var i = 0; i < words.length; i++) {
      var w = words[i];
      var de = fitCell(w.de, bold, colDe - cellPad * 2);
      var ru = fitCell(w.ru, regular, colRu - cellPad * 2);
      var deLines = de.lines;
      var ruLines = ru.lines;
      var rows = Math.max(deLines.length, ruLines.length);
      var rowHeight = rows * lineHeight + 7;

      if (y - rowHeight < bottomLimit) newPage(false);

      var top = y;
      if (i % 2 === 1) {
        doc.rect(page, contentLeft, top - rowHeight, contentWidth, rowHeight, COLORS.stripe);
      }

      var textY = top - lineHeight + 3.5;
      doc.text(page, String(i + 1), contentLeft + 6, textY, regular, 8.5, COLORS.muted);

      var k;
      for (k = 0; k < deLines.length; k++) {
        doc.text(page, deLines[k], contentLeft + colNo + cellPad, textY - k * lineHeight, bold, de.size, COLORS.ink);
      }
      for (k = 0; k < ruLines.length; k++) {
        doc.text(page, ruLines[k], contentLeft + colNo + colDe + cellPad, textY - k * lineHeight, regular, ru.size, COLORS.ink);
      }

      y = top - rowHeight;
      doc.line(page, contentLeft, y, contentRight, y, 0.4, COLORS.rule);
    }

    /* Вертикальные линии таблицы и рамка не рисуются построчно —
       нижнюю границу последней строки уже поставили выше. */
    doc.line(page, contentLeft, y, contentRight, y, 1, COLORS.accent);

    for (var p = 0; p < pages.length; p++) {
      var footer = 'ЛИСТ ' + (p + 1) + ' ИЗ ' + pages.length;
      doc.textCentered(pages[p], footer, W / 2, margin - 12, regular, 8, COLORS.muted);
    }

    return doc.build();
  }

  /* ---------- Простой текст ---------- */

  function buildTxt(data) {
    var lines = [];
    lines.push((data.setTitle || '').toUpperCase());
    if (data.blockTitle) lines.push(data.blockTitle);
    lines.push('Слов: ' + data.words.length + ' · ' + formatDate(Date.now()));
    lines.push('');
    for (var i = 0; i < data.words.length; i++) {
      lines.push((i + 1) + '. ' + data.words[i].de + ' — ' + data.words[i].ru);
    }
    lines.push('');
    return lines.join('\r\n');
  }

  /* ---------- Сохранение ---------- */

  /* В APK файл пишет Android через мост, в браузере — обычная ссылка. */
  function save(filename, bytes, mime) {
    if (window.AndroidBridge && typeof window.AndroidBridge.saveFile === 'function') {
      var result = window.AndroidBridge.saveFile(filename, bytesToBase64(bytes), mime);
      return { native: true, message: result };
    }
    var blob = new Blob([bytes], { type: mime });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
    return { native: false };
  }

  function exportSet(format, data) {
    var base = sanitizeFileName(
      (data.blockTitle ? data.blockTitle + ' — ' : '') + (data.setTitle || 'Wortliste')
    );
    if (format === 'pdf') {
      return save(base + '.pdf', buildPdf(data), 'application/pdf');
    }
    if (format === 'docx') {
      return save(base + '.docx', window.DocxWriter.build(data),
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    }
    if (format === 'txt') {
      return save(base + '.txt', new TextEncoder().encode(buildTxt(data)), 'text/plain;charset=utf-8');
    }
    throw new Error('Неизвестный формат: ' + format);
  }

  return {
    exportSet: exportSet,
    buildPdf: buildPdf,
    buildTxt: buildTxt,
    bytesToBase64: bytesToBase64,
    sanitizeFileName: sanitizeFileName,
    formatDate: formatDate
  };
})();
