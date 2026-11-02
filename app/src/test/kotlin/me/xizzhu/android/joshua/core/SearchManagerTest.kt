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

package me.xizzhu.android.joshua.core

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.xizzhu.android.joshua.core.repository.BibleReadingRepository
import me.xizzhu.android.joshua.core.repository.VerseAnnotationRepository
import me.xizzhu.android.joshua.core.repository.remote.BOOK_CODES
import me.xizzhu.android.joshua.tests.BaseUnitTest
import me.xizzhu.android.joshua.tests.MockContents
import kotlin.test.*

class SearchManagerTest : BaseUnitTest() {
    private lateinit var bibleReadingRepository: BibleReadingRepository
    private lateinit var bookmarkRepository: VerseAnnotationRepository<Bookmark>
    private lateinit var highlightRepository: VerseAnnotationRepository<Highlight>
    private lateinit var noteRepository: VerseAnnotationRepository<Note>
    private lateinit var searchManager: SearchManager

    @BeforeTest
    override fun setup() {
        super.setup()

        bibleReadingRepository = mockk()
        every { bibleReadingRepository.currentTranslation } returns flowOf(MockContents.kjvShortName)
        coEvery { bibleReadingRepository.readBookNames(any()) } returns MockContents.kjvBookNames
        coEvery { bibleReadingRepository.readBookShortNames(any()) } returns MockContents.kjvBookShortNames
        coEvery { bibleReadingRepository.readVerses(any(), any()) } returns emptyMap()
        coEvery { bibleReadingRepository.search(any()) } returns emptyList()

        bookmarkRepository = mockk()
        coEvery { bookmarkRepository.read(Constants.SORT_BY_BOOK) } returns emptyList()

        highlightRepository = mockk()
        coEvery { highlightRepository.read(Constants.SORT_BY_BOOK) } returns emptyList()

        noteRepository = mockk()
        coEvery { noteRepository.search(any()) } returns emptyList()

        searchManager = SearchManager(bibleReadingRepository, bookmarkRepository, highlightRepository, noteRepository)
    }

