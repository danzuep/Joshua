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

package org.ebible.android.bible.reading.toolbar

import android.app.Application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import org.ebible.android.bible.R
import org.ebible.android.bible.core.BibleReadingManager
import org.ebible.android.bible.core.TranslationManager
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.infra.BaseViewModel
import org.ebible.android.bible.ui.TranslationInfoComparator
import org.ebible.android.bible.utils.filterIsValid
import org.ebible.android.bible.utils.filterNotEmpty
import co.touchlab.kermit.Logger

@HiltViewModel
class ReadingToolbarViewModel @Inject constructor(
    private val bibleReadingManager: BibleReadingManager,
    translationManager: TranslationManager,
    application: Application
) : BaseViewModel<ReadingToolbarViewModel.ViewAction, ReadingToolbarViewModel.ViewState>(
    initialViewState = ViewState(
        title = application.getString(R.string.app_name),
        translationItems = emptyList(),
        error = null
    )
) {
    sealed class ViewAction

    data class ViewState(
        val title: String,
        val translationItems: List<TranslationItem>,
        val error: Error? = null
    ) {
        sealed class Error {
            data class ParallelTranslationRemovalError(val translationToRemove: String) : Error()
            data class ParallelTranslationRequestingError(val translationToRequest: String) : Error()
            data class TranslationSelectionError(val translationToSelect: String) : Error()
        }
    }

    private val translationComparator = TranslationInfoComparator(TranslationInfoComparator.SORT_ORDER_LANGUAGE_THEN_SHORT_NAME)

    /** The verse in the middle of the screen, with the saved (first visible) verse it was reported for. */
    private val scrolledVerses = MutableStateFlow<Pair<VerseIndex, VerseIndex>?>(null)

    /** The title follows the middle verse, while the saved reading position stays on [firstVerse]. */
    fun showReadingChapter(firstVerse: VerseIndex, middleVerse: VerseIndex) {
        scrolledVerses.value = firstVerse to middleVerse
    }

    init {
        combine(
            bibleReadingManager.currentTranslation().filterNotEmpty(),
            bibleReadingManager.currentVerseIndex().filterIsValid(),
            scrolledVerses,
        ) { currentTranslation, currentVerseIndex, scrolled ->
            // Ignored once the saved position has moved on, e.g. after choosing a chapter.
            val titleVerse = scrolled?.takeIf { it.first == currentVerseIndex }?.second ?: currentVerseIndex
            val currentBookShortName = bibleReadingManager.readBookShortNames(currentTranslation)[titleVerse.bookIndex]
            updateViewState { it.copy(title = "$currentBookShortName ${titleVerse.chapterIndex + 1}") }
        }.launchIn(viewModelScope)

        combine(
            bibleReadingManager.currentTranslation().filterNotEmpty(),
            bibleReadingManager.parallelTranslations(),
            translationManager.downloadedTranslations(),
        ) { currentTranslation, parallelTranslations, downloadedTranslations ->
            val translationItems = ArrayList<TranslationItem>(downloadedTranslations.size + 1)
            downloadedTranslations.sortedWith(translationComparator).forEach { downloaded ->
                val isCurrentTranslation = currentTranslation == downloaded.shortName
                translationItems.add(TranslationItem.Translation(
                    translationShortName = downloaded.shortName,
                    isCurrentTranslation = isCurrentTranslation,
                    isParallelTranslation = parallelTranslations.contains(downloaded.shortName),
                ))
            }
            translationItems.add(TranslationItem.More)
            updateViewState { it.copy(translationItems = translationItems) }
        }.launchIn(viewModelScope)
    }

    fun requestParallelTranslation(translationToRequest: String) {
        viewModelScope.launch {
            runCatching {
                bibleReadingManager.requestParallelTranslation(translationToRequest)
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to request parallel translation [$translationToRequest]" }
                updateViewState { it.copy(error = ViewState.Error.ParallelTranslationRequestingError(translationToRequest)) }
            }
        }
    }

    fun removeParallelTranslation(translationToRemove: String) {
        viewModelScope.launch {
            runCatching {
                if (bibleReadingManager.currentTranslation().first() == translationToRemove) {
                    // Unchecking the current translation hands the reader over to the next selected or downloaded one.
                    val replacement = bibleReadingManager.parallelTranslations().first().firstOrNull()
                        ?: viewState().first().translationItems.filterIsInstance<TranslationItem.Translation>()
                            .firstOrNull { it.translationShortName != translationToRemove }?.translationShortName
                    if (replacement != null) {
                        bibleReadingManager.saveCurrentTranslation(replacement)
                        bibleReadingManager.removeParallelTranslation(replacement)
                    }
                } else {
                    bibleReadingManager.removeParallelTranslation(translationToRemove)
                }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to remove parallel translation [$translationToRemove]" }
                updateViewState { it.copy(error = ViewState.Error.ParallelTranslationRemovalError(translationToRemove)) }
            }
        }
    }

    fun selectTranslation(translationToSelect: String) {
        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentTranslation(translationToSelect)

                runCatching {
                    bibleReadingManager.removeParallelTranslation(translationToSelect)
                }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to remove the selected translation from parallel" } }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to select translation [$translationToSelect]" }
                updateViewState { it.copy(error = ViewState.Error.TranslationSelectionError(translationToSelect)) }
            }
        }
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }
}
