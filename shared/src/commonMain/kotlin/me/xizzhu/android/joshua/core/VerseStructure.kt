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

/**
 * USFM structure kept inside the stored verse text so that chapters can be read as flowing paragraphs:
 * [PARAGRAPH] starts a new paragraph, a heading is wrapped in [HEADING] (at the start of a verse), and poetry lines
 * are separated by '\n' (indented with em spaces). Footnotes and cross-references are wrapped in [NOTE_MARK]s, see
 * [extractNotes]. Everything outside the reading screen sees [stripStructure]d text.
 */
const val PARAGRAPH = '\u2029'
const val HEADING = '\u2028'
const val NOTE_MARK = '\u2027'

private val HEADING_REGEX = Regex("$HEADING[^$HEADING]*$HEADING")
private val NOTE_REGEX = Regex("$NOTE_MARK[fx][^$NOTE_MARK]*$NOTE_MARK")
private val NOTE_CONTENT_REGEX = Regex("$NOTE_MARK([fx])([^$NOTE_MARK]*)$NOTE_MARK")
private val HEADING_CONTENT_REGEX = Regex("$HEADING([^$HEADING]*)$HEADING")

/** A footnote ([isCrossReference] = false) or a cross-reference of a verse. */
class VerseNote(val isCrossReference: Boolean, val text: String)

/** The footnotes and cross-references of a verse, in the order they appear. */
fun String.extractNotes(): List<VerseNote> =
        NOTE_CONTENT_REGEX.findAll(this).map { VerseNote(it.groupValues[1] == "x", it.groupValues[2]) }.toList()

/** The section headings of a verse, in the order they appear. */
fun String.extractHeadings(): List<String> =
        HEADING_CONTENT_REGEX.findAll(this).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()

fun String.hasNotes(): Boolean = indexOf(NOTE_MARK) >= 0

/** The text without its footnotes and cross-references. */
fun String.removeNotes(): String = if (indexOf(NOTE_MARK) < 0) this else replace(NOTE_REGEX, "")

/** The plain verse text, without headings, notes, paragraph marks or the line breaks of poetry. */
fun String.stripStructure(): String =
        if (indexOf(PARAGRAPH) < 0 && indexOf(HEADING) < 0 && indexOf(NOTE_MARK) < 0 && indexOf('\n') < 0) this
        else removeNotes().replace(HEADING_REGEX, "").replace(PARAGRAPH, ' ').replace(Regex("[\\s\u2003]+"), " ").trim()

fun Verse.stripStructure(): Verse = copy(
        text = text.copy(text = text.text.stripStructure()),
        parallel = parallel.map { it.copy(text = it.text.stripStructure()) }
)

/** The text as the reading screen shows it: headings and notes removed, paragraph and poetry line breaks kept as '\n'. */
fun String.toDisplayedText(): String =
        removeNotes().replace(HEADING_REGEX, "").replace(PARAGRAPH, '\n').trim { it == '\n' || it == ' ' }

fun Verse.toDisplayedVerse(): Verse = copy(
        text = text.copy(text = text.text.toDisplayedText()),
        parallel = parallel.map { it.copy(text = it.text.toDisplayedText()) }
)
