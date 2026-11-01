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

package me.xizzhu.android.joshua.reading.verse

import io.mockk.mockk
import me.xizzhu.android.joshua.core.*
import me.xizzhu.android.joshua.tests.BaseUnitTest
import me.xizzhu.android.joshua.tests.MockContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import me.xizzhu.android.joshua.ui.recyclerview.BaseItem
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
    fun `test toStringForCopy matches the displayed text exactly`() {
        val text = "${HEADING}Title${HEADING}${PARAGRAPH}\u201CHello\u201D\u2014world\u00A0now\n\u2003Line two"
        assertEquals(
                "John 1:16 \u201CHello\u201D\u2014world\u00A0now\n\u2003Line two",
                listOf(verse(15, text)).toStringForCopy("John", consolidateVerses = false, markdown = false)
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for a single verse`() {
        assertEquals(
                "John 1:16\n> For God so loved the world",
                listOf(verse(15, "For God so loved the world")).toStringForCopy("John", consolidateVerses = false, markdown = true)
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for poetry lines`() {
        assertEquals(
                "John 1:16\n> Line one\n> \u2003Line two",
                listOf(verse(15, "${PARAGRAPH}\nLine one\n\u2003Line two")).toStringForCopy("John", consolidateVerses = false, markdown = true)
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for separate verses`() {
        assertEquals(
                "John 1:16\n> A\n\nJohn 1:17\n> B",
                listOf(verse(15, "A"), verse(16, "B")).toStringForCopy("John", consolidateVerses = false, markdown = true)
        )
    }

    @Test
    fun `test toStringForCopy as Markdown for consolidated verses`() {
        assertEquals(
                "John 1:16-17\n> A B",
                listOf(verse(15, "A"), verse(16, "B")).toStringForCopy("John", consolidateVerses = true, markdown = true)
        )
    }

    @Test
    fun `test toStringForCopy without Markdown keeps the plain format`() {
        assertEquals(
                "John 1:16 A\nJohn 1:17 B",
                listOf(verse(15, "A"), verse(16, "B")).toStringForCopy("John", consolidateVerses = false, markdown = false)
        )
        assertEquals(
                "John 1:16-17\nA B",
                listOf(verse(15, "A"), verse(16, "B")).toStringForCopy("John", consolidateVerses = true, markdown = false)
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
                listOf(MockContents.kjvVerses[0]).toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1 ${MockContents.kjvVerses[0].text.text}",
                listOf(MockContents.kjvVerses[0]).toStringForSharing(MockContents.kjvBookNames[0], true)
        )

        // multiple verses
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1 ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:2 ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:10 ${MockContents.kjvVerses[9].text.text}",
                listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[1], MockContents.kjvVerses[9])
                        .toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1 ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:2 ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:10 ${MockContents.kjvVerses[9].text.text}",
                listOf(MockContents.kjvVerses[9], MockContents.kjvVerses[1], MockContents.kjvVerses[0])
                        .toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "${MockContents.kjvVerses[0].text.text} ${MockContents.kjvVerses[1].text.text}\n\n" +
                        "${MockContents.kjvBookNames[0]} 1:10\n" +
                        MockContents.kjvVerses[9].text.text,
                listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[1], MockContents.kjvVerses[9])
                        .toStringForSharing(MockContents.kjvBookNames[0], true)
        )
    }

    @Test
    fun testVersesWithParallelTranslationsToStringForSharing() {
        // single verse
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[0]).toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[0]).toStringForSharing(MockContents.kjvBookNames[0], true)
        )

        // multiple verses
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:2\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[1].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[0], MockContents.kjvVersesWithCuvParallel[1])
                        .toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text} ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text} ${MockContents.cuvVerses[1].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[0], MockContents.kjvVersesWithCuvParallel[1])
                        .toStringForSharing(MockContents.kjvBookNames[0], true)
        )
    }

    @Test
    fun testVersesWithRandomOrderToStringForSharing() {
        // without parallel
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1 ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:2 ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:10 ${MockContents.kjvVerses[9].text.text}",
                listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[9], MockContents.kjvVerses[1])
                        .toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "${MockContents.kjvVerses[0].text.text} ${MockContents.kjvVerses[1].text.text}\n\n" +
                        "${MockContents.kjvBookNames[0]} 1:10\n" +
                        MockContents.kjvVerses[9].text.text,
                listOf(MockContents.kjvVerses[0], MockContents.kjvVerses[9], MockContents.kjvVerses[1])
                        .toStringForSharing(MockContents.kjvBookNames[0], true)
        )

        // with parallel
        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text}\n" +
                        "${MockContents.kjvBookNames[0]} 1:2\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[1].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[1], MockContents.kjvVersesWithCuvParallel[0])
                        .toStringForSharing(MockContents.kjvBookNames[0], false)
        )

        assertEquals(
                "${MockContents.kjvBookNames[0]} 1:1-2\n" +
                        "${MockContents.kjvShortName}: ${MockContents.kjvVerses[0].text.text} ${MockContents.kjvVerses[1].text.text}\n" +
                        "${MockContents.cuvShortName}: ${MockContents.cuvVerses[0].text.text} ${MockContents.cuvVerses[1].text.text}",
                listOf(MockContents.kjvVersesWithCuvParallel[1], MockContents.kjvVersesWithCuvParallel[0])
                        .toStringForSharing(MockContents.kjvBookNames[0], true)
        )
    }
}
