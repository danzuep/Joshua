/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 eBible
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ebible.android.bible.core

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ebible.android.bible.core.repository.BibleReadingRepository
import org.ebible.android.bible.core.repository.TranslationRepository
import co.touchlab.kermit.Logger

class BibleReadingManager(
        private val bibleReadingRepository: BibleReadingRepository, translationRepository: TranslationRepository, appScope: CoroutineScope
) {
    companion object {
        private val TAG = BibleReadingManager::class.java.simpleName
    }

    init {
        appScope.launch { translationRepository.downloadedTranslations.collect(::updateDownloadedTranslations) }
    }

    private var knownDownloadedTranslations: Set<String>? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun updateDownloadedTranslations(translations: List<TranslationInfo>) {
        try {
            val downloadedTranslations = translations.map { it.shortName }.toSet()
            // The first emission is the already downloaded set at startup, so only later emissions count as new downloads.
            val newlyDownloaded = knownDownloadedTranslations?.let { downloadedTranslations - it }.orEmpty()
            knownDownloadedTranslations = downloadedTranslations

            if (downloadedTranslations.isEmpty()) {
                bibleReadingRepository.saveCurrentTranslation("")
                bibleReadingRepository.clearParallelTranslation()
            } else {
                val previousCurrentTranslation = currentTranslation().first()
                var currentTranslation = previousCurrentTranslation
                if (currentTranslation.isEmpty() || !downloadedTranslations.contains(currentTranslation)) {
                    currentTranslation = translations.first().shortName
                }
                val newTranslation = translations.firstOrNull { newlyDownloaded.contains(it.shortName) }?.shortName
                if (newTranslation != null) {
                    currentTranslation = newTranslation
                }
                if (currentTranslation != previousCurrentTranslation) {
                    bibleReadingRepository.saveCurrentTranslation(currentTranslation)
                    moveToFirstBookWithVerses(currentTranslation)
                }

                parallelTranslations().first().let { current ->
                    // The previous current translation stays selected, at the top of the parallel translations.
                    val previous = if (newTranslation != null && previousCurrentTranslation.isNotEmpty()
                            && downloadedTranslations.contains(previousCurrentTranslation)) {
                        listOf(previousCurrentTranslation) + current
                    } else {
                        current
                    }
                    val updated = previous.distinct().filter { currentTranslation != it && downloadedTranslations.contains(it) }
                    if (current != updated) bibleReadingRepository.saveParallelTranslations(updated)
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error occurred while observing downloaded translations" }
        }
    }

    fun currentVerseIndex(): Flow<VerseIndex> = bibleReadingRepository.currentVerseIndex

    suspend fun saveCurrentVerseIndex(verseIndex: VerseIndex) {
        bibleReadingRepository.saveCurrentVerseIndex(verseIndex)
    }

    fun currentTranslation(): Flow<String> = bibleReadingRepository.currentTranslation

    suspend fun saveCurrentTranslation(translationShortName: String) {
        bibleReadingRepository.saveCurrentTranslation(translationShortName)
        moveToFirstBookWithVerses(translationShortName)
    }

    /** A partial translation, such as a New Testament, doesn't have the book that was open, so open its first book instead. */
    private suspend fun moveToFirstBookWithVerses(translationShortName: String) {
        try {
            val books = bibleReadingRepository.readBookIndicesWithVerses(translationShortName)
            val current = bibleReadingRepository.currentVerseIndex.first()
            if (books.isNotEmpty() && (!current.isValid() || current.bookIndex !in books)) {
                bibleReadingRepository.saveCurrentVerseIndex(VerseIndex(books.min(), 0, 0))
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to open a book that is in the translation" }
        }
    }

    fun parallelTranslations(): Flow<List<String>> = bibleReadingRepository.parallelTranslations

    suspend fun requestParallelTranslation(translationShortName: String) {
        bibleReadingRepository.requestParallelTranslation(translationShortName)
    }

    suspend fun removeParallelTranslation(translationShortName: String) {
        bibleReadingRepository.removeParallelTranslation(translationShortName)
    }

    suspend fun reorderTranslations(translationShortNames: List<String>) {
        val distinctShortNames = translationShortNames.distinct()
        if (distinctShortNames.isEmpty()) return
        bibleReadingRepository.saveCurrentTranslation(distinctShortNames.first())
        moveToFirstBookWithVerses(distinctShortNames.first())
        bibleReadingRepository.saveParallelTranslations(distinctShortNames.drop(1))
    }

    suspend fun readBookNames(translationShortName: String): List<String> =
            bibleReadingRepository.readBookNames(translationShortName)

    suspend fun readBookShortNames(translationShortName: String): List<String> =
            bibleReadingRepository.readBookShortNames(translationShortName)

    suspend fun readBookIndicesWithVerses(translationShortName: String): Set<Int> =
            bibleReadingRepository.readBookIndicesWithVerses(translationShortName)

    suspend fun readVerses(translationShortName: String, bookIndex: Int, chapterIndex: Int): List<Verse> =
            readStructuredVerses(translationShortName, bookIndex, chapterIndex).map { it.stripStructure() }

    /** The verses of the chapter with their paragraphs, headings and poetry lines, see [PARAGRAPH]. */
    suspend fun readStructuredVerses(translationShortName: String, bookIndex: Int, chapterIndex: Int): List<Verse> =
            bibleReadingRepository.readVerses(translationShortName, bookIndex, chapterIndex)

    suspend fun readVerses(translationShortName: String, parallelTranslations: List<String>,
                           bookIndex: Int, chapterIndex: Int): List<Verse> =
            bibleReadingRepository.readVerses(translationShortName, parallelTranslations, bookIndex, chapterIndex)

    suspend fun readVerses(translationShortName: String, verseIndexes: List<VerseIndex>): Map<VerseIndex, Verse> =
            bibleReadingRepository.readVerses(translationShortName, verseIndexes)
}