    @Test
    fun `test search() with verses from New Testament only`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 0), VerseIndex(0, 0, 1)))
        } returns mapOf(Pair(VerseIndex(0, 0, 0), MockContents.kjvVerses[0]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "query", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])

        searchManager.saveConfiguration(SearchConfiguration(false, true, false, false, false))
        val actual = searchManager.search("query")
        assertTrue(actual.verses.isEmpty())
        assertTrue(actual.bookmarks.isEmpty())
        assertTrue(actual.highlights.isEmpty())
        assertTrue(actual.notes.isEmpty())
    }

    @Test
    fun `test search() with verses and bookmarks`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 0), VerseIndex(0, 0, 1)))
        } returns mapOf(Pair(VerseIndex(0, 0, 0), MockContents.kjvVerses[0]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "God", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery {
            bookmarkRepository.read(Constants.SORT_BY_BOOK)
        } returns listOf(Bookmark(VerseIndex(0, 0, 0), 12345L), Bookmark(VerseIndex(0, 0, 1), 12345L))

        val actual = searchManager.search("God")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertEquals(listOf(Pair(Bookmark(VerseIndex(0, 0, 0), 12345L), MockContents.kjvVerses[0])), actual.bookmarks)
        assertTrue(actual.highlights.isEmpty())
        assertTrue(actual.notes.isEmpty())
    }

    @Test
    fun `test search() with verses excluding bookmarks`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 0), VerseIndex(0, 0, 1)))
        } returns mapOf(Pair(VerseIndex(0, 0, 0), MockContents.kjvVerses[0]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "God", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery {
            bookmarkRepository.read(Constants.SORT_BY_BOOK)
        } returns listOf(Bookmark(VerseIndex(0, 0, 0), 12345L), Bookmark(VerseIndex(0, 0, 1), 12345L))

        searchManager.saveConfiguration(SearchConfiguration(true, true, false, true, true))
        val actual = searchManager.search("God")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertTrue(actual.bookmarks.isEmpty())
        assertTrue(actual.highlights.isEmpty())
        assertTrue(actual.notes.isEmpty())
    }

    @Test
    fun `test search() with verses and highlights`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 0), VerseIndex(0, 0, 1)))
        } returns mapOf(Pair(VerseIndex(0, 0, 0), MockContents.kjvVerses[0]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "God", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery {
            highlightRepository.read(Constants.SORT_BY_BOOK)
        } returns listOf(Highlight(VerseIndex(0, 0, 0), Highlight.COLOR_PINK, 12345L), Highlight(VerseIndex(0, 0, 1), Highlight.COLOR_BLUE, 12345L))

        val actual = searchManager.search("God")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertTrue(actual.bookmarks.isEmpty())
        assertEquals(listOf(Pair(Highlight(VerseIndex(0, 0, 0), Highlight.COLOR_PINK, 12345L), MockContents.kjvVerses[0])), actual.highlights)
        assertTrue(actual.notes.isEmpty())
    }

    @Test
    fun `test search() with verses excluding highlights`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 0), VerseIndex(0, 0, 1)))
        } returns mapOf(Pair(VerseIndex(0, 0, 0), MockContents.kjvVerses[0]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "God", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery {
            highlightRepository.read(Constants.SORT_BY_BOOK)
        } returns listOf(Highlight(VerseIndex(0, 0, 0), Highlight.COLOR_PINK, 12345L), Highlight(VerseIndex(0, 0, 1), Highlight.COLOR_BLUE, 12345L))

        searchManager.saveConfiguration(SearchConfiguration(true, true, true, false, true))
        val actual = searchManager.search("God")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertTrue(actual.bookmarks.isEmpty())
        assertTrue(actual.highlights.isEmpty())
        assertTrue(actual.notes.isEmpty())
    }

    @Test
    fun `test search() with verses and notes`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(
                    MockContents.kjvShortName,
                    listOf(VerseIndex(0, 0, 9), VerseIndex(1, 2, 3))
            )
        } returns mapOf(Pair(VerseIndex(0, 0, 9), MockContents.kjvVerses[9]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "query", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery { noteRepository.search("query") } returns listOf(
                Note(VerseIndex(0, 0, 9), "just a note", 12345L),
                // no verse is available for this note, should be ignored
                // https://github.com/xizzhu/Joshua/issues/153
                Note(VerseIndex(1, 2, 3), "should be ignored", 54321L)
        )

        val actual = searchManager.search("query")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertTrue(actual.bookmarks.isEmpty())
        assertTrue(actual.highlights.isEmpty())
        assertEquals(listOf(Pair(Note(VerseIndex(0, 0, 9), "just a note", 12345L), MockContents.kjvVerses[9])), actual.notes)
    }

    @Test
    fun `test search() with verses excluding notes`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(
                    MockContents.kjvShortName,
                    listOf(VerseIndex(0, 0, 9), VerseIndex(1, 2, 3))
            )
        } returns mapOf(Pair(VerseIndex(0, 0, 9), MockContents.kjvVerses[9]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "query", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery { noteRepository.search("query") } returns listOf(
                Note(VerseIndex(0, 0, 9), "just a note", 12345L),
                // no verse is available for this note, should be ignored
                // https://github.com/xizzhu/Joshua/issues/153
                Note(VerseIndex(1, 2, 3), "should be ignored", 54321L)
        )

        searchManager.saveConfiguration(SearchConfiguration(true, true, true, true, false))
        val actual = searchManager.search("query")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertTrue(actual.bookmarks.isEmpty())
        assertTrue(actual.highlights.isEmpty())
        assertTrue(actual.notes.isEmpty())
    }

    @Test
    fun `test search() with verses, bookmarks, highlights, and notes`() = runTest {
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 0), VerseIndex(0, 0, 1)))
        } returns mapOf(Pair(VerseIndex(0, 0, 0), MockContents.kjvVerses[0]))
        coEvery {
            bibleReadingRepository.readVerses(MockContents.kjvShortName, listOf(VerseIndex(0, 0, 9), VerseIndex(1, 2, 3)))
        } returns mapOf(Pair(VerseIndex(0, 0, 9), MockContents.kjvVerses[9]))
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "God", true, true))
        } returns listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2])
        coEvery {
            bookmarkRepository.read(Constants.SORT_BY_BOOK)
        } returns listOf(Bookmark(VerseIndex(0, 0, 0), 12345L), Bookmark(VerseIndex(0, 0, 1), 12345L))
        coEvery {
            highlightRepository.read(Constants.SORT_BY_BOOK)
        } returns listOf(Highlight(VerseIndex(0, 0, 0), Highlight.COLOR_PINK, 12345L), Highlight(VerseIndex(0, 0, 1), Highlight.COLOR_BLUE, 12345L))
        coEvery { noteRepository.search("God") } returns listOf(
                Note(VerseIndex(0, 0, 9), "just a note", 12345L),
                // no verse is available for this note, should be ignored
                // https://github.com/xizzhu/Joshua/issues/153
                Note(VerseIndex(1, 2, 3), "should be ignored", 54321L)
        )

        val actual = searchManager.search("God")
        assertEquals(listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[2]), actual.verses)
        assertEquals(listOf(Pair(Bookmark(VerseIndex(0, 0, 0), 12345L), MockContents.kjvVerses[0])), actual.bookmarks)
        assertEquals(listOf(Pair(Highlight(VerseIndex(0, 0, 0), Highlight.COLOR_PINK, 12345L), MockContents.kjvVerses[0])), actual.highlights)
        assertEquals(listOf(Pair(Note(VerseIndex(0, 0, 9), "just a note", 12345L), MockContents.kjvVerses[9])), actual.notes)
    }

    @Test
    fun `test search() by reference`() = runTest {
        coEvery { bibleReadingRepository.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames
        coEvery { bibleReadingRepository.readBookShortNames(MockContents.kjvShortName) } returns MockContents.kjvBookShortNames
        coEvery { bibleReadingRepository.readVerses(MockContents.kjvShortName, 0, 0) } returns MockContents.kjvVerses
        searchManager.saveConfiguration(SearchConfiguration(true, true, true, true, true, searchByReference = true))

        val chapter = searchManager.search("Genesis 1")
        assertEquals(MockContents.kjvVerses, chapter.verses)
        assertTrue(chapter.byReference)
        assertTrue(chapter.bookmarks.isEmpty())

        val verse = searchManager.search("gen. 1:2")
        assertEquals(listOf(MockContents.kjvVerses[1]), verse.verses)
        assertTrue(verse.byReference)
    }

    @Test
    fun `test search() by reference falls back to text search`() = runTest {
        coEvery { bibleReadingRepository.readBookNames(MockContents.kjvShortName) } returns MockContents.kjvBookNames
        coEvery { bibleReadingRepository.readBookShortNames(MockContents.kjvShortName) } returns MockContents.kjvBookShortNames
        coEvery {
            bibleReadingRepository.search(VerseSearchQuery(MockContents.kjvShortName, "God created", true, true))
        } returns listOf(MockContents.kjvVerses[0])
        searchManager.saveConfiguration(SearchConfiguration(true, true, true, true, true, searchByReference = true))

        val actual = searchManager.search("God created")
        assertEquals(listOf(MockContents.kjvVerses[0]), actual.verses)
        assertFalse(actual.byReference)
    }

    @Test
    fun `test parseReference()`() {
        val bookNames = MockContents.kjvBookNames
        val bookShortNames = MockContents.kjvBookShortNames
        fun parse(query: String) = parseReference(query, bookNames, bookShortNames)

        assertEquals(BibleReference(0, 0), parse("Genesis 1"))
        assertEquals(BibleReference(42, 0), parse("jhn1"))
        assertEquals(BibleReference(42, 0, 0..0), parse("jhn1:1"))
        assertEquals(BibleReference(0, 0, 0..0), parse("Gen. 1:1"))
        assertEquals(BibleReference(0, 0, 0..0), parse("  Genesis 1:1  "))
        assertEquals(BibleReference(42, 0, 3..3), parse("John 1:4"))
        assertEquals(BibleReference(42, 0, 3..3), parse("St. John 1:4"))
        assertEquals(BibleReference(8, 1, 2..4), parse("1 Samuel 2:3-5"))
        assertEquals(BibleReference(8, 1, 2..4), parse("1 Sam 2:3 - 5"))
        assertEquals(BibleReference(bookNames.indexOf("1 John"), 2), parse("1 John 3"))
        assertEquals(BibleReference(bookNames.indexOf("Song of Solomon"), 1), parse("Song of Solomon 2"))

        assertNull(parse(""))
        assertNull(parse("John"))
        assertNull(parse("God created"))
        assertNull(parse("John 0"))
        assertNull(parse("John 1:0"))
        assertNull(parse("John 1:5-3"))
        assertNull(parse("Unknown 1"))
        assertNull(parse("Joh 1"))
        assertNull(parse("John 1 2"))

        assertEquals(BibleReference(42, 0), parse("JHN 1"))
        assertEquals(BibleReference(42, 0), parse("jhn 1"))
        assertEquals(BibleReference(42, 0, 0..0), parse("JHN1.1"))
        assertEquals(BibleReference(42, 0, 0..0), parse("jhn1:1"))
        assertEquals(BibleReference(42, 0, 0..0), parse("Jhn 1.1"))
        assertEquals(BibleReference(42, 10), parse("JHN11"))
        assertEquals(BibleReference(42, 0, 0..2), parse("jhn 1.1-3"))
        assertEquals(BibleReference(BOOK_CODES.indexOf("1JN"), 1, 2..2), parse("1jn 2:3"))
        assertEquals(BibleReference(BOOK_CODES.indexOf("1JN"), 1, 2..3), parse("1 JN 2.3-4"))
        assertEquals(BibleReference(0, 0, 0..0), parse("gen1.1"))
        assertNull(parse("JHN"))
        assertNull(parse("XYZ 1"))
        assertNull(parse("JHN 1:0"))
    }

    @Test
    fun `test default configuration searches by reference`() = runTest {
        assertTrue(SearchConfiguration(true, true, true, true, true).searchByReference)
        assertTrue(searchManager.configuration().first().searchByReference)
    }

    @Test
    fun `test toKeywords()`() {
        assertTrue("".toKeywords().isEmpty())
        assertTrue("    ".toKeywords().isEmpty())
        assertTrue("　 　".toKeywords().isEmpty())

        assertEquals(listOf("query"), "query".toKeywords())
        assertEquals(listOf("query"), "QueRy".toKeywords())
        assertEquals(listOf("query"), "  query 　\t\t  ".toKeywords())
        assertEquals(listOf("query"), "\tQUERY　\t  ".toKeywords())

        assertEquals(listOf("multiple", "keywords"), "multiple keywords".toKeywords())
        assertEquals(listOf("multiple", "keywords"), "Multiple  　\t\t   　   Keywords\t\t".toKeywords())
        assertEquals(listOf("multiple", "keywords"), "    \t　　 MULTIPLE   keywords　　　  ".toKeywords())
        assertEquals(listOf("multiple", "keywords"), "   　　　  　 multiple　　 　\t　　   keywords 　　 　　  ".toKeywords())

        assertEquals(listOf("with double quotes"), "\"with double quotes\"".toKeywords())
        assertEquals(listOf("outside", "with double quotes"), "outside     \"with double quotes\"   \t\t\t   ".toKeywords())
        assertEquals(listOf("before", "with double quotes", "after"), "before     \"with double quotes\"   \tafter\t\t   ".toKeywords())
        assertEquals(listOf("before", "with double quotes", "after"), "before\"with double quotes\"   \tafter\t\t   ".toKeywords())
    }

    @Test
    fun `test containsWholeWord()`() {
        assertTrue("God created God's world".containsWholeWord("God"))
        assertFalse("godly creation".containsWholeWord("God"))
        assertFalse("my_God".containsWholeWord("God"))
    }
}
