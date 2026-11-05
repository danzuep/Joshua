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

abstract class VerseAnnotation(open val verseIndex: VerseIndex, open val timestamp: Long) {
    open fun isValid(): Boolean = verseIndex.isValid() && timestamp > 0L
}

data class Bookmark(override val verseIndex: VerseIndex, override val timestamp: Long) : VerseAnnotation(verseIndex, timestamp)

data class Highlight(override val verseIndex: VerseIndex, val color: Int, override val timestamp: Long) : VerseAnnotation(verseIndex, timestamp) {
    companion object {
        const val COLOR_NONE = 0
        const val COLOR_YELLOW = 0xFFFFFF00.toInt()
        const val COLOR_PINK = 0xFFFFC0CB.toInt()
        const val COLOR_ORANGE = 0xFFFF8C00.toInt()
        const val COLOR_PURPLE = 0xFFFF00FF.toInt()
        const val COLOR_RED = 0xFFDE0100.toInt()
        const val COLOR_GREEN = 0xFF00FF00.toInt()
        const val COLOR_BLUE = 0xFF0000FF.toInt()
        val AVAILABLE_COLORS = arrayOf(COLOR_NONE, COLOR_YELLOW, COLOR_PINK, COLOR_ORANGE, COLOR_PURPLE, COLOR_RED, COLOR_GREEN, COLOR_BLUE)

        @Retention(AnnotationRetention.SOURCE)
        annotation class AvailableColor
    }

    override fun isValid(): Boolean = super.isValid() && AVAILABLE_COLORS.contains(color)
}

data class Note(override val verseIndex: VerseIndex, val note: String, override val timestamp: Long) : VerseAnnotation(verseIndex, timestamp)
