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

package me.xizzhu.android.joshua.core.repository.remote

import me.xizzhu.android.joshua.core.HEADING
import me.xizzhu.android.joshua.core.PARAGRAPH
import java.io.InputStream
import java.util.TreeMap
import java.util.zip.ZipInputStream

/** Protestant canon in Joshua's order (0-based book index = position in this list). */
internal val BOOK_CODES: List<String> = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM " +
        "EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL " +
        "1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split(" ")

/** A book of scripture, independent of the markup (USFM, USX, ...) it was read from. Verse numbers are 1-based. */
class ScriptureBook(val code: String, val name: String?, val chapters: Map<Int, Map<Int, String>>)

private val NOTE = Regex("""\\(f|fe|x|ef|ex)\b.*?\\\1\*""", RegexOption.DOT_MATCHES_ALL)
private val WORD_ATTRIBUTES = Regex("""\|[^\\]*?(?=\\\+?w\*)""")
private val MARKER = Regex("""\\\+?[a-z0-9-]+\*|\\\+?[a-z0-9-]+\s?""")
private val WHITESPACE = Regex("""\s+""")
private val VERSE = Regex("""\\v\s+(\d+)(?:-(\d+))?[a-z]?\s*""")
private val NON_SCRIPTURE = Regex("""^\\(?:ms\d?|mr|s\d?|sr|r|d|sp|cl|cd|cp|qa|rem|h|toc\d|mt\d?|imt\d?|is\d?|ip|id|b)\b""")

/** Strips notes, word attributes and markers, keeping the scripture text. */
internal fun stripUsfmMarkup(text: String): String =
        text.replace(NOTE, "").replace(WORD_ATTRIBUTES, "").replace(MARKER, "").replace(WHITESPACE, " ").trim()

private val HEADING_LINE = Regex("""^\\(?:s\d?|ms\d?)\s+(.*)""")
private val PARAGRAPH_LINE = Regex("""^\\(?:p|m|pi\d?|mi|pm[a-z]?|po|pr|cls|li\d?|lim\d?|ph\d?)(?:\s+(.*))?$""")
private val POETRY_LINE = Regex("""^\\q(\d?)(?:\s+(.*))?$""")

/**
 * Parses USFM into verses that keep their structure, see [PARAGRAPH] and [HEADING]: paragraph markers, section
 * headings and poetry lines are stored in the verse text, which readers that don't care remove with `stripStructure()`.
 */
internal fun parseUsfm(content: String): ScriptureBook {
    var code: String? = null
    var name: String? = null
    val chapters = TreeMap<Int, MutableMap<Int, String>>()
    var chapter = 0
    var verse = -1
    // The break and heading that go in front of the next text.
    var pendingBreak = ""
    var pendingHeading: String? = null

    for (rawLine in content.removePrefix("\uFEFF").lines()) {
        var line = rawLine.trim()
        when {
            line.isEmpty() -> continue
            line.startsWith("\\id ") -> { code = line.split(WHITESPACE)[1].uppercase(); continue }
            line.startsWith("\\toc2 ") || (line.startsWith("\\h ") && name == null) -> {
                name = stripUsfmMarkup(line.substringAfter(' ')).ifEmpty { name }
                continue
            }
            line.startsWith("\\c ") -> {
                chapter = line.split(WHITESPACE)[1].toInt()
                verse = -1
                pendingBreak = ""
                chapters.getOrPut(chapter) { TreeMap() }
                continue
            }
        }

        val headingLine = HEADING_LINE.find(line)
        if (headingLine != null) {
            pendingHeading = stripUsfmMarkup(headingLine.groupValues[1]).ifEmpty { null }
            pendingBreak = PARAGRAPH.toString()
            continue
        }
        if (NON_SCRIPTURE.containsMatchIn(line) || chapter == 0) continue

        val paragraph = PARAGRAPH_LINE.find(line)
        val poetry = POETRY_LINE.find(line)
        if (paragraph != null) {
            pendingBreak = PARAGRAPH.toString()
            line = paragraph.groupValues[1]
        } else if (poetry != null) {
            if (pendingBreak != PARAGRAPH.toString()) {
                pendingBreak = "\n" + "\u2003".repeat(((poetry.groupValues[1].toIntOrNull() ?: 1) - 1).coerceAtLeast(0))
            }
            line = poetry.groupValues[2]
        }

        val verses = chapters.getValue(chapter)
        fun append(text: String) {
            val cleaned = stripUsfmMarkup(text)
            if (verse < 0 || cleaned.isEmpty()) return

            val heading = pendingHeading?.let { "$HEADING$it$HEADING" } ?: ""
            val existing = verses.getValue(verse)
            verses[verse] = when {
                existing.isEmpty() -> heading + pendingBreak + cleaned
                else -> existing + heading + pendingBreak.ifEmpty { " " } + cleaned
            }
            pendingHeading = null
            pendingBreak = ""
        }

        var cursor = 0
        for (match in VERSE.findAll(line)) {
            append(line.substring(cursor, match.range.first))
            verse = match.groupValues[1].toInt()
            verses[verse] = ""
            // Bridged verses (e.g. 2-3): the text lives in the first verse.
            val end = match.groupValues[2].ifEmpty { null }?.toInt() ?: verse
            for (bridged in verse + 1..end) verses[bridged] = ""
            cursor = match.range.last + 1
        }
        append(line.substring(cursor))
    }
    return ScriptureBook(requireNotNull(code) { "Missing \\id marker" }, name, chapters)
}

/** Reads a USFM zip (as published by eBible.org) into the books of the 66 book canon, keyed by USFM code. */
internal fun readUsfmZip(input: InputStream): Map<String, ScriptureBook> {
    val books = hashMapOf<String, ScriptureBook>()
    ZipInputStream(input).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            val entryName = entry.name.lowercase()
            if (!entry.isDirectory && (entryName.endsWith(".usfm") || entryName.endsWith(".sfm"))) {
                val book = parseUsfm(zip.readBytes().toString(Charsets.UTF_8))
                if (book.code in BOOK_CODES) books[book.code] = book
            }
        }
    }
    return books
}

/**
 * Builds a [RemoteTranslation] from [books]. Books and chapters missing from the source (e.g. New Testament only
 * translations) are left empty. Verses missing inside a chapter (e.g. omitted by a critical text) become empty strings
 * to keep the numbering aligned.
 */
internal fun toRemoteTranslation(translationInfo: RemoteTranslationInfo, books: Map<String, ScriptureBook>): RemoteTranslation {
    require(books.isNotEmpty()) { "No books found for ${translationInfo.shortName}" }

    val bookNames = BOOK_CODES.map { code -> books[code]?.name ?: code }
    val verses = hashMapOf<Pair<Int, Int>, List<String>>()
    BOOK_CODES.forEachIndexed { bookIndex, code ->
        books[code]?.chapters?.forEach { (chapter, chapterVerses) ->
            val last = chapterVerses.keys.maxOrNull() ?: return@forEach
            verses[Pair(bookIndex, chapter - 1)] = (1..last).map { chapterVerses[it] ?: "" }
        }
    }
    return RemoteTranslation(translationInfo, bookNames, bookNames, verses)
}
