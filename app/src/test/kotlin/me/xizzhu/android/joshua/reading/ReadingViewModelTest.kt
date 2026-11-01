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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.xizzhu.android.joshua.core.BibleReadingManager
import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.CrossReferences
import me.xizzhu.android.joshua.core.CrossReferencesManager
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.ReadingProgressManager
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.SettingsManager
import me.xizzhu.android.joshua.core.StrongNumber
import me.xizzhu.android.joshua.core.StrongNumberManager
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
    private lateinit var crossReferencesManager: CrossReferencesManager
    private lateinit var readingProgressManager: ReadingProgressManager
    private lateinit var translationManager: TranslationManager
    private lateinit var bookmarkManager: VerseAnnotationManager<Bookmark>
    private lateinit var highlightManager: VerseAnnotationManager<Highlight>
    private lateinit var noteManager: VerseAnnotationManager<Note>
    private lateinit var strongNumberManager: StrongNumberManager
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
        crossReferencesManager = mockk()
        readingProgressManager = mockk()
        translationManager = mockk()
        every { translationManager.downloadedTranslations() } returns emptyFlow()
        bookmarkManager = mockk()
        highlightManager = mockk()
        noteManager = mockk()
        strongNumberManager = mockk()
        settingsManager = mockk()
        every { settingsManager.settings() } returns flowOf(Settings.DEFAULT)
        application = mockk()

        readingViewModel = createViewModel()
    }

    private fun createViewModel() = ReadingViewModel(
        bibleReadingManager, readingProgressManager, translationManager,
        bookmarkManager, highlightManager, noteManager,
        crossReferencesManager, strongNumberManager, settingsManager, timeProvider, application
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
                crossReferencesDownloadingState = ReadingViewModel.ViewState.DownloadingState.Idle,
                strongNumberDownloadingState = ReadingViewModel.ViewState.DownloadingState.Idle,
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

        assertEquals(listOf<ReadingViewModel.ViewAction>(ReadingViewModel.ViewAction.DismissPreview), actions)
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
    fun `test loadVerses with simple reading mode on`() = runTest {
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { bibleReadingManager.parallelTranslations() } returns flowOf(emptyList())
        coEvery { bibleReadingManager.readVerses(MockContents.kjvShortName, 0, 0) } returns MockContents.kjvVerses
        coEvery { bookmarkManager.read(0, 0) } returns emptyList()
        coEvery { highlightManager.read(0, 0) } returns emptyList()
        coEvery { noteManager.read(0, 0) } returns emptyList()
        every { settingsManager.settings() } returns flowOf(Settings.DEFAULT.copy(simpleReadingModeOn = true))

        val actions = collectViewActions()

        readingViewModel.loadVerses(0, 0)

        assertEquals(1, actions.size)
        with(actions[0] as ReadingViewModel.ViewAction.ShowVerses) {
            assertEquals(0, bookIndex)
            assertEquals(0, chapterIndex)
            assertEquals(MockContents.kjvVerses.size, items.size)
        }
    }

    @Test
    fun `test loadVerses with simple reading mode off`() = runTest {
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { bibleReadingManager.parallelTranslations() } returns flowOf(listOf(MockContents.cuvShortName))
        coEvery { bibleReadingManager.readVerses(MockContents.kjvShortName, listOf(MockContents.cuvShortName), 0, 0) } returns MockContents.kjvVersesWithCuvParallel
        coEvery { bookmarkManager.read(0, 0) } returns emptyList()
        coEvery { highlightManager.read(0, 0) } returns emptyList()
        coEvery { noteManager.read(0, 0) } returns emptyList()
        every { settingsManager.settings() } returns flowOf(Settings.DEFAULT.copy(simpleReadingModeOn = false))

        val actions = collectViewActions()

        readingViewModel.loadVerses(0, 0)

        assertEquals(1, actions.size)
        assertTrue(actions[0] is ReadingViewModel.ViewAction.ShowVerses)
    }

    @Test
    fun `test loadVerses with exception`() = runTest {
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { bibleReadingManager.parallelTranslations() } returns flowOf(emptyList())
        coEvery { bibleReadingManager.readVerses(MockContents.kjvShortName, 0, 0) } throws RuntimeException("random exception")
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
        coEvery { crossReferencesManager.readCrossReferences(verseIndex) } returns CrossReferences(verseIndex, MockContents.crossReferences.getValue(verseIndex))
        coEvery { strongNumberManager.readStrongNumber(verseIndex) } returns MockContents.strongNumber.getValue(verseIndex)
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.kjvShortName)
        every { translationManager.downloadedTranslations() } returns flowOf(listOf(
                MockContents.kjvDownloadedTranslationInfo, MockContents.cuvDownloadedTranslationInfo, MockContents.bbeDownloadedTranslationInfo
        ))
        coEvery {
            bibleReadingManager.readVerses(MockContents.kjvShortName, listOf(VerseIndex(18, 133, 2), VerseIndex(18, 101, 24), VerseIndex(61, 0, 0)))
        } returns mapOf(
                VerseIndex(18, 133, 2) to Verse(VerseIndex(18, 133, 2), Verse.Text(MockContents.kjvShortName, "The LORD that made heaven and earth bless thee out of Zion."), emptyList())
        )
        coEvery {
            bibleReadingManager.readVerses(
                    MockContents.kjvShortName, listOf(MockContents.bbeShortName, MockContents.cuvShortName),
                    verseIndex.bookIndex, verseIndex.chapterIndex
            )
        } returns MockContents.kjvVersesWithBbeCuvParallel
        coEvery { bibleReadingManager.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames
        coEvery { bibleReadingManager.readBookNames(MockContents.bbeShortName) } returns MockContents.bbeBookNames
        coEvery { bibleReadingManager.readBookNames(MockContents.cuvShortName) } returns MockContents.cuvBookNames

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
        detail.crossReferencedItems.forEachIndexed { index, item ->
            when (index) {
                0 -> {
                    assertEquals(VerseIndex(18, 133, 2), item.verseIndex)
                    assertEquals(Verse.Text("KJV", "The LORD that made heaven and earth bless thee out of Zion."), item.verseText)
                }
                else -> fail()
            }
        }
        assertEquals(
                listOf(
                        StrongNumber("H7225", "beginning, chief(-est), first(-fruits, part, time), principal thing."),
                        StrongNumber("H1254", "choose, create (creator), cut down, dispatch, do, make (fat)."),
                        StrongNumber("H430", "angels, [idiom] exceeding, God (gods) (-dess, -ly), [idiom] (very) great, judges, [idiom] mighty."),
                        StrongNumber("H853", "(as such unrepresented in English)."),
                        StrongNumber("H8064", "mantle"),
                        StrongNumber("H776", "[idiom] common, country, earth, field, ground, land, [idiom] natins, way, [phrase] wilderness, world.")
                ),
                detail.strongNumberItems.map { it.strongNumber }
        )
    }

    @Test
    fun `test loadVerseDetail with empty verses`() = runTest {
        val verseIndex = VerseIndex(0, 0, 0)
        coEvery { bookmarkManager.read(verseIndex) } returns Bookmark(verseIndex, 0L)
        coEvery { highlightManager.read(verseIndex) } returns Highlight(verseIndex, Highlight.COLOR_PURPLE, 0L)
        coEvery { noteManager.read(verseIndex) } returns Note(verseIndex, "just a note", 0L)
        coEvery { crossReferencesManager.readCrossReferences(verseIndex) } returns CrossReferences(verseIndex, emptyList())
        coEvery { strongNumberManager.readStrongNumber(verseIndex) } returns MockContents.strongNumber.getValue(verseIndex)
        every { bibleReadingManager.currentTranslation() } returns flowOf(MockContents.msgShortName)
        every { translationManager.downloadedTranslations() } returns flowOf(listOf(
                MockContents.kjvDownloadedTranslationInfo, MockContents.msgDownloadedTranslationInfo
        ))
        coEvery { bibleReadingManager.readVerses(MockContents.msgShortName, emptyList()) } returns emptyMap()
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
        assertEquals(
                listOf(
                        StrongNumber("H7225", "beginning, chief(-est), first(-fruits, part, time), principal thing."),
                        StrongNumber("H1254", "choose, create (creator), cut down, dispatch, do, make (fat)."),
                        StrongNumber("H430", "angels, [idiom] exceeding, God (gods) (-dess, -ly), [idiom] (very) great, judges, [idiom] mighty."),
                        StrongNumber("H853", "(as such unrepresented in English)."),
                        StrongNumber("H8064", "mantle"),
                        StrongNumber("H776", "[idiom] common, country, earth, field, ground, land, [idiom] natins, way, [phrase] wilderness, world.")
                ),
                detail.strongNumberItems.map { it.strongNumber }
        )
    }

    @Test
    fun `test loadVerseDetail with exception`() = runTest {
        val verseIndex = VerseIndex(0, 0, 0)
        coEvery { bookmarkManager.read(verseIndex) } throws RuntimeException("random exception")
        coEvery { highlightManager.read(verseIndex) } returns Highlight(verseIndex, Highlight.COLOR_PURPLE, 0L)
        coEvery { noteManager.read(verseIndex) } returns Note(verseIndex, "just a note", 0L)
        coEvery { crossReferencesManager.readCrossReferences(verseIndex) } returns CrossReferences(verseIndex, emptyList())
        coEvery { strongNumberManager.readStrongNumber(verseIndex) } returns emptyList()
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

    @Test
    fun `test downloadStrongNumber`() = runTest {
        every { strongNumberManager.download() } returns flowOf(0, 20, 99, 100)

        readingViewModel.downloadStrongNumber()

        assertEquals(
            ReadingViewModel.ViewState.DownloadingState.Completed(successful = true),
            readingViewModel.viewState().first().strongNumberDownloadingState
        )

        readingViewModel.markStrongNumberDownloadingStateAsIdle()
        assertEquals(ReadingViewModel.ViewState.DownloadingState.Idle, readingViewModel.viewState().first().strongNumberDownloadingState)
    }

    @Test
    fun `test downloadStrongNumber installing`() = runTest {
        every { strongNumberManager.download() } returns flowOf(0, 20, 99)

        readingViewModel.downloadStrongNumber()

        assertEquals(
            ReadingViewModel.ViewState.DownloadingState.Installing,
            readingViewModel.viewState().first().strongNumberDownloadingState
        )
    }

    @Test
    fun `test downloadStrongNumber cancelled`() = runTest {
        every { strongNumberManager.download() } returns flowOf(0, -1)

        readingViewModel.downloadStrongNumber()

        val viewState = readingViewModel.viewState().first()
        assertEquals(ReadingViewModel.ViewState.DownloadingState.Idle, viewState.strongNumberDownloadingState)
        assertNull(viewState.error)
    }

    @Test
    fun `test downloadStrongNumber with exception`() = runTest {
        every { strongNumberManager.download() } returns flow { throw RuntimeException("random exception") }

        readingViewModel.downloadStrongNumber()

        val viewState = readingViewModel.viewState().first()
        assertEquals(ReadingViewModel.ViewState.DownloadingState.Idle, viewState.strongNumberDownloadingState)
        assertEquals(ReadingViewModel.ViewState.Error.StrongNumberDownloadingError, viewState.error)
    }

    @Test
    fun `test downloadCrossReferences`() = runTest {
        every { crossReferencesManager.download() } returns flowOf(0, 50, 100)

        readingViewModel.downloadCrossReferences()

        assertEquals(
            ReadingViewModel.ViewState.DownloadingState.Completed(successful = true),
            readingViewModel.viewState().first().crossReferencesDownloadingState
        )
    }

    @Test
    fun `test downloadCrossReferences with exception`() = runTest {
        every { crossReferencesManager.download() } returns flow { throw RuntimeException("random exception") }

        readingViewModel.downloadCrossReferences()

        val viewState = readingViewModel.viewState().first()
        assertEquals(ReadingViewModel.ViewState.DownloadingState.Idle, viewState.crossReferencesDownloadingState)
        assertEquals(ReadingViewModel.ViewState.Error.CrossReferencesDownloadingError, viewState.error)
    }

    @Test
    fun `test loadStrongNumbers`() = runTest {
        coEvery { strongNumberManager.readStrongNumber(VerseIndex(0, 0, 0)) } returns MockContents.strongNumber.getValue(VerseIndex(0, 0, 0))
        val actions = collectViewActions()

        readingViewModel.loadStrongNumbers(VerseIndex(0, 0, 0))

        assertEquals(6, (actions.single() as ReadingViewModel.ViewAction.ShowStrongNumbers).items.size)
    }
}
