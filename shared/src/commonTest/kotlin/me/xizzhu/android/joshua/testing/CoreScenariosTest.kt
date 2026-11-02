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

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xizzhu.android.joshua.core.Bookmark
import me.xizzhu.android.joshua.core.Constants
import me.xizzhu.android.joshua.core.Highlight
import me.xizzhu.android.joshua.core.Note
import me.xizzhu.android.joshua.core.Settings
import me.xizzhu.android.joshua.core.VerseAnnotation
import me.xizzhu.android.joshua.core.VerseIndex
import me.xizzhu.android.joshua.core.repository.SettingsRepository
import me.xizzhu.android.joshua.core.repository.VerseAnnotationRepository
import me.xizzhu.android.joshua.core.repository.local.LocalSettingsStorage
import me.xizzhu.android.joshua.core.repository.local.LocalVerseAnnotationStorage
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreScenariosTest {
    private class InMemoryAnnotations<T : VerseAnnotation> : LocalVerseAnnotationStorage<T> {
        val items = linkedMapOf<VerseIndex, T>()
        override suspend fun readSortOrder(): Int = Constants.DEFAULT_SORT_ORDER
        override suspend fun saveSortOrder(sortOrder: Int) {}
        override suspend fun read(sortOrder: Int): List<T> = items.values.toList()
        override suspend fun read(bookIndex: Int, chapterIndex: Int): List<T> =
            items.values.filter { it.verseIndex.bookIndex == bookIndex && it.verseIndex.chapterIndex == chapterIndex }
        override suspend fun read(verseIndex: VerseIndex): T = items.getValue(verseIndex)
        override suspend fun search(query: String): List<T> = emptyList()
        override suspend fun save(verseAnnotation: T) { items[verseAnnotation.verseIndex] = verseAnnotation }
        override suspend fun save(verseAnnotations: List<T>) = verseAnnotations.forEach { save(it) }
        override suspend fun remove(verseIndex: VerseIndex) { items.remove(verseIndex) }
    }

    private class InMemorySettings : LocalSettingsStorage {
        var saved = Settings.DEFAULT
        override suspend fun readSettings(): Settings = saved
        override suspend fun saveSettings(settings: Settings) { saved = settings }
    }

    @Test
    fun verseActionsChain() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val bookmarkStorage = InMemoryAnnotations<Bookmark>()
        val highlightStorage = InMemoryAnnotations<Highlight>()
        val noteStorage = InMemoryAnnotations<Note>()
        val verse = CoreVerse(
            VerseIndex(0, 0, 0),
            VerseAnnotationRepository(bookmarkStorage, scope), VerseAnnotationRepository(highlightStorage, scope), VerseAnnotationRepository(noteStorage, scope)
        )

        verse.bookmark().highlight(Highlight.COLOR_PINK).upsertNote("first")
            .navigateTo(42, 0, 0).bookmark().upsertNote("second").upsertNote("")

        assertEquals(setOf(VerseIndex(0, 0, 0), VerseIndex(42, 0, 0)), bookmarkStorage.items.keys)
        assertEquals(Highlight.COLOR_PINK, highlightStorage.items.getValue(VerseIndex(0, 0, 0)).color)
        assertEquals(listOf("first"), noteStorage.items.values.map { it.note })
    }

    @Test
    fun settingsChain() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val storage = InMemorySettings()
        val repository = SettingsRepository(storage, scope)
        scope.advanceUntilIdle()

        CoreSettings(repository).keepScreenOn(false).nightMode(Settings.NIGHT_MODE_ON).fontSizeScale(10F).hideSearchButton()

        assertEquals(
            Settings.DEFAULT.copy(keepScreenOn = false, nightMode = Settings.NIGHT_MODE_ON, fontSizeScale = Settings.MAX_FONT_SIZE_SCALE, hideSearchButton = true),
            storage.saved
        )
    }
}
