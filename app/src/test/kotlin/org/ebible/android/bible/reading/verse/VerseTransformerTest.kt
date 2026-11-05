/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
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

import io.mockk.mockk
import org.ebible.android.bible.core.*
import org.ebible.android.bible.tests.BaseUnitTest
import org.ebible.android.bible.tests.MockContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.ebible.android.bible.ui.recyclerview.BaseItem
import kotlin.test.assertTrue

class VerseTransformerTest : BaseUnitTest() {
    private fun verse(index: Int, text: String) = Verse(VerseIndex(0, 0, index), Verse.Text("T", text), emptyList())

    private fun List<BaseItem>.paragraphs() = map { it as ParagraphItem }

    @Test
    fun `test toParagraphItems without structure is one paragraph`() {
        val items = listOf(verse(0, "In"), verse(1, "the"), verse(2, "beginning")).toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(1, items.size)
        assertNull(items[0].heading)
        assertEquals(listOf("In", "the", "beginning"), items[0].segments.map { it.text })
        assertEquals(listOf("1", "2", "3"), items[0].segments.map { it.label })
    }

    @Test
    fun `test toParagraphItems marks the start of a chapter with its number`() {
        val verses = listOf(
                Verse(VerseIndex(0, 7, 0), Verse.Text("T", "One"), emptyList()),
                Verse(VerseIndex(0, 7, 1), Verse.Text("T", "Two"), emptyList())
        )
        val segments = verses.toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs().single().segments
        assertEquals(8, segments[0].chapterNumber)
        assertNull(segments[1].chapterNumber)
    }

    @Test
    fun `test toParagraphItems with headings and paragraphs`() {
        val verses = listOf(
                verse(0, "${HEADING}Creation${HEADING}${PARAGRAPH}One"),
                verse(1, "Two ${PARAGRAPH}Three"),
                verse(2, "${PARAGRAPH}Four")
        )
        val items = verses.toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(3, items.size)
        assertEquals("Creation", items[0].heading)
        assertEquals(listOf("One", "Two"), items[0].segments.map { it.text })
        assertEquals(listOf("Three"), items[1].segments.map { it.text })
        assertNull(items[1].segments[0].label)
        assertEquals(listOf("Four"), items[2].segments.map { it.text })
        assertEquals("3", items[2].segments[0].label)
    }

    @Test
    fun `test toParagraphItems only the last part of a split verse ends the verse`() {
        val items = listOf(verse(0, "One ${PARAGRAPH}Two"), verse(1, "Three"))
                .toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(listOf(false), items[0].segments.map { it.endsVerse })
        assertEquals(listOf(true, true), items[1].segments.map { it.endsVerse })
    }

    @Test
    fun `test toParagraphItems with a heading in the middle of a verse`() {
        val items = listOf(verse(0, "In the beginning${HEADING}Creation${HEADING}${PARAGRAPH}God made"))
                .toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(2, items.size)
        assertNull(items[0].heading)
        assertEquals(listOf("In the beginning"), items[0].segments.map { it.text })
        assertEquals("Creation", items[1].heading)
        assertEquals(listOf("God made"), items[1].segments.map { it.text })
    }

    @Test
    fun `test toParagraphItems keeps a heading of a verse without text`() {
        val items = listOf(verse(0, "${HEADING}Title${HEADING}"), verse(1, "${PARAGRAPH}Text"))
                .toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(1, items.size)
        assertEquals("Title", items[0].heading)
        assertEquals(listOf("Text"), items[0].segments.map { it.text })
        assertEquals("2", items[0].segments[0].label)
    }

    @Test
    fun `test toParagraphItems leaves notes out of the text`() {
        val items = listOf(verse(0, "In${NOTE_MARK}fa note${NOTE_MARK} the beginning"), verse(1, "God${NOTE_MARK}x1:1 ref${NOTE_MARK}"))
                .toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(listOf("In the beginning", "God"), items.single().segments.map { it.text })
    }

    @Test
    fun `test toParagraphItems with bridged verses`() {
        val items = listOf(verse(0, "One"), verse(1, "Two"), verse(2, ""), verse(3, "Four"))
                .toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(listOf("1", "2-3", "4"), items.single().segments.map { it.label })
    }

    @Test
    fun `test toParagraphItems with poetry`() {
        val items = listOf(verse(0, "${PARAGRAPH}\nLine one"), verse(1, "\n\u2003Line two"))
                .toParagraphItems(mockk(), emptyList(), emptyList(), emptyList()).paragraphs()
        assertEquals(listOf("Line one", "\n\u2003Line two"), items.single().segments.map { it.text })
    }

