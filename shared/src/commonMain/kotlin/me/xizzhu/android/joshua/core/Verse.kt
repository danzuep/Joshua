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

data class VerseIndex(val bookIndex: Int, val chapterIndex: Int, val verseIndex: Int) {
    companion object {
        val INVALID = VerseIndex(-1, -1, -1)
    }

    fun isValid(): Boolean =
            bookIndex >= 0 && bookIndex < Bible.BOOK_COUNT
                    && chapterIndex >= 0 && chapterIndex < Bible.getChapterCount(bookIndex)
                    && verseIndex >= 0
}

data class Verse(val verseIndex: VerseIndex, val text: Text, val parallel: List<Text>) {
    companion object {
        val INVALID = Verse(VerseIndex.INVALID, Text.INVALID, emptyList())
    }

    fun isValid(): Boolean {
        if (!verseIndex.isValid() || !text.isValid()) return false

        for (p in parallel) {
            if (!p.isValid()) return false
        }

        return true
    }

    data class Text(val translationShortName: String, val text: String) {
        companion object {
            val INVALID = Text("", "")
        }

        fun isValid(): Boolean = translationShortName.isNotEmpty() // text can be empty
    }
}
