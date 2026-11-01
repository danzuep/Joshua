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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.xizzhu.android.joshua.core.BibleReadingManager
import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.HEADING
import me.xizzhu.android.joshua.core.NOTE_MARK
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.ReadingProgressManager
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.SettingsManager
import me.xizzhu.android.joshua.core.TranslationManager
import me.xizzhu.android.joshua.core.Verse
import me.xizzhu.android.joshua.core.VerseAnnotationManager
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.reading.detail.VerseDetailViewLayout
import me.xizzhu.android.joshua.tests.BaseUnitTest
import me.xizzhu.android.joshua.tests.MockContents
import me.xizzhu.android.joshua.tests.TestTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ReadingViewModelTest : BaseUnitTest() {
    private lateinit var bibleReadingManager: BibleReadingManager
    private lateinit var readingProgressManager: ReadingProgressManager
    private lateinit var translationManager: TranslationManager
    private lateinit var bookmarkManager: VerseAnnotationManager<Bookmark>
    private lateinit var highlightManager: VerseAnnotationManager<Highlight>
    private lateinit var noteManager: VerseAnnotationManager<Note>
    private lateinit var settingsManager: SettingsManager
    private lateinit var application: Application
    private val timeProvider = TestTimeProvider()

    private lateinit var readingViewModel: ReadingViewModel

    override fun setup() {
        super.setup()

        bibleReadingManager = mockk()
        every { bibleReadingManager.currentTranslation() } returns emptyFlow()
        every { bibleReadingManager.parallelTranslations() } returns emptyFlow()
        every { bibleReadingManager.currentVerseIndex() } returns emptyFlow()
        readingProgressManager = mockk()
        translationManager = mockk()
        every { translationManager.downloadedTranslations() } returns emptyFlow()
        bookmarkManager = mockk()
        highlightManager = mockk()
        noteManager = mockk()
        settingsManager = mockk()
        every { settingsManager.settings() } returns flowOf(Settings.DEFAULT)
        application = mockk()

        readingViewModel = createViewModel()
    }

    private fun createViewModel() = ReadingViewModel(
        bibleReadingManager, readingProgressManager, translationManager,
        bookmarkManager, highlightManager, noteManager,
        settingsManager, timeProvider, application
    )

    private fun collectViewActions(): List<ReadingViewModel.ViewAction> =
        mutableListOf<ReadingViewModel.ViewAction>().also { actions ->
            readingViewModel.viewAction().onEach { actions.add(it) }.launchIn(testScope)
        }

    @Test
    fun `test currentReadingStatus`() = runTest {
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { bibleReadingManager.parallelTranslations() } returns flowOf(listOf(MockContents.cuvShortName))
        every { bibleReadingManager.currentVerseIndex() } returns flowOf(VerseIndex(1, 2, 3))
        coEvery { bibleReadingManager.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames

        readingViewModel = createViewModel()

        val actual = readingViewModel.viewState().first().currentReadingStatus
        assertEquals(
            ReadingViewModel.ViewState.CurrentReadingStatus(
                currentTranslation = MockContents.kjvShortName,
                parallelTranslations = listOf(MockContents.cuvShortName),
                currentVerseIndex = VerseIndex(1, 2, 3),
                bookNames = MockContents.kjvBookNames,
            ),
            actual
        )
    }

    @Test
    fun `test initial view state`() = runTest {
        assertEquals(
            ReadingViewModel.ViewState(
                settings = Settings.DEFAULT,
                currentReadingStatus = null,
                error = null,
            ),
            readingViewModel.viewState().first()
        )
    }

    @Test
    fun `test settings`() = runTest {
        val settings = Settings.DEFAULT.copy(keepScreenOn = false, hideSearchButton = true)
        every { settingsManager.settings() } returns flowOf(settings)

        readingViewModel = createViewModel()

        assertEquals(settings, readingViewModel.viewState().first().settings)
    }

    @Test
    fun `test checkDownloadedTranslation with downloaded translation`() = runTest {
        every { translationManager.downloadedTranslations() } returns flowOf(listOf(MockContents.kjvDownloadedTranslationInfo))

        readingViewModel.checkDownloadedTranslation()

        assertNull(readingViewModel.viewState().first().error)
    }

    @Test
    fun `test checkDownloadedTranslation without downloaded translation`() = runTest {
        every { translationManager.downloadedTranslations() } returns flowOf(emptyList())

        readingViewModel.checkDownloadedTranslation()

        assertEquals(ReadingViewModel.ViewState.Error.NoTranslationDownloadedError, readingViewModel.viewState().first().error)

        readingViewModel.markErrorAsShown(ReadingViewModel.ViewState.Error.NoTranslationDownloadedError)
        assertNull(readingViewModel.viewState().first().error)
    }

    @Test
    fun `test markErrorAsShown with different error`() = runTest {
        every { translationManager.downloadedTranslations() } returns flowOf(emptyList())
        readingViewModel.checkDownloadedTranslation()

        readingViewModel.markErrorAsShown(ReadingViewModel.ViewState.Error.BookmarkSavingError)

        assertEquals(ReadingViewModel.ViewState.Error.NoTranslationDownloadedError, readingViewModel.viewState().first().error)
    }

    @Test
    fun `test selectTranslation`() = runTest {
        coEvery { bibleReadingManager.saveCurrentTranslation("KJV") } returns Unit
        coEvery { bibleReadingManager.removeParallelTranslation("KJV") } returns Unit

        readingViewModel.selectTranslation("KJV")

        coVerify(exactly = 1) { bibleReadingManager.saveCurrentTranslation("KJV") }
        coVerify(exactly = 1) { bibleReadingManager.removeParallelTranslation("KJV") }
        assertNull(readingViewModel.viewState().first().error)
    }

    @Test
    fun `test selectTranslation with exception`() = runTest {
        coEvery { bibleReadingManager.saveCurrentTranslation("KJV") } throws RuntimeException("random exception")

        readingViewModel.selectTranslation("KJV")

        assertEquals(ReadingViewModel.ViewState.Error.TranslationSelectionError("KJV"), readingViewModel.viewState().first().error)
    }

    @Test
    fun `test selectChapter with exception`() = runTest {
        coEvery { bibleReadingManager.saveCurrentVerseIndex(VerseIndex(1, 2, 0)) } throws RuntimeException("random exception")

        readingViewModel.selectChapter(1, 2)

        assertEquals(ReadingViewModel.ViewState.Error.ChapterSelectionError(1, 2), readingViewModel.viewState().first().error)
    }

    @Test
    fun `test selectVerse`() = runTest {
        coEvery { bibleReadingManager.saveCurrentVerseIndex(VerseIndex(1, 2, 3)) } returns Unit
        val actions = collectViewActions()

        readingViewModel.selectVerse(VerseIndex(1, 2, 3))

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `test selectVerse with exception`() = runTest {
        coEvery { bibleReadingManager.saveCurrentVerseIndex(VerseIndex(1, 2, 3)) } throws RuntimeException("random exception")

        readingViewModel.selectVerse(VerseIndex(1, 2, 3))

        assertEquals(ReadingViewModel.ViewState.Error.VerseSelectionError(VerseIndex(1, 2, 3)), readingViewModel.viewState().first().error)
    }

    @Test
    fun `test updateCurrentVerse with exception does not set error`() = runTest {
        coEvery { bibleReadingManager.saveCurrentVerseIndex(VerseIndex(1, 2, 3)) } throws RuntimeException("random exception")

        readingViewModel.updateCurrentVerse(VerseIndex(1, 2, 3))

        assertNull(readingViewModel.viewState().first().error)
    }

    @Test
    fun `test loadVerses`() = runTest {
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        coEvery { bibleReadingManager.readStructuredVerses(MockContents.kjvShortName, 0, 0) } returns MockContents.kjvVerses
        coEvery { bookmarkManager.read(0, 0) } returns emptyList()
        coEvery { highlightManager.read(0, 0) } returns emptyList()
        coEvery { noteManager.read(0, 0) } returns emptyList()

        val actions = collectViewActions()

        readingViewModel.loadVerses(0, 0)

        assertEquals(1, actions.size)
        with(actions[0] as ReadingViewModel.ViewAction.ShowVerses) {
            assertEquals(0, bookIndex)
            assertEquals(0, chapterIndex)
            assertEquals(1, items.size)
        }
    }

    @Test
    fun `test loadVerses with exception`() = runTest {
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        coEvery { bibleReadingManager.readStructuredVerses(MockContents.kjvShortName, 0, 0) } throws RuntimeException("random exception")
        coEvery { bookmarkManager.read(0, 0) } returns emptyList()
        coEvery { highlightManager.read(0, 0) } returns emptyList()
        coEvery { noteManager.read(0, 0) } returns emptyList()

        readingViewModel.loadVerses(0, 0)

        assertEquals(ReadingViewModel.ViewState.Error.VersesLoadingError(0, 0), readingViewModel.viewState().first().error)
    }

    @Test
    fun `test loadVerseDetail`() = runTest {
        val verseIndex = VerseIndex(0, 0, 0)
        coEvery { bookmarkManager.read(verseIndex) } returns Bookmark(verseIndex, 12345L)
        coEvery { highlightManager.read(verseIndex) } returns Highlight(verseIndex, Highlight.COLOR_PURPLE, 12345L)
        coEvery { noteManager.read(verseIndex) } returns Note(verseIndex, "just a note", 12345L)
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { translationManager.downloadedTranslations() } returns flowOf(listOf(
                MockContents.kjvDownloadedTranslationInfo, MockContents.cuvDownloadedTranslationInfo, MockContents.bbeDownloadedTranslationInfo
        ))
        coEvery {
            bibleReadingManager.readVerses(
                    MockContents.kjvShortName, listOf(MockContents.bbeShortName, MockContents.cuvShortName),
                    verseIndex.bookIndex, verseIndex.chapterIndex
            )
        } returns MockContents.kjvVersesWithBbeCuvParallel
        coEvery {
            bibleReadingManager.readStructuredVerses(MockContents.kjvShortName, verseIndex.bookIndex, verseIndex.chapterIndex)
        } returns listOf(
                Verse(verseIndex, Verse.Text(MockContents.kjvShortName,
                        "${HEADING}Creation${HEADING}In the beginning${NOTE_MARK}fa note${NOTE_MARK} God created${NOTE_MARK}x1:1 Gen 1:1${NOTE_MARK} the heaven"), emptyList())
        )
        coEvery { bibleReadingManager.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames
        coEvery { bibleReadingManager.readBookNames(MockContents.bbeShortName) } returns MockContents.bbeBookNames
        coEvery { bibleReadingManager.readBookNames(MockContents.cuvShortName) } returns MockContents.cuvBookNames

        val actions = collectViewActions()

        readingViewModel.loadVerseDetail(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_NOTE)

        assertEquals(2, actions.size)
        val shown = actions[0] as ReadingViewModel.ViewAction.ShowVerseDetail
        assertEquals(VerseDetailViewLayout.VERSE_DETAIL_NOTE, shown.content)
        val detail = shown.verseDetail
        assertEquals(listOf("a note"), detail.footnotes)
        assertEquals(listOf("1:1 Gen 1:1"), detail.crossReferences)
        assertEquals(listOf("Creation"), detail.headings)
        assertEquals(3, detail.downloadedTranslationCount)
        assertTrue(detail.hasParallel)
        assertTrue(detail.hasParatext)
        with(actions[1] as ReadingViewModel.ViewAction.UpdateVerse) {
            assertEquals(VerseIndex(0, 0, 0), update.verseIndex)
            assertEquals(VerseUpdate.VERSE_SELECTED, update.operation)
        }
        assertEquals(VerseIndex(0, 0, 0), detail.verseIndex)
        assertEquals(3, detail.verseTextItems.size)
        detail.verseTextItems.forEachIndexed { index, item ->
            assertEquals(VerseIndex(0, 0, 0), item.verseIndex)
            assertEquals(
                    when (index) {
                        0 -> Verse.Text("KJV", "In the beginning God created the heaven and the earth.")
                        1 -> Verse.Text("BBE", "At the first God made the heaven and the earth.")
                        2 -> Verse.Text("中文和合本", "起初神创造天地。")
                        else -> fail()
                    },
                    item.verseText
            )
        }
        assertTrue(detail.bookmarked)
        assertEquals(Highlight.COLOR_PURPLE, detail.highlightColor)
        assertEquals("just a note", detail.note)
    }

    @Test
    fun `test loadVerseDetail with empty verses`() = runTest {
        val verseIndex = VerseIndex(0, 0, 0)
        coEvery { bookmarkManager.read(verseIndex) } returns Bookmark(verseIndex, 0L)
        coEvery { highlightManager.read(verseIndex) } returns Highlight(verseIndex, Highlight.COLOR_PURPLE, 0L)
        coEvery { noteManager.read(verseIndex) } returns Note(verseIndex, "just a note", 0L)
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.msgShortName)
        every { translationManager.downloadedTranslations() } returns flowOf(listOf(
                MockContents.kjvDownloadedTranslationInfo, MockContents.msgDownloadedTranslationInfo
        ))
        coEvery { bibleReadingManager.readVerses(MockContents.msgShortName, emptyList()) } returns emptyMap()
        coEvery { bibleReadingManager.readStructuredVerses(MockContents.msgShortName, 0, 0) } returns emptyList()
        coEvery {
            bibleReadingManager.readVerses(
                    MockContents.msgShortName, listOf(MockContents.kjvShortName), verseIndex.bookIndex, verseIndex.chapterIndex
            )
        } returns MockContents.msgVersesWithKjvParallel
        coEvery { bibleReadingManager.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames
        coEvery { bibleReadingManager.readBookNames(MockContents.msgShortName) } returns MockContents.msgBookNames

        val actions = collectViewActions()

        readingViewModel.loadVerseDetail(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_NOTE)

        assertEquals(2, actions.size)
        val shown = actions[0] as ReadingViewModel.ViewAction.ShowVerseDetail
        assertEquals(VerseDetailViewLayout.VERSE_DETAIL_NOTE, shown.content)
        val detail = shown.verseDetail
        with(actions[1] as ReadingViewModel.ViewAction.UpdateVerse) {
            assertEquals(VerseIndex(0, 0, 0), update.verseIndex)
            assertEquals(VerseUpdate.VERSE_SELECTED, update.operation)
        }
        assertEquals(VerseIndex(0, 0, 0), detail.verseIndex)
        assertEquals(2, detail.verseTextItems.size)
        detail.verseTextItems.forEachIndexed { index, item ->
            assertEquals(VerseIndex(0, 0, 0), item.verseIndex)
            assertEquals(
                    when (index) {
                        0 -> Verse.Text("MSG", "First this: God created the Heavens and Earth—all you see, all you don't see. Earth was a soup of nothingness, a bottomless emptiness, an inky blackness. God's Spirit brooded like a bird above the watery abyss.")
                        1 -> Verse.Text("KJV", "In the beginning God created the heaven and the earth. And the earth was without form, and void; and darkness was upon the face of the deep. And the Spirit of God moved upon the face of the waters.")
                        else -> fail()
                    },
                    item.verseText
            )
        }
        assertFalse(detail.bookmarked)
        assertEquals(Highlight.COLOR_NONE, detail.highlightColor)
        assertTrue(detail.note.isEmpty())
    }

    @Test
    fun `test loadVerseDetail with a single downloaded translation`() = runTest {
        val verseIndex = VerseIndex(0, 0, 0)
        coEvery { bookmarkManager.read(verseIndex) } returns Bookmark(verseIndex, 0L)
        coEvery { highlightManager.read(verseIndex) } returns Highlight(verseIndex, Highlight.COLOR_NONE, 0L)
        coEvery { noteManager.read(verseIndex) } returns Note(verseIndex, "", 0L)
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { translationManager.downloadedTranslations() } returns flowOf(listOf(MockContents.kjvDownloadedTranslationInfo))
        coEvery {
            bibleReadingManager.readVerses(MockContents.kjvShortName, emptyList(), verseIndex.bookIndex, verseIndex.chapterIndex)
        } returns listOf(Verse(verseIndex, Verse.Text(MockContents.kjvShortName, "In the beginning"), emptyList()))
        coEvery {
            bibleReadingManager.readStructuredVerses(MockContents.kjvShortName, verseIndex.bookIndex, verseIndex.chapterIndex)
        } returns listOf(Verse(verseIndex, Verse.Text(MockContents.kjvShortName, "In the beginning"), emptyList()))
        coEvery { bibleReadingManager.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames

        val actions = collectViewActions()

        readingViewModel.loadVerseDetail(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_VERSES)

        val detail = (actions[0] as ReadingViewModel.ViewAction.ShowVerseDetail).verseDetail
        assertEquals(1, detail.downloadedTranslationCount)
        assertFalse(detail.hasParallel)
        assertFalse(detail.hasParatext)
        assertEquals(1, detail.verseTextItems.size)
    }

    @Test
    fun `test loadVerseDetail with exception`() = runTest {
        val verseIndex = VerseIndex(0, 0, 0)
        coEvery { bookmarkManager.read(verseIndex) } throws RuntimeException("random exception")
        coEvery { highlightManager.read(verseIndex) } returns Highlight(verseIndex, Highlight.COLOR_PURPLE, 0L)
        coEvery { noteManager.read(verseIndex) } returns Note(verseIndex, "just a note", 0L)
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        coEvery { bibleReadingManager.readVerses(MockContents.kjvShortName, emptyList()) } returns emptyMap()

        readingViewModel.loadVerseDetail(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_VERSES)

        assertEquals(
            ReadingViewModel.ViewState.Error.VerseDetailLoadingError(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_VERSES),
            readingViewModel.viewState().first().error
        )
    }

    @Test
    fun `test saveBookmark`() = runTest {
        timeProvider.currentTimeMillis = 1L
        coEvery { bookmarkManager.save(Bookmark(VerseIndex(1, 2, 3), 1L)) } returns Unit
        coEvery { bookmarkManager.remove(VerseIndex(1, 2, 3)) } returns Unit
        val actions = collectViewActions()

        readingViewModel.saveBookmark(VerseIndex(1, 2, 3), toBeBookmarked = true)
        readingViewModel.saveBookmark(VerseIndex(1, 2, 3), toBeBookmarked = false)

        assertEquals(4, actions.size)
        assertVerseUpdate(actions[0], VerseUpdate.BOOKMARK_ADDED, null)
        assertEquals(ReadingViewModel.ViewAction.BookmarkSaved(true), actions[1])
        assertVerseUpdate(actions[2], VerseUpdate.BOOKMARK_REMOVED, null)
        assertEquals(ReadingViewModel.ViewAction.BookmarkSaved(false), actions[3])
        assertNull(readingViewModel.viewState().first().error)
    }

    @Test
    fun `test saveBookmark with exception`() = runTest {
        timeProvider.currentTimeMillis = 1L
        coEvery { bookmarkManager.save(Bookmark(VerseIndex(1, 2, 3), 1L)) } throws RuntimeException("random exception")
        val actions = collectViewActions()

        readingViewModel.saveBookmark(VerseIndex(1, 2, 3), toBeBookmarked = true)

        assertTrue(actions.isEmpty())
        assertEquals(ReadingViewModel.ViewState.Error.BookmarkSavingError, readingViewModel.viewState().first().error)
    }

    @Test
    fun `test saveHighlight`() = runTest {
        timeProvider.currentTimeMillis = 1L
        coEvery { highlightManager.save(Highlight(VerseIndex(1, 2, 3), Highlight.COLOR_BLUE, 1L)) } returns Unit
        coEvery { highlightManager.save(Highlight(VerseIndex(1, 2, 3), Highlight.COLOR_PURPLE, 1L)) } returns Unit
        coEvery { highlightManager.remove(VerseIndex(1, 2, 3)) } returns Unit
        val actions = collectViewActions()

        readingViewModel.saveHighlight(VerseIndex(1, 2, 3), Highlight.COLOR_BLUE)
        readingViewModel.saveHighlight(VerseIndex(1, 2, 3), Highlight.COLOR_PURPLE)
        readingViewModel.saveHighlight(VerseIndex(1, 2, 3), Highlight.COLOR_NONE)

        assertEquals(6, actions.size)
        assertVerseUpdate(actions[0], VerseUpdate.HIGHLIGHT_UPDATED, Highlight.COLOR_BLUE)
        assertEquals(ReadingViewModel.ViewAction.HighlightSaved(Highlight.COLOR_BLUE), actions[1])
        assertVerseUpdate(actions[2], VerseUpdate.HIGHLIGHT_UPDATED, Highlight.COLOR_PURPLE)
        assertEquals(ReadingViewModel.ViewAction.HighlightSaved(Highlight.COLOR_PURPLE), actions[3])
        assertVerseUpdate(actions[4], VerseUpdate.HIGHLIGHT_UPDATED, Highlight.COLOR_NONE)
        assertEquals(ReadingViewModel.ViewAction.HighlightSaved(Highlight.COLOR_NONE), actions[5])
    }

    @Test
    fun `test saveHighlight with exception`() = runTest {
        coEvery { highlightManager.remove(VerseIndex(1, 2, 3)) } throws RuntimeException("random exception")

        readingViewModel.saveHighlight(VerseIndex(1, 2, 3), Highlight.COLOR_NONE)

        assertEquals(ReadingViewModel.ViewState.Error.HighlightSavingError, readingViewModel.viewState().first().error)
    }

    @Test
    fun `test saveNote`() = runTest {
        timeProvider.currentTimeMillis = 1L
        coEvery { noteManager.save(Note(VerseIndex(1, 2, 3), "note", 1L)) } returns Unit
        coEvery { noteManager.save(Note(VerseIndex(1, 2, 3), "updated note", 1L)) } returns Unit
        coEvery { noteManager.remove(VerseIndex(1, 2, 3)) } returns Unit
        val actions = collectViewActions()

        readingViewModel.saveNote(VerseIndex(1, 2, 3), "note")
        readingViewModel.saveNote(VerseIndex(1, 2, 3), "updated note")
        readingViewModel.saveNote(VerseIndex(1, 2, 3), "")

        assertEquals(6, actions.size)
        assertVerseUpdate(actions[0], VerseUpdate.NOTE_ADDED, null)
        assertEquals(ReadingViewModel.ViewAction.NoteSaved("note"), actions[1])
        assertVerseUpdate(actions[2], VerseUpdate.NOTE_ADDED, null)
        assertEquals(ReadingViewModel.ViewAction.NoteSaved("updated note"), actions[3])
        assertVerseUpdate(actions[4], VerseUpdate.NOTE_REMOVED, null)
        assertEquals(ReadingViewModel.ViewAction.NoteSaved(""), actions[5])
    }

    @Test
    fun `test saveNote with exception`() = runTest {
        coEvery { noteManager.remove(VerseIndex(1, 2, 3)) } throws RuntimeException("random exception")

        readingViewModel.saveNote(VerseIndex(1, 2, 3), "")

        assertEquals(ReadingViewModel.ViewState.Error.NoteSavingError, readingViewModel.viewState().first().error)
    }

    private fun assertVerseUpdate(action: ReadingViewModel.ViewAction, operation: Int, data: Any?) {
        with((action as ReadingViewModel.ViewAction.UpdateVerse).update) {
            assertEquals(VerseIndex(1, 2, 3), verseIndex)
            assertEquals(operation, this.operation)
            assertEquals(data, this.data)
        }
    }

}
