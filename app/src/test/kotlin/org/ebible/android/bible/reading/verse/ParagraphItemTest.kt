/*
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

package org.ebible.android.bible.reading.verse

import android.text.Spanned
import android.text.style.ImageSpan
import org.ebible.android.bible.core.Bookmark
import org.ebible.android.bible.core.Highlight
import org.ebible.android.bible.core.Constants
import org.ebible.android.bible.core.Note
import org.ebible.android.bible.core.Verse
import org.ebible.android.bible.core.VerseAnnotation
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.core.repository.VerseAnnotationRepository
import org.ebible.android.bible.core.repository.local.LocalVerseAnnotationStorage
import org.ebible.android.bible.reading.VerseUpdate
import org.ebible.android.bible.testing.CoreVerse
import org.ebible.android.bible.tests.BaseUnitTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ParagraphItemTest : BaseUnitTest() {
    private val context = RuntimeEnvironment.getApplication()

    private fun verse(index: Int, text: String) = Verse(VerseIndex(0, 0, index), Verse.Text("T", text), emptyList())

    private fun iconCount(item: ParagraphItem): Int {
        val text = item.textForDisplay() as Spanned
        return text.getSpans(0, text.length, ImageSpan::class.java).size + bookmarkSpans(text).size
    }

    /** The bookmark icon is stacked under the verse number by [LabelWithIconSpan], not appended as an [ImageSpan]. */
    private fun bookmarkSpans(text: Spanned) = text.getSpans(0, text.length, LabelWithIconSpan::class.java)

    @Test
    fun `test bookmark and note icons render after being saved on a highlighted verse`() {
        val verses = listOf(verse(0, "One"), verse(1, "Two"), verse(2, "Three"), verse(3, "Four"))
        val item = verses.toParagraphItems(context, emptyList(), emptyList(), emptyList()).single() as ParagraphItem
        val verseIndex = VerseIndex(0, 0, 3)

        assertEquals(0, iconCount(item))
        item.update(VerseUpdate(verseIndex, VerseUpdate.HIGHLIGHT_UPDATED, Highlight.COLOR_BLUE))
        item.update(VerseUpdate(verseIndex, VerseUpdate.NOTE_ADDED))
        item.update(VerseUpdate(verseIndex, VerseUpdate.BOOKMARK_ADDED))
        assertEquals(2, iconCount(item))

        item.update(VerseUpdate(verseIndex, VerseUpdate.BOOKMARK_REMOVED))
        assertEquals(1, iconCount(item))
    }

    @Test
    fun `test icons render for saved state when chapter is loaded`() {
        val verses = listOf(verse(0, "One"), verse(1, "Two"), verse(2, "Three"), verse(3, "Four"))
        val verseIndex = VerseIndex(0, 0, 3)
        val item = verses.toParagraphItems(
                context,
                listOf(Bookmark(verseIndex, 0L)),
                listOf(Highlight(verseIndex, Highlight.COLOR_BLUE, 0L)),
                listOf(Note(verseIndex, "note", 0L))
        ).single() as ParagraphItem
        assertEquals(2, iconCount(item))
    }

    private class InMemoryAnnotations<T : VerseAnnotation> : LocalVerseAnnotationStorage<T> {
        val items = linkedMapOf<VerseIndex, T>()
        override suspend fun readSortOrder(): Int = Constants.DEFAULT_SORT_ORDER
        override suspend fun saveSortOrder(sortOrder: Int) {}
        override suspend fun read(sortOrder: Int): List<T> = items.values.toList()
        override suspend fun read(bookIndex: Int, chapterIndex: Int): List<T> =
                items.values.filter { it.verseIndex.bookIndex == bookIndex && it.verseIndex.chapterIndex == chapterIndex }
        override suspend fun read(verseIndex: VerseIndex): T = items.getValue(verseIndex)
        override suspend fun search(query: String): List<T> = emptyList()
        override suspend fun save(verseAnnotation: T) { items[verseAnnotation.verseIndex] = verseAnnotation }
        override suspend fun save(verseAnnotations: List<T>) = verseAnnotations.forEach { save(it) }
        override suspend fun remove(verseIndex: VerseIndex) { items.remove(verseIndex) }
    }

    private fun hebrews(index: Int, text: String) = Verse(VerseIndex(57, 0, index), Verse.Text("T", text), emptyList())

    /** Bookmarks only Hebrews 1:3 with the shared fluent builder, then loads the chapter like the reader does. */
    @Test
    fun `test only Hebrews 1_3 shows a bookmark icon, under its verse number`() = runTest {
        val bookmarks = InMemoryAnnotations<Bookmark>()
        CoreVerse(
                VerseIndex(57, 0, 2),
                VerseAnnotationRepository(bookmarks, this),
                VerseAnnotationRepository(InMemoryAnnotations<Highlight>(), this),
                VerseAnnotationRepository(InMemoryAnnotations<Note>(), this)
        ).bookmark()

        val verses = listOf(
                hebrews(0, "God spoke long ago."), hebrews(1, "He has spoken by his Son."),
                hebrews(2, "His Son is the radiance of his glory."), hebrews(3, "Having become better than the angels.")
        )
        val item = verses.toParagraphItems(context, bookmarks.read(57, 0), emptyList(), emptyList()).single() as ParagraphItem
        val text = item.textForDisplay() as Spanned
        val icons = bookmarkSpans(text)

        assertEquals(1, icons.size)
        val icon = icons.single()
        assertEquals("3", text.substring(text.getSpanStart(icon), text.getSpanEnd(icon)), "icon should be under verse 3's number")
        assertTrue(text.getSpanStart(icon) < text.indexOf("His Son is"), "icon should come before verse 3's text")
        assertEquals(0, text.getSpans(0, text.length, ImageSpan::class.java).size)
    }

    @Test
    fun `test John 1_29 with a mid-verse footnote shows footnote and bookmark icons`() {
        fun john(index: Int, text: String) = Verse(VerseIndex(42, 0, index), Verse.Text("T", text), emptyList())
        val verses = listOf(
                john(27, "These things were done in Bethany beyond the Jordan.\u2029"),
                john(28, "\u2029The next day, he saw Jesus coming to him, and said, \u201cBehold,\u2027f1:29 \u201cBehold\u201d, means look.\u2027 the Lamb of God, who takes away the sin of the world!"),
                john(29, "\u2029The next day John saw Jesus.")
        )
        val bookmarked = VerseIndex(42, 0, 28)
        val items = verses.toParagraphItems(context, emptyList(), emptyList(), emptyList()).filterIsInstance<ParagraphItem>()
        val item = items.first { it.containsVerse(28) }

        assertEquals(1, iconCount(item))
        item.update(VerseUpdate(bookmarked, VerseUpdate.BOOKMARK_ADDED))
        assertEquals(2, iconCount(item))

        val loaded = verses.toParagraphItems(context, listOf(Bookmark(bookmarked, 0L)), emptyList(), emptyList())
                .filterIsInstance<ParagraphItem>().first { it.containsVerse(28) }
        assertEquals(2, iconCount(loaded))
    }

    @Test
    fun `test bookmarking a middle verse updates the icons live`() {
        val verses = listOf(hebrews(0, "One"), hebrews(1, "Two"), hebrews(2, "Three"), hebrews(3, "Four"))
        val item = verses.toParagraphItems(context, emptyList(), emptyList(), emptyList()).single() as ParagraphItem

        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.BOOKMARK_ADDED))
        assertEquals(1, iconCount(item))
    }
}
