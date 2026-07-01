package com.arslan.b1vokabeln

/** A single vocabulary entry from the Goethe B1 word list. */
data class Word(
    val id: Int,
    val text: String,
    var learned: Boolean
)