    @Test
    fun `test toStringForCopy keeps non-UTF8 characters and adds the translation name`() {
        val text = "${HEADING}Title${HEADING}${PARAGRAPH}\u201CHello\u201D\u2014world\u00A0now\n\u2003Line two"
        assertEquals(
                "John 1:16 (World English Bible)\n> \u201CHello\u201D\u2014world\u00A0now\n> \u2003Line two",
                listOf(verse(15, text)).toStringForCopy("John", "World English Bible")
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for a single verse`() {
        assertEquals(
                "John 1:16 (World English Bible)\n> For God so loved the world",
                listOf(verse(15, "For God so loved the world")).toStringForCopy("John", "World English Bible")
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for poetry lines`() {
        assertEquals(
                "John 1:16 (World English Bible)\n> Line one\n> \u2003Line two",
                listOf(verse(15, "${PARAGRAPH}\nLine one\n\u2003Line two")).toStringForCopy("John", "World English Bible")
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for consecutive verses`() {
        assertEquals(
                "John 1:16-17 (World English Bible)\n> 16 A 17 B",
                listOf(verse(15, "A"), verse(16, "B")).toStringForCopy("John", "World English Bible")
        )
    }

    @Test
    fun `test toStringForCopy adds the chapter mark only across chapters`() {
        fun isaiah(chapter: Int, verse: Int, text: String) = Verse(VerseIndex(22, chapter - 1, verse - 1), Verse.Text("T", text), emptyList())
        assertEquals(
                "Isaiah 52:13-53:1 (World English Bible)\n> 13 A 14 B 15 C \u00B653:1 D",
                listOf(isaiah(52, 13, "A"), isaiah(52, 14, "B"), isaiah(52, 15, "C"), isaiah(53, 1, "D"))
                        .toStringForCopy("Isaiah", "World English Bible")
        )
        assertEquals(
                "Isaiah 53:1-2 (World English Bible)\n> 1 D 2 E",
                listOf(isaiah(53, 1, "D"), isaiah(53, 2, "E")).toStringForCopy("Isaiah", "World English Bible")
        )
    }

    @Test
    fun `test toStringForCopy isolates the chapter and reference in right to left text`() {
        fun matthew(chapter: Int, verse: Int, text: String) = Verse(VerseIndex(39, chapter - 1, verse - 1), Verse.Text("T", text), emptyList())
        assertEquals(
                "Matthew \u20661:25-2:1\u2069 (Arabic)\n> 25 \u0628\u0635\u062D \u00B6\u20662:1\u2069 \u0628\u0639\u062F",
                listOf(matthew(1, 25, "\u0628\u0635\u062D"), matthew(2, 1, "\u0628\u0639\u062F")).toStringForCopy("Matthew", "Arabic")
        )
    }

    @Test
    fun `test toStringForSharing keeps the plain format without the translation name`() {
        assertEquals(
                "John 1:16-17\n16 A 17 B",
                listOf(verse(15, "A"), verse(16, "B")).toStringForSharing("John")
        )
    }

    @Test
    fun `test stripStructure`() {
        assertEquals("One Two Three", "${HEADING}Title${HEADING}${PARAGRAPH}One\n\u2003Two${PARAGRAPH}Three".stripStructure())
    }


    @Test
    fun testVerseToStringForSharing() {
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1 ${MockContents.kjvVerses[0].text.text}",
                MockContents.kjvVerses[0].toStringForSharing(MockContents.kjvBookNames[0])
        )
    }

    @Test
    fun testVerseWithParallelTranslationsToStringForSharing() {
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text}",
                MockContents.kjvVersesWithCuvParallel[0].toStringForSharing(MockContents.kjvBookNames[0])
        )
    }

    @Test
    fun testVersesToStringForSharing() {
        // single verse
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1 ${MockContents.kjvVerses[0].text.text}",
                listOf(MockContents.kjvVerses[0]).toStringForSharing(MockContents.kjvBookNames[0])
        )

        // multiple verses
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "1 ${MockContents.kjvVerses[0].text.text} 2 ${MockContents.kjvVerses[1].text.text}\n\n" +
                        "${MockContents.kjvBookNames[0]} 1:10\n" +
                        MockContents.kjvVerses[9].text.text,
                listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[1], MockContents.kjvVerses[9])
                        .toStringForSharing(MockContents.kjvBookNames[0])
        )
    }

    @Test
    fun testVersesWithParallelTranslationsToStringForSharing() {
        // single verse
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[0]).toStringForSharing(MockContents.kjvBookNames[0])
        )

        // multiple verses
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "${MockContents.kjvShortName}: 1 ${MockContents.kjvVerses[0].text.text} 2 ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.cuvShortName}: 1 ${MockContents.cuvVerses[0].text.text} 2 ${MockContents.cuvVerses[1].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[0], MockContents.kjvVersesWithCuvParallel[1])
                        .toStringForSharing(MockContents.kjvBookNames[0])
        )
    }

    @Test
    fun testVersesWithRandomOrderToStringForSharing() {
        // without parallel
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "1 ${MockContents.kjvVerses[0].text.text} 2 ${MockContents.kjvVerses[1].text.text}\n\n" +
                        "${MockContents.kjvBookNames[0]} 1:10\n" +
                        MockContents.kjvVerses[9].text.text,
                listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[9], MockContents.kjvVerses[1])
                        .toStringForSharing(MockContents.kjvBookNames[0])
        )

        // with parallel
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "${MockContents.kjvShortName}: 1 ${MockContents.kjvVerses[0].text.text} 2 ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.cuvShortName}: 1 ${MockContents.cuvVerses[0].text.text} 2 ${MockContents.cuvVerses[1].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[1], MockContents.kjvVersesWithCuvParallel[0])
                        .toStringForSharing(MockContents.kjvBookNames[0])
        )
    }
}
