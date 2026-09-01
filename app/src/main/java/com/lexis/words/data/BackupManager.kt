package com.lexis.words.data

import android.content.Context
import androidx.core.content.FileProvider
import android.net.Uri
import com.lexis.words.BuildConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One ".lexis" file = everything: words, images, review progress, block/list
 * structure, settings, the release signing key, and a source snapshot — so the
 * app can be restored (or rebuilt and re-signed) from a single download, per spec.
 */
class BackupManager(private val context: Context, private val repository: Repository, private val settingsStore: SettingsStore) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun createBackup(): File {
        val blocks = repository.blockDao.observeAllOnce()
        val lists = repository.wordListDao.observeAllOnce()
        val words = repository.wordDao.observeAllOnce()
        val settings = settingsStore.currentOnce()

        val manifest = BackupManifest(
            appVersion = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            createdAt = System.currentTimeMillis(),
            blocks = blocks.map {
                BackupBlock(it.id, it.name, it.colorHex, it.coverImagePath?.let(::File)?.name, it.position, it.createdAt)
            },
            lists = lists.map { BackupList(it.id, it.blockId, it.name, it.position, it.createdAt) },
            words = words.map {
                BackupWord(
                    it.id, it.listId, it.de, it.ru, it.imagePath?.let(::File)?.name,
                    it.stage, it.mastered, it.dueEpochDay, it.lastReviewedAt, it.correctCount, it.wrongCount, it.createdAt
                )
            },
            settings = BackupSettings(settings.imagesEnabled, settings.soundEnabled, settings.vibrationEnabled, settings.batchSize, settings.wordLimitPerList),
            signing = if (BuildConfig.HAS_REAL_SIGNING) BackupSigning(
                keystoreFileName = BuildConfig.SIGNING_KEYSTORE_NAME,
                keyAlias = BuildConfig.SIGNING_KEY_ALIAS,
                storePassword = BuildConfig.SIGNING_STORE_PASSWORD,
                keyPassword = BuildConfig.SIGNING_KEY_PASSWORD,
                packageName = BuildConfig.APPLICATION_ID,
                sha256 = BuildConfig.SIGNING_SHA256,
            ) else null,
        )

        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(outDir, "lexis-backup-$stamp.lexis")

        ZipOutputStream(outFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val imageNames = linkedSetOf<String>()
            (blocks.mapNotNull { it.coverImagePath } + words.mapNotNull { it.imagePath }).forEach { path ->
                val f = File(path)
                if (f.exists() && imageNames.add(f.name)) {
                    zip.putNextEntry(ZipEntry("images/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            copyAssetIfPresent(zip, "signing/lexis-release.jks", "signing/lexis-release.jks")
            copyAssetIfPresent(zip, "source/source.zip", "source/lexis-source.zip")
        }
        return outFile
    }

    private fun copyAssetIfPresent(zip: ZipOutputStream, assetPath: String, entryName: String) {
        try {
            context.assets.open(assetPath).use { input ->
                zip.putNextEntry(ZipEntry(entryName))
                input.copyTo(zip)
                zip.closeEntry()
            }
        } catch (_: Exception) {
            // Asset absent in this build (e.g. a local/debug build with no CI-injected signing) — skip.
        }
    }

    /** Wipes local data and restores it from a previously exported .lexis file. */
    suspend fun restoreBackup(source: Uri) {
        val tmpDir = File(context.cacheDir, "restore_tmp").apply { deleteRecursively(); mkdirs() }
        var manifest: BackupManifest? = null

        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        if (entry.name == "manifest.json") {
                            manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                        } else if (entry.name.startsWith("images/")) {
                            val dest = File(tmpDir, entry.name)
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val m = manifest ?: throw IllegalStateException("Файл резервной копии повреждён или это не .lexis файл")

        val imagesDir = repository.imagesDirectory()
        // Move restored images into permanent storage before touching the DB.
        val restoredImages = File(tmpDir, "images")
        val nameToPath = mutableMapOf<String, String>()
        if (restoredImages.exists()) {
            restoredImages.listFiles()?.forEach { f ->
                val dest = File(imagesDir, f.name)
                f.copyTo(dest, overwrite = true)
                nameToPath[f.name] = dest.absolutePath
            }
        }

        // Replace all rows. Foreign-key cascades clear lists/words when blocks are cleared.
        repository.blockDao.observeAllOnce().forEach { repository.blockDao.delete(it) }

        m.blocks.forEach { b ->
            repository.blockDao.insert(
                BlockEntity(b.id, b.name, b.colorHex, b.coverImageFile?.let { nameToPath[it] }, b.position, b.createdAt)
            )
        }
        m.lists.forEach { l ->
            repository.wordListDao.insert(WordListEntity(l.id, l.blockId, l.name, l.position, l.createdAt))
        }
        m.words.forEach { w ->
            repository.wordDao.insert(
                WordEntity(
                    w.id, w.listId, w.de, w.ru, w.imageFile?.let { nameToPath[it] },
                    w.stage, w.mastered, w.dueEpochDay, w.lastReviewedAt, w.correctCount, w.wrongCount, w.createdAt
                )
            )
        }
        settingsStore.setImagesEnabled(m.settings.imagesEnabled)
        settingsStore.setSoundEnabled(m.settings.soundEnabled)
        settingsStore.setVibrationEnabled(m.settings.vibrationEnabled)
        settingsStore.setBatchSize(m.settings.batchSize)

        tmpDir.deleteRecursively()
    }
}
