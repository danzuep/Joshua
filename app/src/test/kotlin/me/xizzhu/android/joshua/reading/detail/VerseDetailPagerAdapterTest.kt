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

package me.xizzhu.android.joshua.reading.detail

import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.reading.VerseDetailViewData
import kotlin.test.Test
import kotlin.test.assertEquals

class VerseDetailPagerAdapterTest {
    private fun verseDetail(
        downloadedTranslationCount: Int = 1,
        note: String = "",
        footnotes: List<String> = emptyList(),
        crossReferences: List<String> = emptyList()
    ) = VerseDetailViewData(
        verseIndex = VerseIndex(0, 0, 0), verseTextItems = emptyList(),
        bookmarked = false, highlightColor = Highlight.COLOR_NONE, note = note,
        footnotes = footnotes, crossReferences = crossReferences,
        downloadedTranslationCount = downloadedTranslationCount
    )

    @Test
    fun `test visibleVersePages with a single translation and no paratext`() {
        assertEquals(
            listOf(VerseDetailPagerAdapter.PAGE_NOTE),
            visibleVersePages(verseDetail(downloadedTranslationCount = 1))
        )
    }

    @Test
    fun `test visibleVersePages with parallel translations and paratext`() {
        assertEquals(
            listOf(VerseDetailPagerAdapter.PAGE_NOTE, VerseDetailPagerAdapter.PAGE_VERSES, VerseDetailPagerAdapter.PAGE_PARATEXT),
            visibleVersePages(verseDetail(downloadedTranslationCount = 2, footnotes = listOf("a note")))
        )
    }

    @Test
    fun `test defaultVersePage selects paratext when note is empty`() {
        assertEquals(
            VerseDetailPagerAdapter.PAGE_PARATEXT,
            defaultVersePage(verseDetail(crossReferences = listOf("1:1 Gen 1:1")))
        )
    }

    @Test
    fun `test defaultVersePage selects notes when note is not empty`() {
        assertEquals(
            VerseDetailPagerAdapter.PAGE_NOTE,
            defaultVersePage(verseDetail(note = "just a note", footnotes = listOf("a note")))
        )
    }

    @Test
    fun `test defaultVersePage selects notes when there is no paratext`() {
        assertEquals(VerseDetailPagerAdapter.PAGE_NOTE, defaultVersePage(verseDetail()))
    }
}
