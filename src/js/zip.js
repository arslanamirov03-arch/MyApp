/* Минимальный писатель ZIP (метод store, без сжатия).
   Нужен для сборки .docx — это ZIP-контейнер с XML внутри. */
window.ZipWriter = (function () {
  'use strict';

  var CRC_TABLE = (function () {
    var table = new Uint32Array(256);
    for (var n = 0; n < 256; n++) {
      var c = n;
      for (var k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
    return table;
  })();

  function crc32(bytes) {
    var c = 0xffffffff;
    for (var i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  }

  function utf8(str) {
    return new TextEncoder().encode(str);
  }

  /* Дата/время в формате MS-DOS, как того требует спецификация ZIP. */
  function dosDateTime(date) {
    var year = date.getFullYear();
    if (year < 1980) year = 1980;
    var time = (date.getHours() << 11) | (date.getMinutes() << 5) | (date.getSeconds() >> 1);
    var day = ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
    return { time: time & 0xffff, date: day & 0xffff };
  }

  function Writer() {
    this.files = [];
    this.date = new Date();
  }

  /* content: строка (кодируется в UTF-8) или Uint8Array */
  Writer.prototype.add = function (name, content) {
    var bytes = typeof content === 'string' ? utf8(content) : content;
    this.files.push({ name: utf8(name), bytes: bytes, crc: crc32(bytes) });
    return this;
  };

  Writer.prototype.generate = function () {
    var dt = dosDateTime(this.date);
    var parts = [];
    var central = [];
    var offset = 0;
    var i;

    for (i = 0; i < this.files.length; i++) {
      var f = this.files[i];
      var local = new Uint8Array(30 + f.name.length);
      var dv = new DataView(local.buffer);
      dv.setUint32(0, 0x04034b50, true);   // сигнатура локального заголовка
      dv.setUint16(4, 20, true);           // версия для распаковки
      dv.setUint16(6, 0x0800, true);       // флаг: имена в UTF-8
      dv.setUint16(8, 0, true);            // метод: store
      dv.setUint16(10, dt.time, true);
      dv.setUint16(12, dt.date, true);
      dv.setUint32(14, f.crc, true);
      dv.setUint32(18, f.bytes.length, true);
      dv.setUint32(22, f.bytes.length, true);
      dv.setUint16(26, f.name.length, true);
      dv.setUint16(28, 0, true);
      local.set(f.name, 30);
      parts.push(local, f.bytes);

      var cd = new Uint8Array(46 + f.name.length);
      var cdv = new DataView(cd.buffer);
      cdv.setUint32(0, 0x02014b50, true);  // сигнатура записи каталога
      cdv.setUint16(4, 20, true);
      cdv.setUint16(6, 20, true);
      cdv.setUint16(8, 0x0800, true);
      cdv.setUint16(10, 0, true);
      cdv.setUint16(12, dt.time, true);
      cdv.setUint16(14, dt.date, true);
      cdv.setUint32(16, f.crc, true);
      cdv.setUint32(20, f.bytes.length, true);
      cdv.setUint32(24, f.bytes.length, true);
      cdv.setUint16(28, f.name.length, true);
      cdv.setUint32(42, offset, true);
      cd.set(f.name, 46);
      central.push(cd);

      offset += local.length + f.bytes.length;
    }

    var centralSize = 0;
    for (i = 0; i < central.length; i++) centralSize += central[i].length;

    var end = new Uint8Array(22);
    var edv = new DataView(end.buffer);
    edv.setUint32(0, 0x06054b50, true);
    edv.setUint16(8, this.files.length, true);
    edv.setUint16(10, this.files.length, true);
    edv.setUint32(12, centralSize, true);
    edv.setUint32(16, offset, true);

    var total = offset + centralSize + 22;
    var out = new Uint8Array(total);
    var pos = 0;
    for (i = 0; i < parts.length; i++) { out.set(parts[i], pos); pos += parts[i].length; }
    for (i = 0; i < central.length; i++) { out.set(central[i], pos); pos += central[i].length; }
    out.set(end, pos);
    return out;
  };

  return { Writer: Writer, crc32: crc32 };
})();
