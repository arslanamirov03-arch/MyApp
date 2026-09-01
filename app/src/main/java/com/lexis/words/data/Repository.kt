package com.lexis.words.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class Repository(private val context: Context, private val db: AppDatabase) {
    val blockDao get() = db.blockDao()
    val wordListDao get() = db.wordListDao()
    val wordDao get() = db.wordDao()

    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply { mkdirs() }
    }

    suspend fun createBlock(name: String, colorHex: String): Long {
        val pos = blockDao.nextPosition()
        return blockDao.insert(BlockEntity(name = name, colorHex = colorHex, position = pos))
    }

    suspend fun createList(blockId: Long, name: String): Long {
        val pos = wordListDao.nextPosition(blockId)
        return wordListDao.insert(WordListEntity(blockId = blockId, name = name, position = pos))
    }

    suspend fun addWord(listId: Long, de: String, ru: String, imagePath: String?): Long {
        return wordDao.insert(WordEntity(listId = listId, de = de.trim(), ru = ru.trim(), imagePath = imagePath))
    }

    suspend fun addWordsBulk(listId: Long, pairs: List<Pair<String, String>>) {
        wordDao.insertAll(pairs.map { (de, ru) -> WordEntity(listId = listId, de = de.trim(), ru = ru.trim()) })
    }

    /** One pair per line; separator is a dash (— – -), a semicolon, or a tab. */
    fun parseBulkText(text: String): List<Pair<String, String>> =
        text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+[—–-]\\s+|\\t|;"), limit = 2)
                val de = parts.getOrNull(0)?.trim().orEmpty()
                val ru = parts.getOrNull(1)?.trim().orEmpty()
                if (de.isNotEmpty() && ru.isNotEmpty()) de to ru else null
            }

    /** Copies a picked/pasted image into app-private storage and returns its path. */
    fun importImage(source: Uri): String? {
        return try {
            val out = File(imagesDir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun imagesDirectory(): File = imagesDir
}
