/* Генератор .docx (Office Open XML) поверх собственного ZIP-писателя.
   Слова кладутся в настоящую таблицу Word — такой файл без потерь читают
   и Word, и LibreOffice, и парсеры документов. */
window.DocxWriter = (function () {
  'use strict';

  var W_NS = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main';

  function esc(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /* Word не принимает управляющие символы в тексте. */
  function clean(str) {
    return String(str == null ? '' : str).replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '');
  }

  function run(text, opts) {
    opts = opts || {};
    var props = '';
    if (opts.bold) props += '<w:b/>';
    if (opts.caps) props += '<w:caps/>';
    if (opts.color) props += '<w:color w:val="' + opts.color + '"/>';
    if (opts.size) props += '<w:sz w:val="' + opts.size * 2 + '"/><w:szCs w:val="' + opts.size * 2 + '"/>';
    if (opts.spacing) props += '<w:spacing w:val="' + opts.spacing + '"/>';
    if (opts.font) props += '<w:rFonts w:ascii="' + esc(opts.font) + '" w:hAnsi="' + esc(opts.font) + '" w:cs="' + esc(opts.font) + '"/>';
    return '<w:r>' + (props ? '<w:rPr>' + props + '</w:rPr>' : '') +
      '<w:t xml:space="preserve">' + esc(clean(text)) + '</w:t></w:r>';
  }

  function paragraph(runs, opts) {
    opts = opts || {};
    var props = '';
    if (opts.align) props += '<w:jc w:val="' + opts.align + '"/>';
    if (opts.spaceBefore || opts.spaceAfter) {
      props += '<w:spacing w:before="' + (opts.spaceBefore || 0) + '" w:after="' + (opts.spaceAfter || 0) + '"/>';
    }
    if (opts.border) {
      props += '<w:pBdr><w:bottom w:val="single" w:sz="' + (opts.border.size || 6) +
        '" w:space="4" w:color="' + (opts.border.color || '000000') + '"/></w:pBdr>';
    }
    if (opts.shading) props += '<w:shd w:val="clear" w:color="auto" w:fill="' + opts.shading + '"/>';
    if (opts.keepNext) props += '<w:keepNext/>';
    return '<w:p>' + (props ? '<w:pPr>' + props + '</w:pPr>' : '') + runs + '</w:p>';
  }

  function cell(content, widthTwips, opts) {
    opts = opts || {};
    var props = '<w:tcW w:w="' + widthTwips + '" w:type="dxa"/>';
    if (opts.shading) props += '<w:shd w:val="clear" w:color="auto" w:fill="' + opts.shading + '"/>';
    props += '<w:vAlign w:val="center"/>';
    return '<w:tc><w:tcPr>' + props + '</w:tcPr>' + content + '</w:tc>';
  }

  /* Собирает документ со словарной таблицей.
     data: { title, blockTitle, setTitle, words:[{de,ru}], meta:{...} } */
  function build(data) {
    var words = data.words || [];
    var font = 'Georgia';
    var accent = 'C05F3C';
    var gold = '9E4527';

    var colNo = 900, colDe = 4200, colRu = 4538;
    var totalWidth = colNo + colDe + colRu;

    var body = '';

    body += paragraph(
      run(data.setTitle || 'Список слов', { bold: true, size: 20, caps: true, spacing: 40, color: accent, font: font }),
      { align: 'center', spaceAfter: 40 }
    );
    body += paragraph(
      run(data.blockTitle || '', { size: 11, caps: true, spacing: 30, color: gold, font: font }),
      { align: 'center', spaceAfter: 40 }
    );
    body += paragraph(
      run((data.meta && data.meta.line) || '', { size: 9, color: '444444', font: font }),
      { align: 'center', spaceAfter: 200, border: { size: 12, color: accent } }
    );

    var rows = '';
    /* Шапка таблицы повторяется на каждой странице. */
    rows += '<w:tr><w:trPr><w:tblHeader/></w:trPr>' +
      cell(paragraph(run('№', { bold: true, size: 10, color: 'FFFFFF', font: font }), { align: 'center' }), colNo, { shading: accent }) +
      cell(paragraph(run('DEUTSCH', { bold: true, size: 10, caps: true, spacing: 20, color: 'FFFFFF', font: font })), colDe, { shading: accent }) +
      cell(paragraph(run('ÜBERSETZUNG', { bold: true, size: 10, caps: true, spacing: 20, color: 'FFFFFF', font: font })), colRu, { shading: accent }) +
      '</w:tr>';

    for (var i = 0; i < words.length; i++) {
      var w = words[i];
      var stripe = i % 2 === 1 ? 'F7F2EA' : null;
      rows += '<w:tr>' +
        cell(paragraph(run(String(i + 1), { size: 9, color: '777777', font: font }), { align: 'center' }), colNo, { shading: stripe }) +
        cell(paragraph(run(w.de, { bold: true, size: 11, font: font })), colDe, { shading: stripe }) +
        cell(paragraph(run(w.ru, { size: 11, font: font })), colRu, { shading: stripe }) +
        '</w:tr>';
    }

    var borders = '<w:tblBorders>' +
      '<w:top w:val="single" w:sz="8" w:space="0" w:color="' + accent + '"/>' +
      '<w:left w:val="single" w:sz="8" w:space="0" w:color="' + accent + '"/>' +
      '<w:bottom w:val="single" w:sz="8" w:space="0" w:color="' + accent + '"/>' +
      '<w:right w:val="single" w:sz="8" w:space="0" w:color="' + accent + '"/>' +
      '<w:insideH w:val="single" w:sz="4" w:space="0" w:color="DCCFBD"/>' +
      '<w:insideV w:val="single" w:sz="4" w:space="0" w:color="DCCFBD"/>' +
      '</w:tblBorders>';

    body += '<w:tbl><w:tblPr><w:tblW w:w="' + totalWidth + '" w:type="dxa"/>' +
      '<w:jc w:val="center"/>' + borders +
      '<w:tblCellMar><w:top w:w="60" w:type="dxa"/><w:left w:w="90" w:type="dxa"/>' +
      '<w:bottom w:w="60" w:type="dxa"/><w:right w:w="90" w:type="dxa"/></w:tblCellMar>' +
      '</w:tblPr><w:tblGrid>' +
      '<w:gridCol w:w="' + colNo + '"/><w:gridCol w:w="' + colDe + '"/><w:gridCol w:w="' + colRu + '"/>' +
      '</w:tblGrid>' + rows + '</w:tbl>';

    body += paragraph(
      run((data.meta && data.meta.footer) || '', { size: 8, color: '777777', font: font }),
      { align: 'center', spaceBefore: 200 }
    );

    var sectPr = '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>' +
      '<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" ' +
      'w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>';

    var document = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<w:document xmlns:w="' + W_NS + '"><w:body>' + body + sectPr + '</w:body></w:document>';

    var styles = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<w:styles xmlns:w="' + W_NS + '">' +
      '<w:docDefaults><w:rPrDefault><w:rPr>' +
      '<w:rFonts w:ascii="' + font + '" w:hAnsi="' + font + '" w:cs="' + font + '"/>' +
      '<w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="de-DE"/>' +
      '</w:rPr></w:rPrDefault>' +
      '<w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault>' +
      '</w:docDefaults>' +
      '<w:style w:type="paragraph" w:default="1" w:styleId="Normal">' +
      '<w:name w:val="Normal"/><w:qFormat/></w:style>' +
      '</w:styles>';

    var contentTypes = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' +
      '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>' +
      '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>' +
      '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>' +
      '</Types>';

    var rels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>' +
      '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>' +
      '</Relationships>';

    var docRels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' +
      '</Relationships>';

    var iso = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
    var core = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' +
      '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" ' +
      'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" ' +
      'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">' +
      '<dc:title>' + esc(data.title || '') + '</dc:title>' +
      '<dc:subject>' + esc(data.blockTitle || '') + '</dc:subject>' +
      '<cp:keywords>Deutsch, Wortschatz</cp:keywords>' +
      '<dcterms:created xsi:type="dcterms:W3CDTF">' + iso + '</dcterms:created>' +
      '<dcterms:modified xsi:type="dcterms:W3CDTF">' + iso + '</dcterms:modified>' +
      '</cp:coreProperties>';

    var zip = new window.ZipWriter.Writer();
    /* Порядок записей важен: [Content_Types].xml должен идти первым. */
    zip.add('[Content_Types].xml', contentTypes);
    zip.add('_rels/.rels', rels);
    zip.add('word/document.xml', document);
    zip.add('word/_rels/document.xml.rels', docRels);
    zip.add('word/styles.xml', styles);
    zip.add('docProps/core.xml', core);
    return zip.generate();
  }

  return { build: build };
})();
