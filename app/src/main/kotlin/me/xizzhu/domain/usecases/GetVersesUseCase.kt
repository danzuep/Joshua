package me.xizzhu.android.joshua.domain.usecases

import me.xizzhu.android.joshua.domain.BibleRepository
import me.xizzhu.android.joshua.domain.models.Verse
import javax.inject.Inject

class GetVersesUseCase @Inject constructor(
    private val bibleRepository: BibleRepository
) {
    suspend operator fun invoke(bookId: Int, chapter: Int): List<Verse> {
        return bibleRepository.getVerses(bookId, chapter)
    }
}