package me.xizzhu.android.joshua.domain.usecases

import me.xizzhu.android.joshua.domain.BibleRepository
import me.xizzhu.android.joshua.domain.models.Book
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(
    private val bibleRepository: BibleRepository
) {
    suspend operator fun invoke(translation: String): List<Book> {
        return bibleRepository.getBooks(translation)
    }
}