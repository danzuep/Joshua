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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerseStructureTest {
    @Test
    fun `test stripStructure removes headings and notes`() {
        val text = "In the beginning${NOTE_MARK}fa note${NOTE_MARK} God${HEADING}Creation${HEADING}${PARAGRAPH}created"
        assertEquals("In the beginning God created", text.stripStructure())
    }

    @Test
    fun `test toDisplayedText keeps paragraphs and removes headings and notes`() {
        val text = "First line${PARAGRAPH}${HEADING}Title${HEADING}${PARAGRAPH}Second${NOTE_MARK}x1:1 ref${NOTE_MARK}"
        assertEquals("First line\n\nSecond", text.toDisplayedText())
    }

    @Test
    fun `test extractNotes()`() {
        val text = "God${NOTE_MARK}fa note${NOTE_MARK} made${NOTE_MARK}x1:1 Gen 1:1${NOTE_MARK}"
        assertEquals(
            listOf(false to "a note", true to "1:1 Gen 1:1"),
            text.extractNotes().map { it.isCrossReference to it.text }
        )
        assertEquals(emptyList(), "God made".extractNotes())
    }

    @Test
    fun `test hasNotes()`() {
        assertTrue("God${NOTE_MARK}fnote${NOTE_MARK}".hasNotes())
        assertFalse("God made".hasNotes())
    }
}
