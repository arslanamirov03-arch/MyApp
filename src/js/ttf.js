/* Минимальный разбор TrueType: cmap (unicode -> glyph id), hmtx (ширины),
   head/hhea/OS2 (метрики для PDF FontDescriptor).
   Нужен для встраивания шрифта в PDF как CIDFontType2 / Identity-H. */
window.TTF = (function () {
  'use strict';

  function readTables(dv, buf) {
    var numTables = dv.getUint16(4);
    var tables = {};
    for (var i = 0; i < numTables; i++) {
      var o = 12 + i * 16;
      var tag = String.fromCharCode(buf[o], buf[o + 1], buf[o + 2], buf[o + 3]);
      tables[tag] = { offset: dv.getUint32(o + 8), length: dv.getUint32(o + 12) };
    }
    return tables;
  }

  function parseCmap4(dv, off, map) {
    var segCountX2 = dv.getUint16(off + 6);
    var segCount = segCountX2 / 2;
    var endOff = off + 14;
    var startOff = endOff + segCountX2 + 2;
    var deltaOff = startOff + segCountX2;
    var rangeOff = deltaOff + segCountX2;
    for (var s = 0; s < segCount; s++) {
      var end = dv.getUint16(endOff + s * 2);
      var start = dv.getUint16(startOff + s * 2);
      var delta = dv.getInt16(deltaOff + s * 2);
      var ro = dv.getUint16(rangeOff + s * 2);
      if (start > end) continue;
      for (var c = start; c <= end && c !== 0x10000; c++) {
        var gid;
        if (ro === 0) {
          gid = (c + delta) & 0xffff;
        } else {
          var addr = rangeOff + s * 2 + ro + (c - start) * 2;
          if (addr + 1 >= dv.byteLength) continue;
          gid = dv.getUint16(addr);
          if (gid !== 0) gid = (gid + delta) & 0xffff;
        }
        if (gid !== 0) map.set(c, gid);
      }
    }
  }

  function parseCmap12(dv, off, map) {
    var nGroups = dv.getUint32(off + 12);
    for (var g = 0; g < nGroups; g++) {
      var p = off + 16 + g * 12;
      var start = dv.getUint32(p);
      var end = dv.getUint32(p + 4);
      var startGid = dv.getUint32(p + 8);
      if (end - start > 0x10000) end = start + 0x10000;
      for (var c = start; c <= end; c++) map.set(c, startGid + (c - start));
    }
  }

  /* buf: Uint8Array с содержимым .ttf */
  function parse(buf) {
    var dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
    var tables = readTables(dv, buf);
    if (!tables.head || !tables.hhea || !tables.hmtx || !tables.maxp || !tables.cmap) {
      throw new Error('TTF: отсутствуют обязательные таблицы');
    }

    var head = tables.head.offset;
    var unitsPerEm = dv.getUint16(head + 18);
    var bbox = [
      dv.getInt16(head + 36), dv.getInt16(head + 38),
      dv.getInt16(head + 40), dv.getInt16(head + 42)
    ];

    var hhea = tables.hhea.offset;
    var ascent = dv.getInt16(hhea + 4);
    var descent = dv.getInt16(hhea + 6);
    var numberOfHMetrics = dv.getUint16(hhea + 34);

    var numGlyphs = dv.getUint16(tables.maxp.offset + 4);

    var widths = new Uint16Array(numGlyphs);
    var hm = tables.hmtx.offset;
    var lastWidth = 0;
    for (var i = 0; i < numGlyphs; i++) {
      if (i < numberOfHMetrics) lastWidth = dv.getUint16(hm + i * 4);
      widths[i] = lastWidth;
    }

    /* Выбираем самую подходящую подтаблицу cmap. */
    var cmapOff = tables.cmap.offset;
    var nt = dv.getUint16(cmapOff + 2);
    var best = -1, bestScore = -1, bestFmt = 0;
    for (var t = 0; t < nt; t++) {
      var p = cmapOff + 4 + t * 8;
      var pid = dv.getUint16(p);
      var eid = dv.getUint16(p + 2);
      var sub = cmapOff + dv.getUint32(p + 4);
      var fmt = dv.getUint16(sub);
      var score = -1;
      if (pid === 3 && eid === 10 && fmt === 12) score = 5;
      else if (pid === 3 && eid === 1 && fmt === 4) score = 4;
      else if (pid === 0 && fmt === 12) score = 3;
      else if (pid === 0 && fmt === 4) score = 2;
      if (score > bestScore) { bestScore = score; best = sub; bestFmt = fmt; }
    }
    if (best < 0) throw new Error('TTF: не найдена поддерживаемая подтаблица cmap');

    var map = new Map();
    if (bestFmt === 4) parseCmap4(dv, best, map);
    else if (bestFmt === 12) parseCmap12(dv, best, map);
    else throw new Error('TTF: формат cmap ' + bestFmt + ' не поддерживается');

    var italicAngle = 0;
    if (tables.post) italicAngle = dv.getInt32(tables.post.offset + 4) / 65536;

    var capHeight = Math.round(ascent * 0.7);
    if (tables['OS/2']) {
      var os2 = tables['OS/2'].offset;
      var version = dv.getUint16(os2);
      if (version >= 2 && tables['OS/2'].length >= 90) capHeight = dv.getInt16(os2 + 88);
      var typoAsc = dv.getInt16(os2 + 68);
      var typoDesc = dv.getInt16(os2 + 70);
      if (typoAsc) ascent = typoAsc;
      if (typoDesc) descent = typoDesc;
    }

    return {
      data: buf,
      unitsPerEm: unitsPerEm,
      bbox: bbox,
      ascent: ascent,
      descent: descent,
      capHeight: capHeight,
      italicAngle: italicAngle,
      numGlyphs: numGlyphs,
      widths: widths,
      cmap: map,

      /* Глиф для кодовой точки; 0 (.notdef) если символа нет в шрифте. */
      gidFor: function (code) {
        var g = this.cmap.get(code);
        return g === undefined ? 0 : g;
      },

      /* Строка -> массив glyph id (с учётом суррогатных пар). */
      glyphsFor: function (str) {
        var out = [];
        for (var i = 0; i < str.length; i++) {
          var code = str.codePointAt(i);
          if (code > 0xffff) i++;
          out.push(this.gidFor(code));
        }
        return out;
      },

      /* Ширина строки в пунктах при заданном кегле. */
      widthOf: function (str, size) {
        var total = 0;
        for (var i = 0; i < str.length; i++) {
          var code = str.codePointAt(i);
          if (code > 0xffff) i++;
          var gid = this.gidFor(code);
          total += this.widths[gid] || 0;
        }
        return (total / this.unitsPerEm) * size;
      }
    };
  }

  return { parse: parse };
})();
