package me.xizzhu.android.joshua.data.repositories

import me.xizzhu.android.joshua.data.dao.BibleDao
import me.xizzhu.android.joshua.domain.BibleRepository
import me.xizzhu.android.joshua.domain.models.Book
import me.xizzhu.android.joshua.domain.models.Translation
import me.xizzhu.android.joshua.domain.models.Verse
import javax.inject.Inject

class BibleRepositoryImpl @Inject constructor(
    private val bibleDao: BibleDao
) : BibleRepository {

    override suspend fun getTranslations(): List<Translation> {
        return bibleDao.getTranslations().map { entity ->
            Translation(
                shortName = entity.shortName,
                name = entity.name,
                language = entity.language,
                size = entity.size
            )
        }
    }

    override suspend fun getBooks(translation: String): List<Book> {
        return bibleDao.getBooks(translation).map { entity ->
            Book(
                id = entity.id,
                name = entity.name,
                shortName = entity.shortName
            )
        }
    }

    override suspend fun getVerses(bookId: Int, chapter: Int): List<Verse> {
        return bibleDao.getVerses(bookId, chapter).map { entity ->
            Verse(
                bookId = entity.bookId,
                chapter = entity.chapter,
                verse = entity.verse,
                text = entity.text
            )
        }
    }

    override suspend fun searchVerses(query: String): List<Verse> {
        return bibleDao.searchVerses(query).map { entity ->
            Verse(
                bookId = entity.bookId,
                chapter = entity.chapter,
                verse = entity.verse,
                text = entity.text
            )
        }
    }
}