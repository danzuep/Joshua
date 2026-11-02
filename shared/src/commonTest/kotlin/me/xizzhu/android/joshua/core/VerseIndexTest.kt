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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerseIndexTest {
    @Test
    fun isValid_withinBible_returnsTrue() {
        assertTrue(VerseIndex(0, 0, 0).isValid())
    }

    @Test
    fun isValid_outOfRangeBook_returnsFalse() {
        assertFalse(VerseIndex(-1, 0, 0).isValid())
        assertFalse(VerseIndex(Bible.BOOK_COUNT, 0, 0).isValid())
    }

    @Test
    fun isValid_outOfRangeChapter_returnsFalse() {
        assertFalse(VerseIndex(0, -1, 0).isValid())
        assertFalse(VerseIndex(0, Bible.getChapterCount(0), 0).isValid())
    }

    @Test
    fun isValid_negativeVerse_returnsFalse() {
        assertFalse(VerseIndex(0, 0, -1).isValid())
    }
}
