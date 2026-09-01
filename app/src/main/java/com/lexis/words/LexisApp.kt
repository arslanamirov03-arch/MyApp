package com.lexis.words

import android.app.Application
import com.lexis.words.data.AppDatabase
import com.lexis.words.data.BackupManager
import com.lexis.words.data.ExportManager
import com.lexis.words.data.Repository
import com.lexis.words.data.SettingsStore

class LexisApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var exportManager: ExportManager
        private set
    lateinit var backupManager: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = Repository(this, db)
        settingsStore = SettingsStore(this)
        exportManager = ExportManager(this)
        backupManager = BackupManager(this, repository, settingsStore)
    }
}
