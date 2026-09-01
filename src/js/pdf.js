/* Генератор PDF без внешних библиотек.
   Шрифт встраивается как CIDFontType2 с кодировкой Identity-H, поэтому
   в документе корректно работают и умляуты, и кириллица.
   К каждому шрифту прикладывается ToUnicode CMap — без неё текст в PDF
   нельзя было бы выделить, скопировать или прочитать программой. */
window.PDFDoc = (function () {
  'use strict';

  var A4 = { width: 595.276, height: 841.89 };

  function num(n) {
    if (!isFinite(n)) n = 0;
    var s = n.toFixed(3);
    if (s.indexOf('.') >= 0) s = s.replace(/0+$/, '').replace(/\.$/, '');
    return s === '-0' ? '0' : s;
  }

  function latin1Bytes(str) {
    var out = new Uint8Array(str.length);
    for (var i = 0; i < str.length; i++) out[i] = str.charCodeAt(i) & 0xff;
    return out;
  }

  function hex4(n) {
    var s = n.toString(16).toUpperCase();
    return '0000'.slice(s.length) + s;
  }

  /* Текстовая строка PDF с кириллицей: UTF-16BE + BOM в виде hex. */
  function textString(str) {
    var out = 'FEFF';
    for (var i = 0; i < str.length; i++) {
      var code = str.codePointAt(i);
      if (code > 0xffff) {
        i++;
        var v = code - 0x10000;
        out += hex4(0xd800 + (v >> 10)) + hex4(0xdc00 + (v & 0x3ff));
      } else {
        out += hex4(code);
      }
    }
    return '<' + out + '>';
  }

  function pdfDate(d) {
    function p(n) { return (n < 10 ? '0' : '') + n; }
    var tz = -d.getTimezoneOffset();
    var sign = tz >= 0 ? '+' : '-';
    tz = Math.abs(tz);
    return 'D:' + d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) +
      p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds()) +
      sign + p(Math.floor(tz / 60)) + "'" + p(tz % 60) + "'";
  }

  /* ---------- Документ ---------- */

  function Doc(options) {
    options = options || {};
    this.pageSize = options.pageSize || A4;
    this.objects = [];        // тела объектов (индекс + 1 = номер объекта)
    this.pages = [];          // { content: [], }
    this.fonts = [];          // { key, ttf, used:Set, objNum }
    this.info = options.info || {};
  }

  Doc.prototype.addObject = function (body) {
    this.objects.push(body);
    return this.objects.length; // номера объектов начинаются с 1
  };

  /* ttf — результат TTF.parse(). key — имя ресурса, например 'F1'. */
  Doc.prototype.addFont = function (key, ttf, baseName) {
    var font = { key: key, ttf: ttf, used: new Set(), baseName: baseName || 'Embedded' };
    this.fonts.push(font);
    return font;
  };

  Doc.prototype.addPage = function () {
    var page = { ops: [] };
    this.pages.push(page);
    return page;
  };

  /* ---------- Рисование ---------- */

  /* tracking — разрядка; задаётся оператором Tc, а не раздельной
     отрисовкой букв, иначе текст извлекался бы с рваными пробелами. */
  Doc.prototype.text = function (page, str, x, y, font, size, color, tracking) {
    if (!str) return;
    var glyphs = font.ttf.glyphsFor(str);
    var hex = '';
    for (var i = 0; i < glyphs.length; i++) {
      hex += hex4(glyphs[i]);
      font.used.add(glyphs[i]);
    }
    var c = color || [0, 0, 0];
    page.ops.push(
      'BT ' + num(c[0]) + ' ' + num(c[1]) + ' ' + num(c[2]) + ' rg ' +
      '/' + font.key + ' ' + num(size) + ' Tf ' +
      num(tracking || 0) + ' Tc ' +
      '1 0 0 1 ' + num(x) + ' ' + num(y) + ' Tm ' +
      '<' + hex + '> Tj ET'
    );
  };

  Doc.prototype.textCentered = function (page, str, cx, y, font, size, color) {
    var w = font.ttf.widthOf(str, size);
    this.text(page, str, cx - w / 2, y, font, size, color);
  };

  Doc.prototype.textRight = function (page, str, right, y, font, size, color) {
    var w = font.ttf.widthOf(str, size);
    this.text(page, str, right - w, y, font, size, color);
  };

  /* Разрядка букв — характерный приём типографики той эпохи. */
  Doc.prototype.textTracked = function (page, str, x, y, font, size, tracking, color) {
    this.text(page, str, x, y, font, size, color, tracking);
    return this.trackedWidth(str, font, size, tracking);
  };

  Doc.prototype.trackedWidth = function (str, font, size, tracking) {
    var count = 0;
    for (var i = 0; i < str.length; i++) {
      if (str.codePointAt(i) > 0xffff) i++;
      count++;
    }
    return font.ttf.widthOf(str, size) + tracking * Math.max(0, count - 1);
  };

  Doc.prototype.rect = function (page, x, y, w, h, color) {
    var c = color || [0, 0, 0];
    page.ops.push(num(c[0]) + ' ' + num(c[1]) + ' ' + num(c[2]) + ' rg ' +
      num(x) + ' ' + num(y) + ' ' + num(w) + ' ' + num(h) + ' re f');
  };

  Doc.prototype.line = function (page, x1, y1, x2, y2, width, color) {
    var c = color || [0, 0, 0];
    page.ops.push(num(c[0]) + ' ' + num(c[1]) + ' ' + num(c[2]) + ' RG ' +
      num(width) + ' w ' + num(x1) + ' ' + num(y1) + ' m ' + num(x2) + ' ' + num(y2) + ' l S');
  };

  Doc.prototype.strokeRect = function (page, x, y, w, h, width, color) {
    var c = color || [0, 0, 0];
    page.ops.push(num(c[0]) + ' ' + num(c[1]) + ' ' + num(c[2]) + ' RG ' +
      num(width) + ' w ' + num(x) + ' ' + num(y) + ' ' + num(w) + ' ' + num(h) + ' re S');
  };

  /* Пятиконечная звезда — элемент оформления бланка. */
  Doc.prototype.star = function (page, cx, cy, radius, color) {
    var c = color || [0, 0, 0];
    var pts = [];
    for (var i = 0; i < 10; i++) {
      var r = i % 2 === 0 ? radius : radius * 0.382;
      var a = -Math.PI / 2 + (i * Math.PI) / 5;
      pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
    }
    var ops = num(c[0]) + ' ' + num(c[1]) + ' ' + num(c[2]) + ' rg ';
    ops += num(pts[0][0]) + ' ' + num(pts[0][1]) + ' m ';
    for (var j = 1; j < pts.length; j++) ops += num(pts[j][0]) + ' ' + num(pts[j][1]) + ' l ';
    ops += 'h f';
    page.ops.push(ops);
  };

  /* Перенос по словам с запасным разрывом внутри длинного слова. */
  Doc.prototype.wrap = function (str, font, size, maxWidth) {
    str = String(str == null ? '' : str).replace(/\s+/g, ' ').trim();
    if (!str) return [''];
    var words = str.split(' ');
    var lines = [];
    var current = '';

    function breakLongWord(word) {
      var chunk = '';
      for (var i = 0; i < word.length; i++) {
        var ch = word[i];
        if (font.ttf.widthOf(chunk + ch, size) > maxWidth && chunk) {
          lines.push(chunk);
          chunk = ch;
        } else {
          chunk += ch;
        }
      }
      return chunk;
    }

    for (var i = 0; i < words.length; i++) {
      var word = words[i];
      var candidate = current ? current + ' ' + word : word;
      if (font.ttf.widthOf(candidate, size) <= maxWidth) {
        current = candidate;
        continue;
      }
      if (current) { lines.push(current); current = ''; }
      if (font.ttf.widthOf(word, size) > maxWidth) {
        current = breakLongWord(word);
      } else {
        current = word;
      }
    }
    if (current) lines.push(current);
    return lines.length ? lines : [''];
  };

  /* ---------- Сериализация ---------- */

  function buildToUnicode(ttf, usedGlyphs) {
    /* Обратное отображение glyph id -> кодовая точка. */
    var reverse = new Map();
    ttf.cmap.forEach(function (gid, code) {
      if (!reverse.has(gid)) reverse.set(gid, code);
    });

    var entries = [];
    usedGlyphs.forEach(function (gid) {
      var code = reverse.get(gid);
      if (code !== undefined) entries.push([gid, code]);
    });
    entries.sort(function (a, b) { return a[0] - b[0]; });

    var body = '/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n' +
      '/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n' +
      '/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n' +
      '1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n';

    for (var i = 0; i < entries.length; i += 100) {
      var chunk = entries.slice(i, i + 100);
      body += chunk.length + ' beginbfchar\n';
      for (var j = 0; j < chunk.length; j++) {
        var code = chunk[j][1];
        var value;
        if (code > 0xffff) {
          var v = code - 0x10000;
          value = hex4(0xd800 + (v >> 10)) + hex4(0xdc00 + (v & 0x3ff));
        } else {
          value = hex4(code);
        }
        body += '<' + hex4(chunk[j][0]) + '> <' + value + '>\n';
      }
      body += 'endbfchar\n';
    }
    body += 'endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend';
    return body;
  }

  function buildWidthsArray(ttf) {
    var scale = 1000 / ttf.unitsPerEm;
    var parts = [];
    for (var gid = 0; gid < ttf.numGlyphs; gid++) {
      parts.push(Math.round(ttf.widths[gid] * scale));
    }
    return '[0 [' + parts.join(' ') + ']]';
  }

  Doc.prototype.build = function () {
    var self = this;
    var chunks = [];
    var offsets = [];
    var length = 0;

    function push(data) {
      var bytes = typeof data === 'string' ? latin1Bytes(data) : data;
      chunks.push(bytes);
      length += bytes.length;
    }

    /* Резервируем номера объектов заранее: 1 — каталог, 2 — дерево страниц. */
    var catalogNum = 1;
    var pagesNum = 2;
    var nextNum = 3;
    var fontResources = [];

    var fontObjects = [];
    this.fonts.forEach(function (font) {
      var ttf = font.ttf;
      var fileNum = nextNum++;
      var descNum = nextNum++;
      var cidNum = nextNum++;
      var toUniNum = nextNum++;
      var type0Num = nextNum++;
      fontObjects.push({
        font: font, fileNum: fileNum, descNum: descNum,
        cidNum: cidNum, toUniNum: toUniNum, type0Num: type0Num
      });
      fontResources.push('/' + font.key + ' ' + type0Num + ' 0 R');
    });

    var pageNums = [];
    var contentNums = [];
    this.pages.forEach(function () {
      pageNums.push(nextNum++);
      contentNums.push(nextNum++);
    });
    var infoNum = nextNum++;
    var totalObjects = nextNum - 1;

    function beginObject(numObj) {
      offsets[numObj] = length;
      push(numObj + ' 0 obj\n');
    }

    push('%PDF-1.7\n');
    push(new Uint8Array([0x25, 0xe2, 0xe3, 0xcf, 0xd3, 0x0a])); // бинарная метка

    beginObject(catalogNum);
    push('<< /Type /Catalog /Pages ' + pagesNum + ' 0 R /Lang (de-DE) >>\nendobj\n');

    beginObject(pagesNum);
    push('<< /Type /Pages /Count ' + this.pages.length + ' /Kids [' +
      pageNums.map(function (n) { return n + ' 0 R'; }).join(' ') + '] >>\nendobj\n');

    fontObjects.forEach(function (fo) {
      var ttf = fo.font.ttf;
      var scale = 1000 / ttf.unitsPerEm;

      beginObject(fo.fileNum);
      push('<< /Length ' + ttf.data.length + ' /Length1 ' + ttf.data.length + ' >>\nstream\n');
      push(ttf.data);
      push('\nendstream\nendobj\n');

      beginObject(fo.descNum);
      push('<< /Type /FontDescriptor /FontName /' + fo.font.baseName +
        ' /Flags 32 /FontBBox [' +
        Math.round(ttf.bbox[0] * scale) + ' ' + Math.round(ttf.bbox[1] * scale) + ' ' +
        Math.round(ttf.bbox[2] * scale) + ' ' + Math.round(ttf.bbox[3] * scale) + ']' +
        ' /ItalicAngle ' + num(ttf.italicAngle) +
        ' /Ascent ' + Math.round(ttf.ascent * scale) +
        ' /Descent ' + Math.round(ttf.descent * scale) +
        ' /CapHeight ' + Math.round(ttf.capHeight * scale) +
        ' /StemV 80 /FontFile2 ' + fo.fileNum + ' 0 R >>\nendobj\n');

      beginObject(fo.cidNum);
      push('<< /Type /Font /Subtype /CIDFontType2 /BaseFont /' + fo.font.baseName +
        ' /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>' +
        ' /FontDescriptor ' + fo.descNum + ' 0 R /DW 1000 /W ' + buildWidthsArray(ttf) +
        ' /CIDToGIDMap /Identity >>\nendobj\n');

      var toUni = buildToUnicode(ttf, fo.font.used);
      beginObject(fo.toUniNum);
      push('<< /Length ' + toUni.length + ' >>\nstream\n' + toUni + '\nendstream\nendobj\n');

      beginObject(fo.type0Num);
      push('<< /Type /Font /Subtype /Type0 /BaseFont /' + fo.font.baseName +
        ' /Encoding /Identity-H /DescendantFonts [' + fo.cidNum + ' 0 R]' +
        ' /ToUnicode ' + fo.toUniNum + ' 0 R >>\nendobj\n');
    });

    this.pages.forEach(function (page, index) {
      beginObject(pageNums[index]);
      push('<< /Type /Page /Parent ' + pagesNum + ' 0 R /MediaBox [0 0 ' +
        num(self.pageSize.width) + ' ' + num(self.pageSize.height) + ']' +
        ' /Resources << /Font << ' + fontResources.join(' ') + ' >> >>' +
        ' /Contents ' + contentNums[index] + ' 0 R >>\nendobj\n');

      var content = page.ops.join('\n');
      var contentBytes = latin1Bytes(content);
      beginObject(contentNums[index]);
      push('<< /Length ' + contentBytes.length + ' >>\nstream\n');
      push(contentBytes);
      push('\nendstream\nendobj\n');
    });

    var now = new Date();
    beginObject(infoNum);
    push('<< /Title ' + textString(this.info.title || 'Wortliste') +
      ' /Author ' + textString(this.info.author || '') +
      ' /Subject ' + textString(this.info.subject || '') +
      ' /Creator ' + textString(this.info.creator || 'Kabinett') +
      ' /Producer ' + textString(this.info.creator || 'Kabinett') +
      ' /CreationDate (' + pdfDate(now) + ') /ModDate (' + pdfDate(now) + ') >>\nendobj\n');

    var xrefOffset = length;
    var xref = 'xref\n0 ' + (totalObjects + 1) + '\n0000000000 65535 f \n';
    for (var i = 1; i <= totalObjects; i++) {
      var off = offsets[i] || 0;
      xref += ('0000000000' + off).slice(-10) + ' 00000 n \n';
    }
    push(xref);
    push('trailer\n<< /Size ' + (totalObjects + 1) + ' /Root ' + catalogNum + ' 0 R /Info ' +
      infoNum + ' 0 R >>\nstartxref\n' + xrefOffset + '\n%%EOF\n');

    var out = new Uint8Array(length);
    var pos = 0;
    for (var c = 0; c < chunks.length; c++) { out.set(chunks[c], pos); pos += chunks[c].length; }
    return out;
  };

  return { Doc: Doc, A4: A4 };
})();
