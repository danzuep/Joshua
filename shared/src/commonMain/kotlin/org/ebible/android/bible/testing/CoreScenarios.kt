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

package org.ebible.android.bible.testing

import org.ebible.android.bible.core.Bookmark
import org.ebible.android.bible.core.Highlight
import org.ebible.android.bible.core.Note
import org.ebible.android.bible.core.Settings
import org.ebible.android.bible.core.TranslationInfo
import org.ebible.android.bible.core.Verse
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.core.repository.SettingsRepository
import org.ebible.android.bible.core.repository.VerseAnnotationRepository
import org.ebible.android.bible.core.repository.local.LocalReadingStorage
import org.ebible.android.bible.core.repository.local.LocalTranslationStorage

/**
 * Fluent steps over the core repositories, mirroring the app UI builders so a scenario can run on the JVM
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


    suspend fun defaultHighlightColor(color: Int) = update { copy(defaultHighlightColor = color) }
}

/**
 * Fluent reader navigation over [LocalReadingStorage]: every step saves the position, so the next step (or a test)
 * reads it back exactly as the app would.
 *
 * ```
 * CoreReader(storage).toTranslation("WEBU").toBook(42).toChapter(0).toVerse(4).expectVerseIndex(VerseIndex(42, 0, 4))
 * ```
 *
 * To drive the running app, pass a storage whose saves go through the app's own `BibleReadingRepository`: the app
 * caches the position in memory, so writes made through a separate database instance are not seen.
 */
class CoreReader(private val storage: LocalReadingStorage, private var position: VerseIndex = VerseIndex(0, 0, 0)) {
    private suspend fun moveTo(verseIndex: VerseIndex): CoreReader {
        position = verseIndex
        storage.saveCurrentVerseIndex(verseIndex)
        return this
    }

    suspend fun toTranslation(shortName: String): CoreReader {
        storage.saveCurrentTranslation(shortName)
        return this
    }

    suspend fun withParallel(vararg shortNames: String): CoreReader {
        storage.saveParallelTranslations(shortNames.toList())
        return this
    }

    suspend fun toBook(bookIndex: Int): CoreReader = moveTo(VerseIndex(bookIndex, 0, 0))

    suspend fun toChapter(chapterIndex: Int): CoreReader = moveTo(position.copy(chapterIndex = chapterIndex, verseIndex = 0))

    suspend fun toVerse(verseIndex: Int): CoreReader = moveTo(position.copy(verseIndex = verseIndex))

    suspend fun expectVerseIndex(expected: VerseIndex): CoreReader {
        check(storage.readCurrentVerseIndex() == expected) { "expected $expected but was ${storage.readCurrentVerseIndex()}" }
        return this
    }

    suspend fun expectTranslation(shortName: String): CoreReader {
        check(storage.readCurrentTranslation() == shortName) { "expected $shortName but was ${storage.readCurrentTranslation()}" }
        return this
    }

    /** Verses of the current chapter in the current translation. */
    suspend fun verses(): List<Verse> =
        storage.readVerses(storage.readCurrentTranslation(), storage.readParallelTranslations(), position.bookIndex, position.chapterIndex)

    suspend fun expectVerseText(verseIndex: Int, text: String): CoreReader {
        val actual = verses().firstOrNull { it.verseIndex.verseIndex == verseIndex }?.text?.text
        check(actual == text) { "expected verse $verseIndex to be '$text' but was '$actual'" }
        return this
    }
}

/** Fluent translation list steps over [LocalTranslationStorage]: filter, download (install) and remove. */
class CoreTranslations(private val storage: LocalTranslationStorage) {
    private var query = ""

    suspend fun available(vararg translations: TranslationInfo): CoreTranslations {
        storage.replaceTranslations(translations.toList())
        return this
    }

    /** Every whitespace-separated word must match the name, short name or language, like the filter dialog. */
    fun filter(query: String): CoreTranslations {
        this.query = query.trim()
        return this
    }

    suspend fun visible(): List<TranslationInfo> {
        val words = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return storage.readTranslations().filter { info ->
            val haystack = "${info.shortName} ${info.name} ${info.language}"
            words.all { haystack.contains(it, ignoreCase = true) }
        }
    }

    /** Installs the translation with the given short name, with the given verses keyed by (book, chapter). */
    suspend fun download(
        shortName: String,
        bookNames: List<String> = emptyList(),
        bookShortNames: List<String> = bookNames,
        verses: Map<Pair<Int, Int>, List<String>> = emptyMap(),
    ): CoreTranslations {
        val info = storage.readTranslations().first { it.shortName == shortName }
        storage.saveTranslation(info.copy(downloaded = true), bookNames, bookShortNames, verses)
        return this
    }

    suspend fun remove(shortName: String): CoreTranslations {
        storage.removeTranslation(storage.readTranslations().first { it.shortName == shortName })
        return this
    }

    suspend fun expectDownloaded(vararg shortNames: String): CoreTranslations {
        val actual = storage.readTranslations().filter { it.downloaded }.map { it.shortName }.toSet()
        check(actual == shortNames.toSet()) { "expected downloaded ${shortNames.toSet()} but was $actual" }
        return this
    }
}
