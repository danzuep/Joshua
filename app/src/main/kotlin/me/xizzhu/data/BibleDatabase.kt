package me.xizzhu.android.joshua.data

import androidx.room.Database
import androidx.room.RoomDatabase
import me.xizzhu.android.joshua.data.entities.BookEntity
import me.xizzhu.android.joshua.data.entities.ChapterEntity
import me.xizzhu.android.joshua.data.entities.TranslationEntity
import me.xizzhu.android.joshua.data.entities.VerseEntity

@Database(
    entities = [TranslationEntity::class, BookEntity::class, ChapterEntity::class, VerseEntity::class],
    version = 1,
    exportSchema = true
)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
}