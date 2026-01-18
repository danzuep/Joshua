package me.xizzhu.android.joshua.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.xizzhu.android.joshua.data.entities.BookEntity
import me.xizzhu.android.joshua.data.entities.ChapterEntity
import me.xizzhu.android.joshua.data.entities.TranslationEntity
import me.xizzhu.android.joshua.data.entities.VerseEntity

@Dao
interface BibleDao {
    @Query("SELECT * FROM translations")
    suspend fun getTranslations(): List<TranslationEntity>

    @Query("SELECT * FROM books WHERE translation_short_name = :translation")
    suspend fun getBooks(translation: String): List<BookEntity>

    @Query("SELECT * FROM verses WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getVerses(bookId: Int, chapter: Int): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE text LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchVerses(query: String): List<VerseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(verses: List<VerseEntity>)
}