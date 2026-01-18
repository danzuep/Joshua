package me.xizzhu.android.joshua.domain.usecases

import me.xizzhu.android.joshua.domain.BibleRepository
import me.xizzhu.android.joshua.domain.models.Translation
import javax.inject.Inject

class GetTranslationsUseCase @Inject constructor(
    private val bibleRepository: BibleRepository
) {
    suspend operator fun invoke(): List<Translation> {
        return bibleRepository.getTranslations()
    }
}