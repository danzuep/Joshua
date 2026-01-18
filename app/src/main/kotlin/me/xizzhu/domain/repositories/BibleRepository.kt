package me.xizzhu.android.joshua.domain

import me.xizzhu.android.joshua.domain.models.Book
import me.xizzhu.android.joshua.domain.models.Translation
import me.xizzhu.android.joshua.domain.models.Verse

interface BibleRepository {
    suspend fun getTranslations(): List<Translation>
    suspend fun getBooks(translation: String): List<Book>
    suspend fun getVerses(bookId: Int, chapter: Int): List<Verse>
    suspend fun searchVerses(query: String): List<Verse>
}