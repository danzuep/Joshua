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

data class SearchConfiguration(
        val includeOldTestament: Boolean, val includeNewTestament: Boolean,
        val includeBookmarks: Boolean, val includeHighlights: Boolean, val includeNotes: Boolean,
        val wholeWords: Boolean = false, val searchByReference: Boolean = true
)

data class VerseSearchQuery(
        val translation: String, val keyword: String, val includeOldTestament: Boolean, val includeNewTestament: Boolean,
        val wholeWords: Boolean = false
)

data class SearchResult(
        val verses: List<Verse>,
        val bookmarks: List<Pair<Bookmark, Verse>>,
        val highlights: List<Pair<Highlight, Verse>>,
        val notes: List<Pair<Note, Verse>>,
        val byReference: Boolean = false,
        /** Set when the query is a 3-letter book code, e.g. "hag", so the UI can offer to open that book. */
        val bookIndex: Int? = null
)

/** A parsed reference, with 0-based indexes. A null [verseIndexes] means the whole chapter. */
data class BibleReference(val bookIndex: Int, val chapterIndex: Int, val verseIndexes: IntRange? = null)
