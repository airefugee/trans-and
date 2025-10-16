package com.example.translatorapp.data.datasource

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.translatorapp.data.model.TranslationHistoryEntity

@Database(
    entities = [TranslationHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
