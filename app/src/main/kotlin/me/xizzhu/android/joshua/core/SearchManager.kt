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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import me.xizzhu.android.joshua.core.repository.BibleReadingRepository
import me.xizzhu.android.joshua.core.repository.VerseAnnotationRepository
import me.xizzhu.android.joshua.utils.firstNotEmpty
import java.util.*

data class SearchConfiguration(
        val includeOldTestament: Boolean, val includeNewTestament: Boolean,
        val includeBookmarks: Boolean, val includeHighlights: Boolean, val includeNotes: Boolean,
        val wholeWords: Boolean = false
)

data class VerseSearchQuery(
        val translation: String, val keyword: String, val includeOldTestament: Boolean, val includeNewTestament: Boolean,
        val wholeWords: Boolean = false
)

data class SearchResult(
        val verses: List<Verse>,
        val bookmarks: List<Pair<Bookmark, Verse>>,
        val highlights: List<Pair<Highlight, Verse>>,
        val notes: List<Pair<Note, Verse>>
)

class SearchManager(
        private val bibleReadingRepository: BibleReadingRepository,
        private val bookmarkRepository: VerseAnnotationRepository<Bookmark>,
        private val highlightRepository: VerseAnnotationRepository<Highlight>,
        private val noteRepository: VerseAnnotationRepository<Note>
) {
    private val searchConfiguration: MutableStateFlow<SearchConfiguration> = MutableStateFlow(SearchConfiguration(
            includeOldTestament = true, includeNewTestament = true, includeBookmarks = true, includeHighlights = true, includeNotes = true))

    fun configuration(): Flow<SearchConfiguration> = searchConfiguration

    fun saveConfiguration(config: SearchConfiguration) {
        searchConfiguration.value = config
    }

    suspend fun search(keyword: String): SearchResult = withContext(Dispatchers.Default) {
        val config = searchConfiguration.value
        val currentTranslation = bibleReadingRepository.currentTranslation.firstNotEmpty()
        val verses = async {
            bibleReadingRepository.search(
                    VerseSearchQuery(currentTranslation, keyword, config.includeOldTestament, config.includeNewTestament, config.wholeWords)
            )
        }
        val bookmarks = async {
            if (config.includeBookmarks) {
                searchAnnotations(bookmarkRepository, currentTranslation, keyword, config.wholeWords)
            } else {
                emptyList()
            }
        }
        val highlights = async {
            if (config.includeHighlights) {
                searchAnnotations(highlightRepository, currentTranslation, keyword, config.wholeWords)
            } else {
                emptyList()
            }
        }
        val notes = async {
            if (config.includeNotes) {
                searchNotes(currentTranslation, keyword, config.wholeWords)
            } else {
                emptyList()
            }
        }
        return@withContext SearchResult(verses.await(), bookmarks.await(), highlights.await(), notes.await())
    }

    private suspend fun <V : VerseAnnotation> searchAnnotations(
            verseAnnotationRepository: VerseAnnotationRepository<V>, currentTranslation: String, keyword: String, wholeWords: Boolean
    ): List<Pair<V, Verse>> =
            verseAnnotationRepository.read(Constants.SORT_BY_BOOK)
                    .takeIf { it.isNotEmpty() }
                    ?.let { annotations ->
                        val keywords = keyword.toKeywords()
                        val verses = bibleReadingRepository.readVerses(currentTranslation, annotations.map { it.verseIndex })
                        annotations.mapNotNull { annotation ->
                            // Annotations on verses that are empty or missing in this translation are intentionally left out of the results.
                            verses[annotation.verseIndex]
                                    ?.takeIf { it.text.text.isNotEmpty() }
                                    ?.takeIf { verse ->
                                        keywords.any { term ->
                                            if (wholeWords) verse.text.text.containsWholeWord(term)
                                            else verse.text.text.lowercase(Locale.getDefault()).contains(term, false)
                                        }
                                    }
                                    ?.let { Pair(annotation, it) }
                        }
                    }
                    ?: emptyList()

    private suspend fun searchNotes(currentTranslation: String, keyword: String, wholeWords: Boolean): List<Pair<Note, Verse>> =
            noteRepository.search(keyword)
                    .takeIf { it.isNotEmpty() }
                    ?.let { notes ->
                        val verses = bibleReadingRepository.readVerses(currentTranslation, notes.map { it.verseIndex })
                        notes.mapNotNull { note ->
                            // Notes on verses that are empty or missing in this translation are intentionally left out of the results.
                            val noteMatches = !wholeWords || keyword.toKeywords().all { note.note.containsWholeWord(it) }
                            verses[note.verseIndex]?.takeIf { it.text.text.isNotEmpty() && noteMatches }?.let { Pair(note, it) }
                        }
                    }
                    ?: emptyList()
}

internal fun String.containsWholeWord(keyword: String): Boolean {
    val text = lowercase(Locale.getDefault())
    val term = keyword.lowercase(Locale.getDefault())
    if (term.isEmpty()) return false

    var start = text.indexOf(term)
    while (start >= 0) {
        val end = start + term.length
        val leftBoundary = start == 0 || !text.isWordCharacterBefore(start)
        val rightBoundary = end == text.length || !text.isWordCharacterAt(end)
        if (leftBoundary && rightBoundary) return true
        start = text.indexOf(term, start + 1)
    }
    return false
}

private fun String.isWordCharacterBefore(index: Int): Boolean =
        Character.isLetterOrDigit(Character.codePointBefore(this, index)) || Character.codePointBefore(this, index) == '_'.code

private fun String.isWordCharacterAt(index: Int): Boolean =
        Character.isLetterOrDigit(Character.codePointAt(this, index)) || Character.codePointAt(this, index) == '_'.code

fun String.toKeywords(): List<String> {
    if (isEmpty()) return emptyList()

    val results = arrayListOf<String>()
    val sb = StringBuilder()
    var doubleQuoteStarted = false
    lowercase(Locale.getDefault()).forEach { c ->
        if (c.isDoubleQuote()) {
            results.addAndClear(sb)
            doubleQuoteStarted = !doubleQuoteStarted
            return@forEach
        }

        if (c.isWhitespace() && !doubleQuoteStarted) {
            results.addAndClear(sb)
            return@forEach
        }

        sb.append(c)
    }
    if (sb.isNotEmpty()) {
        results.add(sb.toString())
    }
    return results
}

private fun ArrayList<String>.addAndClear(sb: StringBuilder) {
    if (sb.isNotEmpty()) {
        add(sb.toString())
        sb.clear()
    }
}

private fun Char.isDoubleQuote(): Boolean =
        this == Typography.quote
                || this == Typography.leftDoubleQuote || this == Typography.rightDoubleQuote
                || this == Typography.lowDoubleQuote || this == Typography.lowSingleQuote
