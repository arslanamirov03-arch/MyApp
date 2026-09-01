package com.lexis.words.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Generates PDF / DOCX / TXT exports of a word list into the app's cache, ready to share. */
class ExportManager(private val context: Context) {

    private fun exportsDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun exportTxt(listName: String, words: List<WordEntity>): File {
        val file = File(exportsDir(), "${safe(listName)}.txt")
        file.writeText(words.joinToString("\n") { "${it.de} — ${it.ru}" })
        return file
    }

    fun exportPdf(listName: String, words: List<WordEntity>): File {
        val file = File(exportsDir(), "${safe(listName)}.pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val paint = Paint().apply { textSize = 12f }
        val muted = Paint().apply { textSize = 12f; color = 0xFF8B7F70.toInt() }

        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f
        val colWidth = (pageWidth - 60) / 2
        var col = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, doc.pages.size + 1).create())
            canvas = page!!.canvas
            y = 40f
            canvas!!.drawText(listName, 30f, y, titlePaint)
            y += 30f
            col = 0
        }
        newPage()

        for (w in words) {
            if (y > pageHeight - 40) {
                if (col == 0) { col = 1; y = 70f } else { newPage() }
            }
            val x = 30f + col * (colWidth + 20)
            canvas!!.drawText(w.de, x, y, paint)
            canvas!!.drawText(w.ru, x, y + 15f, muted)
            y += 34f
        }
        page?.let { doc.finishPage(it) }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** A minimal, valid .docx (OOXML) built by hand — no heavyweight dependency needed. */
    fun exportDocx(listName: String, words: List<WordEntity>): File {
        val file = File(exportsDir(), "${safe(listName)}.docx")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun entry(name: String, content: String) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val e = ZipEntry(name)
                zip.putNextEntry(e)
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                |<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                |<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                |<Default Extension="xml" ContentType="application/xml"/>
                |<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                |</Types>""".trimMargin()
            )
            entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                |<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                |<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                |</Relationships>""".trimMargin()
            )
            val rows = words.joinToString("") { w ->
                """<w:tr>
                   |<w:tc><w:p><w:r><w:t xml:space="preserve">${xmlEscape(w.de)}</w:t></w:r></w:p></w:tc>
                   |<w:tc><w:p><w:r><w:t xml:space="preserve">${xmlEscape(w.ru)}</w:t></w:r></w:p></w:tc>
                   |</w:tr>""".trimMargin()
            }
            entry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                |<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                |<w:body>
                |<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>${xmlEscape(listName)}</w:t></w:r></w:p>
                |<w:tbl>$rows</w:tbl>
                |</w:body>
                |</w:document>""".trimMargin()
            )
        }
        return file
    }

    private fun xmlEscape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun safe(name: String) = name.replace(Regex("[^A-Za-zА-Яа-яЁё0-9 _-]"), "_").ifBlank { "list" }
}
