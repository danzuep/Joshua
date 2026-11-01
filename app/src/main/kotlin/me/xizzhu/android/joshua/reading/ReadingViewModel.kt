/*
 * Copyright (C) 2023 Xizhi Zhu
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

package me.xizzhu.android.joshua.reading

import android.app.Application
import androidx.annotation.IntDef
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.xizzhu.android.joshua.core.BibleReadingManager
import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.ReadingProgressManager
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.SettingsManager
import me.xizzhu.android.joshua.core.TranslationManager
import me.xizzhu.android.joshua.core.provider.TimeProvider
import me.xizzhu.android.joshua.core.Verse
import me.xizzhu.android.joshua.core.VerseAnnotationManager
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.infra.BaseViewModel
import me.xizzhu.android.joshua.reading.detail.VerseDetailViewLayout
import me.xizzhu.android.joshua.reading.detail.VerseTextItem
import me.xizzhu.android.joshua.reading.verse.toParagraphItems
import me.xizzhu.android.joshua.ui.TranslationInfoComparator
import me.xizzhu.android.joshua.ui.recyclerview.BaseItem
import me.xizzhu.android.joshua.utils.filterIsValid
import me.xizzhu.android.joshua.utils.filterNotEmpty
import me.xizzhu.android.joshua.utils.firstNotEmpty

class VerseDetailViewData(
    val verseIndex: VerseIndex, val verseTextItems: List<VerseTextItem>,
    var bookmarked: Boolean, @Highlight.Companion.AvailableColor var highlightColor: Int, var note: String
)

class VerseUpdate(val verseIndex: VerseIndex, @Operation val operation: Int, val data: Any? = null) {
    companion object {
        const val VERSE_SELECTED = 1
        const val VERSE_DESELECTED = 2
        const val NOTE_ADDED = 3
        const val NOTE_REMOVED = 4
        const val BOOKMARK_ADDED = 5
        const val BOOKMARK_REMOVED = 6
        const val HIGHLIGHT_UPDATED = 7

        @IntDef(VERSE_SELECTED, VERSE_DESELECTED, NOTE_ADDED, NOTE_REMOVED,
            BOOKMARK_ADDED, BOOKMARK_REMOVED, HIGHLIGHT_UPDATED)
        @Retention(AnnotationRetention.SOURCE)
        annotation class Operation
    }
}

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val bibleReadingManager: BibleReadingManager,
    private val readingProgressManager: ReadingProgressManager,
    private val translationManager: TranslationManager,
    private val bookmarkManager: VerseAnnotationManager<Bookmark>,
    private val highlightManager: VerseAnnotationManager<Highlight>,
    private val noteManager: VerseAnnotationManager<Note>,
    private val settingsManager: SettingsManager,
    private val timeProvider: TimeProvider,
    private val application: Application
) : BaseViewModel<ReadingViewModel.ViewAction, ReadingViewModel.ViewState>(
    initialViewState = ViewState(
        settings = Settings.DEFAULT,
        currentReadingStatus = null,
        error = null,
    )
) {
    sealed class ViewAction {
        data class ShowVerses(val bookIndex: Int, val chapterIndex: Int, val items: List<BaseItem>) : ViewAction()
        data class ShowVerseDetail(val verseDetail: VerseDetailViewData, @VerseDetailViewLayout.Companion.VerseDetail val content: Int) : ViewAction()
        data class UpdateVerse(val update: VerseUpdate) : ViewAction()
        data class BookmarkSaved(val bookmarked: Boolean) : ViewAction()
        data class HighlightSaved(@Highlight.Companion.AvailableColor val color: Int) : ViewAction()
        data class NoteSaved(val note: String) : ViewAction()
    }

    data class ViewState(
        val settings: Settings,
        val currentReadingStatus: CurrentReadingStatus?,
        val error: Error?,
    ) {
        data class CurrentReadingStatus(
            val currentTranslation: String, val parallelTranslations: List<String>,
            val currentVerseIndex: VerseIndex, val bookNames: List<String>
        )

        sealed class Error {
            object NoTranslationDownloadedError : Error()
            data class TranslationSelectionError(val translationToSelect: String) : Error()
            data class ChapterSelectionError(val bookIndex: Int, val chapterIndex: Int) : Error()
            data class VerseSelectionError(val verseToSelect: VerseIndex) : Error()
            data class VersesLoadingError(val bookIndex: Int, val chapterIndex: Int) : Error()
            data class VerseDetailLoadingError(val verseIndex: VerseIndex, @VerseDetailViewLayout.Companion.VerseDetail val content: Int) : Error()
            object BookmarkSavingError : Error()
            object HighlightSavingError : Error()
            object NoteSavingError : Error()
        }
    }

    init {
        settingsManager.settings()
            .onEach { settings -> updateViewState { it.copy(settings = settings) } }
            .launchIn(viewModelScope)

        combine(
            bibleReadingManager.currentTranslation().filterNotEmpty(),
            bibleReadingManager.parallelTranslations(),
            bibleReadingManager.currentVerseIndex().filterIsValid(),
        ) { currentTranslation, parallelTranslations, currentVerseIndex ->
            val currentReadingStatus = ViewState.CurrentReadingStatus(
                currentTranslation = currentTranslation,
                parallelTranslations = parallelTranslations,
                currentVerseIndex = currentVerseIndex,
                bookNames = bibleReadingManager.readBookNames(currentTranslation),
            )
            updateViewState { it.copy(currentReadingStatus = currentReadingStatus) }
        }.launchIn(viewModelScope)
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }

    private fun showError(error: ViewState.Error, e: Throwable, message: String) {
        Logger.withTag(tag).e(e) { message }
        updateViewState { it.copy(error = error) }
    }

    fun checkDownloadedTranslation() {
        viewModelScope.launch {
            if (!translationManager.downloadedTranslations().first().isEmpty()) return@launch
            updateViewState { it.copy(error = ViewState.Error.NoTranslationDownloadedError) }
        }
    }

    fun selectTranslation(translationToSelect: String) {
        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentTranslation(translationToSelect)

                try {
                    bibleReadingManager.removeParallelTranslation(translationToSelect)
                } catch (e: Exception) {
                    Logger.withTag(tag).e(e) { "Failed to remove the selected translation from parallel" }
                }
            }.onFailure { e ->
                showError(ViewState.Error.TranslationSelectionError(translationToSelect), e, "Failed to select translation")
            }
        }
    }

    suspend fun currentVerseIndex(): VerseIndex = bibleReadingManager.currentVerseIndex().first { it.isValid() }

    fun openBook(bookIndex: Int) {
        viewModelScope.launch {
            // Wait for the stored position to load first, so it can't overwrite the requested book.
            bibleReadingManager.currentVerseIndex().first()
            selectChapter(bookIndex, 0)
        }
    }

    fun selectChapter(bookIndex: Int, chapterIndex: Int) {
        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentVerseIndex(VerseIndex(bookIndex, chapterIndex, 0))
            }.onFailure { e ->
                showError(ViewState.Error.ChapterSelectionError(bookIndex, chapterIndex), e, "Failed to select chapter")
            }
        }
    }

    fun selectVerse(verseIndex: VerseIndex) {
        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentVerseIndex(verseIndex)
            }.onFailure { e ->
                showError(ViewState.Error.VerseSelectionError(verseIndex), e, "Failed to select verse index")
            }
        }
    }

    fun updateCurrentVerse(verseIndex: VerseIndex) {
        viewModelScope.launch {
            runCatching { bibleReadingManager.saveCurrentVerseIndex(verseIndex) }
                .onFailure { e -> Logger.withTag(tag).e(e) { "Failed to update current verse index" } }
        }
    }

    suspend fun bookName(translationShortName: String, bookIndex: Int): String {
        return bibleReadingManager.readBookNames(translationShortName)[bookIndex]
    }

    fun loadVerses(bookIndex: Int, chapterIndex: Int) {
        viewModelScope.launch {
            runCatching {
                val items = coroutineScope {
                    val highlights = async { highlightManager.read(bookIndex, chapterIndex) }

                    val bookmarks = async { bookmarkManager.read(bookIndex, chapterIndex) }
                    val notes = async { noteManager.read(bookIndex, chapterIndex) }

                    val currentTranslation = bibleReadingManager.currentTranslation().firstNotEmpty()
                    val verses = bibleReadingManager.readStructuredVerses(currentTranslation, bookIndex, chapterIndex)

                    verses.toParagraphItems(application, bookmarks.await(), highlights.await(), notes.await())
                }
                emitViewAction(ViewAction.ShowVerses(bookIndex, chapterIndex, items))
            }.onFailure { e ->
                showError(ViewState.Error.VersesLoadingError(bookIndex, chapterIndex), e, "Failed to load verses")
            }
        }
    }

    fun loadVerseDetail(verseIndex: VerseIndex, @VerseDetailViewLayout.Companion.VerseDetail content: Int) {
        viewModelScope.launch {
            runCatching {
                val verseDetail = readVerseDetail(verseIndex)
                emitViewAction(ViewAction.ShowVerseDetail(verseDetail, content))
                emitViewAction(ViewAction.UpdateVerse(VerseUpdate(verseIndex, VerseUpdate.VERSE_SELECTED)))
            }.onFailure { e ->
                showError(ViewState.Error.VerseDetailLoadingError(verseIndex, content), e, "Failed to load verse detail")
            }
        }
    }

    private suspend fun readVerseDetail(verseIndex: VerseIndex): VerseDetailViewData = coroutineScope {
        val bookmarked = async { bookmarkManager.read(verseIndex).isValid() }
        val highlightColor = async { highlightManager.read(verseIndex).takeIf { it.isValid() }?.color ?: Highlight.COLOR_NONE }
        val note = async { noteManager.read(verseIndex).takeIf { it.isValid() }?.note ?: "" }

        // build the verse
        val currentTranslation = bibleReadingManager.currentTranslation().firstNotEmpty()
        val parallelTranslations = translationManager.downloadedTranslations().first()
            .filter { it.shortName != currentTranslation }
            .sortedWith(TranslationInfoComparator(TranslationInfoComparator.SORT_ORDER_LANGUAGE_THEN_SHORT_NAME))
            .map { it.shortName }
        val verses = bibleReadingManager.readVerses(currentTranslation, parallelTranslations,
            verseIndex.bookIndex, verseIndex.chapterIndex)

        // step 1: finds the verse
        var start: VerseIndex? = null
        for (verse in verses) {
            if (verse.text.text.isNotEmpty()) start = verse.verseIndex // skip empty verses
            if (verse.verseIndex.verseIndex >= verseIndex.verseIndex) break
        }

        val verseIterator = verses.iterator()
        var verse: Verse? = null
        while (verseIterator.hasNext()) {
            val v = verseIterator.next()
            if (v.verseIndex == start) {
                verse = v
                break
            }
        }
        if (verse == null) throw IllegalStateException("Failed to find verse '$verseIndex' for translation '$currentTranslation'")

        // step 2: builds the parallel
        val parallel = Array(parallelTranslations.size) { StringBuilder() }
        val parallelBuilder: (index: Int, Verse.Text) -> Unit = { index, text ->
            with(parallel[index]) {
                if (isNotEmpty()) append(' ')
                append(text.text)
            }
        }
        verse.parallel.forEachIndexed(parallelBuilder)

        var followingEmptyVerseCount = 0
        while (verseIterator.hasNext()) {
            val v = verseIterator.next()
            if (v.text.text.isNotEmpty()) break
            v.parallel.forEachIndexed(parallelBuilder)
            followingEmptyVerseCount++
        }

        // step 3: constructs verse text items
        val verseTextItems = ArrayList<VerseTextItem>(parallelTranslations.size + 1)
        verseTextItems.add(VerseTextItem(
            verseIndex = verseIndex,
            followingEmptyVerseCount = followingEmptyVerseCount,
            verseText = verse.text,
            bookName = bibleReadingManager.readBookNames(verse.text.translationShortName)[verse.verseIndex.bookIndex]
        ))
        parallelTranslations.forEachIndexed { index, translation ->
            verseTextItems.add(VerseTextItem(
                verseIndex = verseIndex,
                followingEmptyVerseCount = followingEmptyVerseCount,
                verseText = Verse.Text(translation, parallel[index].toString()),
                bookName = bibleReadingManager.readBookNames(translation)[verse.verseIndex.bookIndex]
            ))
        }

        VerseDetailViewData(
            verseIndex = verseIndex,
            verseTextItems = verseTextItems,
            bookmarked = bookmarked.await(),
            highlightColor = highlightColor.await(),
            note = note.await()
        )
    }

    fun updateVerse(update: VerseUpdate) {
        emitViewAction(ViewAction.UpdateVerse(update))
    }

    fun saveBookmark(verseIndex: VerseIndex, toBeBookmarked: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (toBeBookmarked) {
                    bookmarkManager.save(Bookmark(verseIndex, timeProvider.currentTimeMillis))
                    emitViewAction(ViewAction.UpdateVerse(VerseUpdate(verseIndex, VerseUpdate.BOOKMARK_ADDED)))
                } else {
                    bookmarkManager.remove(verseIndex)
                    emitViewAction(ViewAction.UpdateVerse(VerseUpdate(verseIndex, VerseUpdate.BOOKMARK_REMOVED)))
                }
                emitViewAction(ViewAction.BookmarkSaved(toBeBookmarked))
            }.onFailure { e ->
                showError(ViewState.Error.BookmarkSavingError, e, "Failed to update bookmark")
            }
        }
    }

    fun saveHighlight(verseIndex: VerseIndex, @Highlight.Companion.AvailableColor color: Int) {
        viewModelScope.launch {
            runCatching {
                if (color == Highlight.COLOR_NONE) {
                    highlightManager.remove(verseIndex)
                } else {
                    highlightManager.save(Highlight(verseIndex, color, timeProvider.currentTimeMillis))
                }
                emitViewAction(ViewAction.UpdateVerse(VerseUpdate(verseIndex, VerseUpdate.HIGHLIGHT_UPDATED, color)))
                emitViewAction(ViewAction.HighlightSaved(color))
            }.onFailure { e ->
                showError(ViewState.Error.HighlightSavingError, e, "Failed to update highlight")
            }
        }
    }

    fun saveNote(verseIndex: VerseIndex, note: String) {
        viewModelScope.launch {
            runCatching {
                if (note.isEmpty()) {
                    noteManager.remove(verseIndex)
                    emitViewAction(ViewAction.UpdateVerse(VerseUpdate(verseIndex, VerseUpdate.NOTE_REMOVED)))
                } else {
                    noteManager.save(Note(verseIndex, note, timeProvider.currentTimeMillis))
                    emitViewAction(ViewAction.UpdateVerse(VerseUpdate(verseIndex, VerseUpdate.NOTE_ADDED)))
                }
                emitViewAction(ViewAction.NoteSaved(note))
            }.onFailure { e ->
                showError(ViewState.Error.NoteSavingError, e, "Failed to update note")
            }
        }
    }

    fun startTrackingReadingProgress() {
        readingProgressManager.startTracking()
    }

    fun stopTrackingReadingProgress() {
        readingProgressManager.stopTracking()
    }
}
