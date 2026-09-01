package com.lexis.words.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val coverImagePath: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "word_lists",
    foreignKeys = [ForeignKey(
        entity = BlockEntity::class,
        parentColumns = ["id"],
        childColumns = ["blockId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class WordListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val name: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * stage: -1 = new / never studied. 0..4 = index into [1,3,7,14,30] day intervals
 * (index 0 = due again tomorrow, index 4 = due again in 30 days).
 * mastered: true once the word has been answered correctly at stage 4 — it then
 * stops being scheduled for review.
 */
@Entity(
    tableName = "words",
    foreignKeys = [ForeignKey(
        entity = WordListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val de: String,
    val ru: String,
    val imagePath: String? = null,
    val stage: Int = -1,
    val mastered: Boolean = false,
    val dueEpochDay: Long = 0,
    val lastReviewedAt: Long? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class WordStatus { NEW, REPEAT, MASTERED }

val REPEAT_INTERVALS_DAYS = listOf(1, 3, 7, 14, 30)
const val DUE_HOUR = 6
