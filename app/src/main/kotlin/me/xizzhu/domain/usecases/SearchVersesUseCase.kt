package me.xizzhu.android.joshua.domain.usecases

import me.xizzhu.android.joshua.domain.BibleRepository
import me.xizzhu.android.joshua.domain.models.Verse
import javax.inject.Inject

class SearchVersesUseCase @Inject constructor(
    private val bibleRepository: BibleRepository
) {
    suspend operator fun invoke(query: String): List<Verse> {
        return bibleRepository.searchVerses(query)
    }
}