/*
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

package com.github.danzuep.yeshua.android.reading.verse

import android.text.Spanned
import android.text.style.ImageSpan
import com.github.danzuep.yeshua.android.core.Bookmark
import com.github.danzuep.yeshua.android.core.Verse
import com.github.danzuep.yeshua.android.core.VerseIndex
import com.github.danzuep.yeshua.android.reading.VerseUpdate
import com.github.danzuep.yeshua.android.tests.BaseUnitTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ParagraphItemTest : BaseUnitTest() {
    private val context = RuntimeEnvironment.getApplication()

    private val verses = (0..3).map { Verse(VerseIndex(57, 0, it), Verse.Text("T", "Verse $it"), emptyList()) }

    private fun paragraph(bookmarks: List<Bookmark> = emptyList()) =
            verses.toParagraphItems(context, bookmarks, emptyList(), emptyList()).single() as ParagraphItem

    private fun bookmarkCount(item: ParagraphItem): Int {
        val text = item.textForDisplay() as Spanned
        return text.getSpans(0, text.length, LabelWithIconSpan::class.java).size
    }

    private fun noteCount(item: ParagraphItem): Int {
        val text = item.textForDisplay() as Spanned
        return text.getSpans(0, text.length, ImageSpan::class.java).size
    }

    @Test
    fun `test bookmark is added and removed live`() {
        val item = paragraph()
        assertEquals(0, bookmarkCount(item))

        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.BOOKMARK_ADDED))
        assertEquals(1, bookmarkCount(item))

        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.BOOKMARK_REMOVED))
        assertEquals(0, bookmarkCount(item))
    }

    @Test
    fun `test bookmark renders for saved state when chapter is loaded`() {
        assertEquals(1, bookmarkCount(paragraph(listOf(Bookmark(VerseIndex(57, 0, 2), 0L)))))
    }

    @Test
    fun `test note icon is an end of verse image span but the bookmark is not`() {
        val item = paragraph(listOf(Bookmark(VerseIndex(57, 0, 2), 0L)))
        assertEquals(0, noteCount(item))
        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.NOTE_ADDED))
        assertEquals(1, noteCount(item))
    }
}
