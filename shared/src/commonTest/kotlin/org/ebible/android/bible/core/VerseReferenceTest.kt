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

package org.ebible.android.bible.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VerseReferenceTest {
    private fun String.links(defaultBook: Int = 0, bookNames: List<String> = emptyList()): List<Pair<String, VerseIndex>> =
            findVerseReferences(bookNames, defaultBook).map { substring(it.start, it.end) to it.verseIndex }

    @Test
    fun singleReference() {
        assertEquals(listOf("Genesis 2:3" to VerseIndex(0, 1, 2)), "Genesis 2:3".links())
    }

    @Test
    fun rangeIsOneLinkToItsFirstVerse() {
        assertEquals(listOf("Exodus 3:4-6" to VerseIndex(1, 2, 3)), "Exodus 3:4-6".links())
    }

    @Test
    fun numberAfterCommaIsSeparateLink() {
        assertEquals(
                listOf("Job 3:4" to VerseIndex(17, 2, 3), "5-6" to VerseIndex(17, 2, 4), "9" to VerseIndex(17, 2, 8)),
                "Job 3:4,5-6, 9".links()
        )
    }

    @Test
    fun referenceAfterCommaWithChapterIsNotAContinuation() {
        assertEquals(
                listOf("Job 3:4" to VerseIndex(17, 2, 3), "5:6" to VerseIndex(17, 4, 5)),
                "Job 3:4, 5:6".links()
        )
    }

    @Test
    fun referencesWithoutBookUseThePreviousOrDefaultBook() {
        assertEquals(
                listOf("1:1" to VerseIndex(4, 0, 0), "Gen 2:3" to VerseIndex(0, 1, 2), "7:8" to VerseIndex(0, 6, 7)),
                "1:1 Gen 2:3; 7:8".links(defaultBook = 4)
        )
    }

    @Test
    fun numberedBooksAndAbbreviations() {
        assertEquals(
                listOf("1 Cor. 13:4" to VerseIndex(45, 12, 3), "Jn 3:16" to VerseIndex(42, 2, 15), "Ps 23:1" to VerseIndex(18, 22, 0)),
                "1 Cor. 13:4; Jn 3:16; Ps 23:1".links()
        )
    }

    @Test
    fun translationBookNames() {
        val names = List(Bible.BOOK_COUNT) { "Book$it" }
        assertEquals(listOf("Book5 1:2" to VerseIndex(5, 0, 1)), "See Book5 1:2.".links(bookNames = names).map {
            it.first to it.second
        })
    }

    @Test
    fun invalidChapterIsIgnored() {
        assertEquals(emptyList(), "Jude 5:1".links())
    }
}
