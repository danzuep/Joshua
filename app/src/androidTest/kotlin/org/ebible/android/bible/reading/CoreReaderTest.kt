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

package org.ebible.android.bible.reading

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.ebible.android.bible.DebugEntryPoints
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.core.repository.BibleReadingRepository
import org.ebible.android.bible.core.repository.local.LocalReadingStorage
import org.ebible.android.bible.core.repository.local.android.AndroidReadingStorage
import org.ebible.android.bible.core.repository.local.android.db.AndroidDatabase
import org.ebible.android.bible.testing.CoreReader
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Seeds the reading position with the shared [CoreReader] and checks that the real reader opens there.
 *
 * Root cause of the earlier failure: the app's singleton [BibleReadingRepository] loads the position from SQLite once
 * and then serves it from memory, so a write through a separate [AndroidDatabase] is never seen by the running app.
 * [AppReadingStorage] therefore sends the saves through that repository, which updates its memory and SQLite together.
 */
class CoreReaderTest {
    private companion object {
        const val JOHN = 42
        const val TIMEOUT_MS = 20_000L
        const val VERSE_29 = "takes away the sin of the world"

        private val context: Context get() = ApplicationProvider.getApplicationContext()

        private val repository: BibleReadingRepository
            get() = EntryPointAccessors.fromApplication(context, DebugEntryPoints::class.java).bibleReadingRepository()

        /** The first launch imports the bundled translation, so wait for it once. */
        @BeforeClass
        @JvmStatic
        fun importBundledTranslation() {
            val intent = Intent(context, ReadingActivity::class.java).putExtra(ReadingActivity.KEY_OPEN_BOOK, JOHN)
            ActivityScenario.launch<ReadingActivity>(intent).use { awaitParagraph(it, "In the beginning was the Word") }
        }

        fun awaitParagraph(scenario: ActivityScenario<ReadingActivity>, contains: String) {
            val deadline = System.currentTimeMillis() + 120_000L
            while (System.currentTimeMillis() < deadline) {
                var found = false
                scenario.onActivity { found = find(it.window.decorView, contains) != null }
                if (found) return
                Thread.sleep(250)
            }
            fail("No paragraph containing \"$contains\" was shown")
        }

        fun find(view: View, contains: String): TextView? {
            if (view is TextView && view.text.toString().contains(contains)) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) find(view.getChildAt(i), contains)?.let { return it }
            }
            return null
        }
    }

    /** Reads and writes the SQLite storage, but routes the saves through the app's repository so its cache sees them. */
    private class AppReadingStorage(
        private val repository: BibleReadingRepository,
        private val delegate: LocalReadingStorage,
    ) : LocalReadingStorage by delegate {
        override suspend fun saveCurrentVerseIndex(verseIndex: VerseIndex) = repository.saveCurrentVerseIndex(verseIndex)

        override suspend fun saveCurrentTranslation(translationShortName: String) =
            repository.saveCurrentTranslation(translationShortName)

        override suspend fun saveParallelTranslations(parallelTranslations: List<String>) =
            repository.saveParallelTranslations(parallelTranslations)
    }

    private lateinit var database: AndroidDatabase
    private lateinit var storage: LocalReadingStorage

    @Before
    fun setUp() {
        database = AndroidDatabase(ApplicationProvider.getApplicationContext<Application>())
        storage = AndroidReadingStorage(database)
    }

    @Test
    fun separateDatabaseWriteIsInvisibleToTheCachedRepository() = runBlocking {
        val appReader = AppReadingStorage(repository, storage)
        CoreReader(appReader).toBook(JOHN).toChapter(0).toVerse(1)

        CoreReader(storage).toBook(57).toChapter(0).toVerse(3).expectVerseIndex(VerseIndex(57, 0, 3))

        assertEquals(VerseIndex(JOHN, 0, 1), repository.currentVerseIndex.first(), "cached position after a separate-DB write")
    }

    @Test
    fun johnOneVerse29SeededThroughCoreReaderIsBound() {
        val translation = runBlocking { storage.readCurrentTranslation() }
        runBlocking {
            CoreReader(AppReadingStorage(repository, storage)).toTranslation(translation).toBook(JOHN).toChapter(0).toVerse(28)
                .expectVerseIndex(VerseIndex(JOHN, 0, 28))
        }

        ActivityScenario.launch<ReadingActivity>(Intent(context, ReadingActivity::class.java)).use { scenario ->
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            var visible = false
            while (!visible && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val view = find(activity.window.decorView, VERSE_29)
                    visible = view != null && view.isShown && view.getGlobalVisibleRect(android.graphics.Rect())
                }
                if (!visible) Thread.sleep(250)
            }
            if (!visible) {
                var bound = false
                var shown = ""
                scenario.onActivity { activity ->
                    bound = find(activity.window.decorView, VERSE_29) != null
                    shown = find(activity.window.decorView, "")?.text?.take(40)?.toString().orEmpty().replace('\n', ' ')
                }
                fail("John 1:29 not visible: bound=$bound firstText='$shown' stored=${runBlocking { storage.readCurrentVerseIndex() }}")
            }
        }
    }
}
