package app.hoerpraxis.data

import kotlinx.serialization.Serializable

enum class ItemStatus { PENDING, DECODING, TRANSCRIBING, READY, ERROR }

@Serializable
data class AudioItem(
    val id: String,
    val title: String,
    val fileName: String,
    val durationMs: Long,
    val addedAt: Long,
    val status: ItemStatus = ItemStatus.PENDING,
    val progress: Int = 0,
    val lastPositionMs: Long = 0L,
    val speed: Float = 1.0f,
    val errorMessage: String? = null,
)

@Serializable
data class Word(
    val w: String,
    val s: Long,
    val e: Long,
)

@Serializable
data class Transcript(
    val words: List<Word> = emptyList(),
)

@Serializable
data class Library(
    val items: List<AudioItem> = emptyList(),
)
