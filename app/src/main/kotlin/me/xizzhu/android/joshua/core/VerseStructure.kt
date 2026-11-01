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
 * are separated by '\n' (indented with em spaces). Everything outside the reading screen sees [stripStructure]d text.
 */
const val PARAGRAPH = '\u2029'
const val HEADING = '\u2028'

private val HEADING_REGEX = Regex("$HEADING[^$HEADING]*$HEADING")

/** The plain verse text, without headings, paragraph marks or the line breaks of poetry. */
fun String.stripStructure(): String =
        if (indexOf(PARAGRAPH) < 0 && indexOf(HEADING) < 0 && indexOf('\n') < 0) this
        else replace(HEADING_REGEX, "").replace(PARAGRAPH, ' ').replace(Regex("[\\s\u2003]+"), " ").trim()

fun Verse.stripStructure(): Verse = copy(
        text = text.copy(text = text.text.stripStructure()),
        parallel = parallel.map { it.copy(text = it.text.stripStructure()) }
)

/** The text as the reading screen shows it: headings removed, paragraph and poetry line breaks kept as '\n'. */
fun String.toDisplayedText(): String =
        replace(HEADING_REGEX, "").replace(PARAGRAPH, '\n').trim { it == '\n' || it == ' ' }

fun Verse.toDisplayedVerse(): Verse = copy(
        text = text.copy(text = text.text.toDisplayedText()),
        parallel = parallel.map { it.copy(text = it.text.toDisplayedText()) }
)
