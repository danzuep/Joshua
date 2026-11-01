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

import android.text.Spanned
import android.text.style.ImageSpan
import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.Verse
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.reading.VerseUpdate
import me.xizzhu.android.joshua.tests.BaseUnitTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ParagraphItemTest : BaseUnitTest() {
    private val context = RuntimeEnvironment.getApplication()

    private fun verse(index: Int, text: String) = Verse(VerseIndex(0, 0, index), Verse.Text("T", text), emptyList())

    private fun iconCount(item: ParagraphItem): Int {
        val text = item.textForDisplay() as Spanned
        return text.getSpans(0, text.length, ImageSpan::class.java).size
    }

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
}
