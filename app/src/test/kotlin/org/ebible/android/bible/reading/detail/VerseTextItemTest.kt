/*
 * Copyright (C) 2023 Xizhi Zhu
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

package org.ebible.android.bible.reading.detail

import org.ebible.android.bible.R
import org.ebible.android.bible.core.Verse
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.tests.BaseUnitTest
import org.ebible.android.bible.tests.MockContents
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VerseTextItemTest : BaseUnitTest() {
    @Test
    fun testItemViewType() {
        assertEquals(R.layout.item_verse_text, VerseTextItem(VerseIndex.INVALID, 0, Verse.Text.INVALID, "").viewType)
    }

    @Test
    fun testTextForDisplay() {
        assertEquals(
                "KJV, Genesis 1:1\nIn the beginning God created the heaven and the earth.",
                VerseTextItem(MockContents.kjvVerses[0].verseIndex, 0, MockContents.kjvVerses[0].text, MockContents.kjvBookNames[0]).textForDisplay.toString()
        )
    }

    @Test
    fun testTextForDisplayWithFollowingEmptyVerse() {
        assertEquals(
                "KJV, Genesis 1:1-2\nIn the beginning God created the heaven and the earth.",
                VerseTextItem(MockContents.kjvVerses[0].verseIndex, 1, MockContents.kjvVerses[0].text, MockContents.kjvBookNames[0]).textForDisplay.toString()
        )
    }

    @Test
    fun testTextForDisplayWithInvalidVerse() {
        assertTrue(VerseTextItem(VerseIndex.INVALID, 0, Verse.Text.INVALID, "").textForDisplay.isEmpty())
        assertTrue(VerseTextItem(VerseIndex.INVALID, 0, MockContents.kjvVerses[0].text, MockContents.kjvBookNames[0]).textForDisplay.isEmpty())
        assertTrue(VerseTextItem(MockContents.kjvVerses[0].verseIndex, 0, Verse.Text.INVALID, "").textForDisplay.isEmpty())
    }
}
