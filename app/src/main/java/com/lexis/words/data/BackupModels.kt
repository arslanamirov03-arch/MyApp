package com.lexis.words.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupBlock(
    val id: Long, val name: String, val colorHex: String,
    val coverImageFile: String?, val position: Int, val createdAt: Long,
)

@Serializable
data class BackupList(
    val id: Long, val blockId: Long, val name: String, val position: Int, val createdAt: Long,
)

@Serializable
data class BackupWord(
    val id: Long, val listId: Long, val de: String, val ru: String,
    val imageFile: String?, val stage: Int, val mastered: Boolean,
    val dueEpochDay: Long, val lastReviewedAt: Long?,
    val correctCount: Int, val wrongCount: Int, val createdAt: Long,
)

@Serializable
data class BackupSettings(
    val imagesEnabled: Boolean, val soundEnabled: Boolean, val vibrationEnabled: Boolean,
    val batchSize: Int, val wordLimitPerList: Int,
)

@Serializable
data class BackupSigning(
    val keystoreFileName: String,
    val keyAlias: String,
    val storePassword: String,
    val keyPassword: String,
    val packageName: String,
    val sha256: String,
)

@Serializable
data class BackupManifest(
    val appVersion: String,
    val createdAt: Long,
    val blocks: List<BackupBlock>,
    val lists: List<BackupList>,
    val words: List<BackupWord>,
    val settings: BackupSettings,
    val signing: BackupSigning?,
)
