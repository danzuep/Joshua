/*
 * Copyright (C) 2026 eBible
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

package me.xizzhu.android.joshua.testing

import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.core.repository.SettingsRepository
import me.xizzhu.android.joshua.core.repository.VerseAnnotationRepository

/**
 * Fluent steps over the core repositories, mirroring the UI builders in [Joshua] so a scenario can run on the JVM
 * without an emulator:
 *
 * ```
 * CoreVerse(VerseIndex(42, 0, 0), bookmarks, highlights, notes).bookmark().highlight(Highlight.COLOR_YELLOW).upsertNote("hi")
 * ```
 *
 * Each step is a suspend call that returns the builder, so steps chain inside a coroutine.
 */
class CoreVerse(
    val verseIndex: VerseIndex,
    private val bookmarks: VerseAnnotationRepository<Bookmark>,
    private val highlights: VerseAnnotationRepository<Highlight>,
    private val notes: VerseAnnotationRepository<Note>,
    private val now: () -> Long = { 1L },
) {
    suspend fun bookmark(): CoreVerse {
        bookmarks.save(Bookmark(verseIndex, now()))
        return this
    }

    suspend fun removeBookmark(): CoreVerse {
        bookmarks.remove(verseIndex)
        return this
    }

    suspend fun highlight(color: Int = Highlight.COLOR_YELLOW): CoreVerse {
        if (color == Highlight.COLOR_NONE) highlights.remove(verseIndex) else highlights.save(Highlight(verseIndex, color, now()))
        return this
    }

    suspend fun upsertNote(note: String): CoreVerse {
        if (note.isEmpty()) notes.remove(verseIndex) else notes.save(Note(verseIndex, note, now()))
        return this
    }

    suspend fun navigateTo(bookIndex: Int, chapterIndex: Int = 0, verseIndex: Int = 0): CoreVerse =
        CoreVerse(VerseIndex(bookIndex, chapterIndex, verseIndex), bookmarks, highlights, notes, now)
}

/** Fluent changes to [Settings]; each step saves the updated settings. */
class CoreSettings(private val repository: SettingsRepository, private var current: Settings = Settings.DEFAULT) {
    private suspend fun update(change: Settings.() -> Settings): CoreSettings {
        current = current.change()
        repository.saveSettings(current)
        return this
    }

    suspend fun keepScreenOn(on: Boolean = true) = update { copy(keepScreenOn = on) }

    suspend fun nightMode(mode: Int) = update { copy(nightMode = mode) }

    suspend fun fontSizeScale(scale: Float) = update { copy(fontSizeScale = scale.coerceIn(Settings.MIN_FONT_SIZE_SCALE, Settings.MAX_FONT_SIZE_SCALE)) }

    suspend fun simpleReadingMode(on: Boolean = true) = update { copy(simpleReadingModeOn = on) }

    suspend fun hideSearchButton(on: Boolean = true) = update { copy(hideSearchButton = on) }

    suspend fun consolidateVersesForSharing(on: Boolean = true) = update { copy(consolidateVersesForSharing = on) }

    suspend fun defaultHighlightColor(color: Int) = update { copy(defaultHighlightColor = color) }
}
