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

import android.content.Context
import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.HEADING
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.PARAGRAPH
import me.xizzhu.android.joshua.core.hasNotes
import me.xizzhu.android.joshua.core.removeNotes
import me.xizzhu.android.joshua.core.toDisplayedVerse
import me.xizzhu.android.joshua.core.Verse
import me.xizzhu.android.joshua.ui.recyclerview.BaseItem

private val STRUCTURE = Regex("$HEADING([^$HEADING]*)$HEADING|$PARAGRAPH")

/**
 * Builds the flowing paragraphs of a chapter from verses that still have their structure, see [PARAGRAPH] and [HEADING].
 * Empty verses (e.g. the second verse of a bridge like 2-3) are skipped.
 */
fun List<Verse>.toParagraphItems(
        context: Context, bookmarks: List<Bookmark>, highlights: List<Highlight>, notes: List<Note>
): List<BaseItem> {
    val bookmarkSet = bookmarks.mapTo(mutableSetOf()) { it.verseIndex.verseIndex }
    val highlightMap = highlights.filter { it.color != Highlight.COLOR_NONE }
            .associateTo(mutableMapOf()) { it.verseIndex.verseIndex to it.color }
    val noteSet = notes.mapTo(mutableSetOf()) { it.verseIndex.verseIndex }
    val footnoteSet = filter { it.text.text.hasNotes() }.mapTo(mutableSetOf()) { it.verseIndex.verseIndex }
    val selected = mutableSetOf<Int>()

    val items = ArrayList<BaseItem>()
    var heading: String? = null
    var segments = ArrayList<ParagraphItem.Segment>()
    fun flush() {
        if (segments.isNotEmpty()) {
            items.add(ParagraphItem(context, heading, segments, bookmarkSet, highlightMap, noteSet, selected, footnoteSet))
            heading = null
            segments = ArrayList()
        }
    }

    forEachIndexed { index, verse ->
        val text = verse.text.text
        if (text.isEmpty()) return@forEachIndexed

        var followingEmptyVerseCount = 0
        while (index + followingEmptyVerseCount + 1 < size
                && this[index + followingEmptyVerseCount + 1].text.text.isEmpty()) {
            followingEmptyVerseCount++
        }
        if (index + followingEmptyVerseCount + 1 >= size) followingEmptyVerseCount = 0

        val verseNumber = verse.verseIndex.verseIndex + 1
        var label: String? = if (followingEmptyVerseCount > 0) "$verseNumber-${verseNumber + followingEmptyVerseCount}" else verseNumber.toString()
        fun addSegment(part: String) {
            val trimmed = (if (segments.isEmpty()) part.trimStart('\n') else part).removeNotes()
            if (trimmed.isNotBlank()) {
                segments.add(ParagraphItem.Segment(verse, trimmed.trimEnd(), label))
                label = null
            }
        }

        // Headings can start anywhere in a verse, and each one starts a new paragraph.
        var cursor = 0
        for (match in STRUCTURE.findAll(text)) {
            addSegment(text.substring(cursor, match.range.first))
            flush()
            if (match.value.first() == HEADING) heading = match.groupValues[1]
            cursor = match.range.last + 1
        }
        addSegment(text.substring(cursor))
    }
    flush()
    return items
}

/** Text for the clipboard: Markdown quotes, with the full translation name in brackets after each reference. */
fun Collection<Verse>.toStringForCopy(bookName: String, translationName: String, consolidateVerses: Boolean): String =
        map { it.toDisplayedVerse() }.toStringForSharing(bookName, consolidateVerses, markdown = true, translationName = translationName)

