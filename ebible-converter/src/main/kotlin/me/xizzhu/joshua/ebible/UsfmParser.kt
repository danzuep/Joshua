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

package me.xizzhu.joshua.ebible

/** Protestant canon in Joshua's order (0-based book index = position in this list). */
internal val BOOK_CODES = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM " +
        "EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL " +
        "1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split(" ")

internal val CHAPTER_COUNTS = intArrayOf(
        50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150, 31, 12, 8, 66, 52, 5, 48,
        12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4, 28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3,
        1, 13, 5, 5, 3, 5, 1, 1, 1, 22)

class UsfmBook(val code: String, val name: String?, val chapters: Map<Int, Map<Int, String>>)

private val NOTE = Regex("""\\(f|fe|x|ef|ex)\b.*?\\\1\*""", RegexOption.DOT_MATCHES_ALL)
private val WORD_ATTRIBUTES = Regex("""\|[^\\]*?(?=\\\+?w\*)""")
private val MARKER = Regex("""\\\+?[a-z0-9-]+\*?\s?""")
private val WHITESPACE = Regex("""\s+""")
private val VERSE = Regex("""\\v\s+(\d+)(?:-(\d+))?[a-z]?\s*""")
private val NON_SCRIPTURE = Regex("""^\\(?:ms\d?|mr|s\d?|sr|r|d|sp|cl|cd|cp|qa|rem|h|toc\d|mt\d?|imt\d?|is\d?|ip|id|b)\b""")

/** Strips notes, word attributes and markers, keeping the scripture text. */
internal fun stripMarkup(text: String): String =
        text.replace(NOTE, "").replace(WORD_ATTRIBUTES, "").replace(MARKER, "").replace(WHITESPACE, " ").trim()

fun parseUsfm(content: String): UsfmBook {
    var code: String? = null
    var name: String? = null
    val chapters = sortedMapOf<Int, MutableMap<Int, String>>()
    var chapter = 0
    var verse = -1

    for (rawLine in content.removePrefix("\uFEFF").lines()) {
        val line = rawLine.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("\\id ") -> code = line.split(WHITESPACE)[1].uppercase()
            line.startsWith("\\toc2 ") || (line.startsWith("\\h ") && name == null) ->
                name = stripMarkup(line.substringAfter(' ')).ifEmpty { name }
            line.startsWith("\\c ") -> {
                chapter = line.split(WHITESPACE)[1].toInt()
                verse = -1
                chapters.getOrPut(chapter) { sortedMapOf() }
            }
            NON_SCRIPTURE.containsMatchIn(line) || chapter == 0 -> Unit
            else -> {
                val verses = chapters.getValue(chapter)
                var cursor = 0
                for (match in VERSE.findAll(line)) {
                    appendToCurrent(verses, verse, line.substring(cursor, match.range.first))
                    verse = match.groupValues[1].toInt()
                    verses[verse] = ""
                    // Bridged verses (e.g. 2-3): the text lives in the first verse.
                    val end = match.groupValues[2].ifEmpty { null }?.toInt() ?: verse
                    for (bridged in verse + 1..end) verses[bridged] = ""
                    cursor = match.range.last + 1
                }
                appendToCurrent(verses, verse, line.substring(cursor))
            }
        }
    }
    return UsfmBook(requireNotNull(code) { "Missing \\id marker" }, name, chapters)
}

private fun appendToCurrent(verses: MutableMap<Int, String>, verse: Int, text: String) {
    val cleaned = stripMarkup(text)
    if (verse < 0 || cleaned.isEmpty()) return
    verses[verse] = listOf(verses.getValue(verse), cleaned).filter { it.isNotEmpty() }.joinToString(" ")
}
