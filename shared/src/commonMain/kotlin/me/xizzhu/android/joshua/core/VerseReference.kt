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

/** A reference found in a note: [text] in `[start, end)` points at [verseIndex], the first verse of a range. */
data class VerseReference(val start: Int, val end: Int, val verseIndex: VerseIndex)

private val ENGLISH_BOOK_NAMES = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
        "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job", "Psalms", "Proverbs",
        "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel",
        "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
        "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians",
        "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon",
        "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"
)

// Abbreviations that are not a prefix of the book name.
private val BOOK_ALIASES = mapOf(
        "jn" to 42, "jhn" to 42, "1jn" to 61, "2jn" to 62, "3jn" to 63, "mt" to 39, "mk" to 40, "lk" to 41,
        "jdg" to 6, "php" to 49, "phm" to 56, "song" to 21, "sng" to 21, "sos" to 21, "rev" to 65, "ps" to 18,
        "psalm" to 18, "1sam" to 8, "2sam" to 9
)

private val REFERENCE_REGEX = Regex("""(\d+):(\d+)(?:\s*[-\u2013](\d+)(?![\d:]))?""")
private val CONTINUATION_REGEX = Regex("""^\s*,\s*(\d+)(?:\s*[-\u2013]\s*(\d+)(?![\d:]))?(?![\d:])""")

private val WORD_REGEX = Regex("""\S+""")

private fun String.normalizedBookName(): String = filter { it.isLetterOrDigit() }.lowercase()

private fun bookLookup(bookNames: List<String>): (String) -> Int? {
    val keys = ArrayList<Pair<String, Int>>()
    bookNames.forEachIndexed { index, name -> if (index < Bible.BOOK_COUNT) keys.add(name.normalizedBookName() to index) }
    ENGLISH_BOOK_NAMES.forEachIndexed { index, name -> keys.add(name.normalizedBookName() to index) }
    return lookup@{ candidate ->
        val key = candidate.normalizedBookName()
        if (key.length < 2) return@lookup null
        keys.firstOrNull { it.first == key }?.let { return@lookup it.second }
        BOOK_ALIASES[key]?.let { return@lookup it }
        if (key.length < 3) return@lookup null
        keys.filter { it.first.startsWith(key) }.map { it.second }.distinct().singleOrNull()
    }
}

/**
 * Finds the verse references of a footnote or cross-reference, e.g. "Gen 1:1-3; Job 3:4,5-6; 7:8". A reference without
 * a book belongs to the previous book, or to [defaultBookIndex] if there is none. The numbers after a comma are
 * verses of the same chapter and are references of their own. [bookNames] are the names used by the text, English
 * names are always understood.
 */
fun String.findVerseReferences(bookNames: List<String>, defaultBookIndex: Int): List<VerseReference> {
    val findBook = bookLookup(bookNames)
    val references = mutableListOf<VerseReference>()
    var book = defaultBookIndex
    var segmentStart = 0

    for (match in REFERENCE_REGEX.findAll(this)) {
        if (match.range.first < segmentStart) continue

        var start = match.range.first
        val words = WORD_REGEX.findAll(substring(segmentStart, start)).filter { it.value.first().isLetterOrDigit() }.toList()
        for (count in minOf(words.size, 4) downTo 1) {
            val first = words[words.size - count]
            val found = findBook(substring(segmentStart + first.range.first, start)) ?: continue
            book = found
            start = segmentStart + first.range.first
            break
        }

        val chapter = match.groupValues[1].toInt() - 1
        val verse = match.groupValues[2].toInt() - 1
        var end = match.range.last + 1
        val chapterIsValid = book in 0 until Bible.BOOK_COUNT && chapter in 0 until Bible.getChapterCount(book)
        if (chapterIsValid && verse >= 0) references.add(VerseReference(start, end, VerseIndex(book, chapter, verse)))

        while (true) {
            val next = CONTINUATION_REGEX.find(substring(end)) ?: break
            val number = next.groups[1]!!
            val numberStart = end + number.range.first
            end += next.range.last + 1
            if (chapterIsValid && number.value.toInt() > 0) {
                references.add(VerseReference(numberStart, end, VerseIndex(book, chapter, number.value.toInt() - 1)))
            }
        }
        segmentStart = end
    }
    return references
}