fun Collection<Verse>.toStringForSharing(bookName: String, consolidateVerses: Boolean, markdown: Boolean = false,
                                         translationName: String? = null): String {
    val sortedVerses = sortedBy { verse ->
        val verseIndex = verse.verseIndex
        verseIndex.bookIndex * 100000 + verseIndex.chapterIndex * 1000 + verseIndex.verseIndex
    }

    if (!consolidateVerses || size == 1) {
        return StringBuilder().apply {
            sortedVerses.forEach { verse -> append(verse, bookName, markdown, translationName) }
        }.toString()
    }

    // format (without parallel):
    // <book name> <chapter index>:<start verse index>-<end verse index>
    // <verse text>
    // <book name> <chapter index>:<verse index>
    // <verse text>
    //
    // format (with parallel):
    // <book name> <chapter index>:<start verse index>-<end verse index>
    // <primary translation>: <verse text>
    // <parallel translation 1>: <verse text>
    // <parallel translation 2>: <verse text>
    // <book name> <chapter index>:<verse index>
    // <primary translation>: <verse text>
    // <parallel translation 1>: <verse text>
    // <parallel translation 2>: <verse text>

    // step 1: find all start - end verse index pairs
    val verseGroups = arrayListOf<Pair<Int, Int>>()
    sortedVerses.forEach { verse ->
        val lastVerseIndexPair = verseGroups.lastOrNull()
        if (lastVerseIndexPair != null && lastVerseIndexPair.second + 1 == verse.verseIndex.verseIndex) {
            verseGroups[verseGroups.size - 1] = lastVerseIndexPair.copy(second = verse.verseIndex.verseIndex)
        } else {
            verseGroups.add(Pair(verse.verseIndex.verseIndex, verse.verseIndex.verseIndex))
        }
    }

    // step 2: build the string for sharing
    val stringBuilder = StringBuilder()

    var currentVerseGroupIndex = 0
    var bodyStart = 0
    val parallelVersesBuilder = Array(sortedVerses.first().parallel.size) { StringBuilder() }
    sortedVerses.forEach { verse ->
        val currentVerseGroup = verseGroups[currentVerseGroupIndex]

        // start of the verse group
        if (verse.verseIndex.verseIndex == currentVerseGroup.first) {
            if (stringBuilder.isNotEmpty()) stringBuilder.append("\n\n")

            stringBuilder.append(bookName).append(' ')
                    .append(verse.verseIndex.chapterIndex + 1).append(':').append(verse.verseIndex.verseIndex + 1)
            if (currentVerseGroup.first < currentVerseGroup.second) {
                stringBuilder.append('-').append(currentVerseGroup.second + 1)
            }
            if (markdown) translationName?.let { stringBuilder.append(" (").append(it).append(')') }
            stringBuilder.append('\n')
            bodyStart = stringBuilder.length

            if (verse.parallel.isNotEmpty()) {
                stringBuilder.append(verse.text.translationShortName).append(": ")

                verse.parallel.forEachIndexed { index, parallel ->
                    with(parallelVersesBuilder[index]) {
                        clear()
                        append(parallel.translationShortName).append(": ")
                    }
                }
            }
        }

        if (verse.verseIndex.verseIndex > currentVerseGroup.first) stringBuilder.append(' ')
        stringBuilder.append(verse.text.text)

        verse.parallel.forEachIndexed { index, parallel ->
            with(parallelVersesBuilder[index]) {
                if (verse.verseIndex.verseIndex > currentVerseGroup.first) append(' ')
                append(parallel.text)
            }
        }

        // end of the verse group
        if (verse.verseIndex.verseIndex == currentVerseGroup.second) {
            parallelVersesBuilder.forEach { parallelBuilder ->
                stringBuilder.append('\n').append(parallelBuilder)
            }
            if (markdown) {
                val body = stringBuilder.substring(bodyStart)
                stringBuilder.setLength(bodyStart)
                stringBuilder.append(body.quoted())
            }

            currentVerseGroupIndex++
        }
    }

    return stringBuilder.toString()
}

private fun String.quoted(): String = split('\n').joinToString("\n") { "> $it" }

private fun StringBuilder.append(verse: Verse, bookName: String, markdown: Boolean, translationName: String?): StringBuilder {
    if (isNotEmpty()) append(if (markdown) "\n\n" else "\n")

    if (markdown) {
        // format:
        // <book name> <chapter index>:<verse index> (<translation name>)
        // > <verse text>
        // or, with parallel translations, one "> <translation>: <verse text>" line per translation
        append(bookName).append(' ')
                .append(verse.verseIndex.chapterIndex + 1).append(':').append(verse.verseIndex.verseIndex + 1)
        translationName?.let { append(" (").append(it).append(')') }
        append('\n')
        val body = if (verse.parallel.isEmpty()) verse.text.text else {
            (listOf(verse.text) + verse.parallel).joinToString("\n") { "${it.translationShortName}: ${it.text}" }
        }
        append(body.quoted())
    } else if (verse.parallel.isEmpty()) {
        // format: <book name> <chapter index>:<verse index> <text>
        append(bookName).append(' ')
                .append(verse.verseIndex.chapterIndex + 1).append(':').append(verse.verseIndex.verseIndex + 1).append(' ')
                .append(verse.text.text)
    } else {
        // format:
        // <book name> <chapter verseIndex>:<verse verseIndex>
        // <primary translation>: <verse text>
        // <parallel translation 1>: <verse text>
        // <parallel translation 2>: <verse text>
        append(bookName).append(' ')
                .append(verse.verseIndex.chapterIndex + 1).append(':').append(verse.verseIndex.verseIndex + 1).append('\n')
                .append(verse.text.translationShortName).append(": ").append(verse.text.text).append('\n')
        verse.parallel.forEach { text -> append(text.translationShortName).append(": ").append(text.text).append('\n') }
        setLength(length - 1) // remove the appended space
    }
    return this
}

fun Verse.toStringForSharing(bookName: String): String = StringBuilder().append(this, bookName, false, null).toString()

fun Verse.toStringForCopy(bookName: String, translationName: String): String =
        StringBuilder().append(toDisplayedVerse(), bookName, true, translationName).toString()
