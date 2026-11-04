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

package org.ebible.android.bible.reading

import android.app.Application
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.core.repository.VerseAnnotationRepository
import org.ebible.android.bible.core.repository.local.android.AndroidBookmarkStorage
import org.ebible.android.bible.core.repository.local.android.AndroidHighlightStorage
import org.ebible.android.bible.core.repository.local.android.AndroidNoteStorage
import org.ebible.android.bible.core.repository.local.android.db.AndroidDatabase
import org.ebible.android.bible.testing.CoreVerse
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs on an emulator against the real app and the minimal World English Bible bundled by the smoke test workflow
 * (John 1 and Hebrews 1, see `.github/scripts/make-minimal-web.sh`). The shared [CoreVerse] builder seeds the data
 * through the same SQLite storage the app reads, and the reader screen is then checked for the rendered bookmark icons.
 */
class BookmarkIconTest {
    private companion object {
        const val JOHN = 42
        const val HEBREWS = 57
        const val HEBREWS_1_START = "God, having in the past spoken"
        const val TIMEOUT_MS = 120_000L

        private val context: Context get() = ApplicationProvider.getApplicationContext()

        private fun reader(book: Int) = Intent(context, ReadingActivity::class.java)
                .putExtra(ReadingActivity.KEY_OPEN_BOOK, book)

        /** The first launch imports the bundled translation, so wait for it once before any test needs a book. */
        @BeforeClass
        @JvmStatic
        fun importBundledTranslation() {
            ActivityScenario.launch<ReadingActivity>(reader(JOHN)).use { scenario ->
                awaitParagraph(scenario, "In the beginning was the Word")
            }
        }

        fun awaitParagraph(scenario: ActivityScenario<ReadingActivity>, startsWith: String) {
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                var found = false
                scenario.onActivity { activity -> found = findParagraph(activity.window.decorView, startsWith) != null }
                if (found) return
                Thread.sleep(500)
            }
            fail("No paragraph starting with \"$startsWith\" was shown within ${TIMEOUT_MS / 1000}s")
        }

        fun findParagraph(view: View, startsWith: String): TextView? {
            if (view is TextView && view.text.toString().contains(startsWith)) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) findParagraph(view.getChildAt(i), startsWith)?.let { return it }
            }
            return null
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var verse3: CoreVerse

    @Before
    fun setUp() {
        val database = AndroidDatabase(ApplicationProvider.getApplicationContext<Application>())
        verse3 = CoreVerse(
                VerseIndex(HEBREWS, 0, 2),
                VerseAnnotationRepository(AndroidBookmarkStorage(database), scope),
                VerseAnnotationRepository(AndroidHighlightStorage(database), scope),
                VerseAnnotationRepository(AndroidNoteStorage(database), scope)
        )
        runBlocking { verse3.removeBookmark() }
    }

    @After
    fun tearDown() {
        runBlocking { verse3.removeBookmark() }
        scope.cancel()
    }

    private fun icons(scenario: ActivityScenario<ReadingActivity>): List<Pair<Int, String>>? {
        var result: List<Pair<Int, String>>? = null
        scenario.onActivity { activity ->
            val text = findParagraph(activity.window.decorView, HEBREWS_1_START)?.text as? Spanned ?: return@onActivity
            result = text.getSpans(0, text.length, ImageSpan::class.java).map { text.getSpanStart(it) to text.toString() }
        }
        return result
    }

    private fun awaitIconCount(scenario: ActivityScenario<ReadingActivity>, expected: Int): List<Pair<Int, String>> {
        val deadline = System.currentTimeMillis() + 15_000L
        var last: List<Pair<Int, String>>? = null
        while (System.currentTimeMillis() < deadline) {
            last = icons(scenario)
            if (last?.size == expected) return last
            Thread.sleep(250)
        }
        fail("Expected $expected bookmark icon(s) in Hebrews 1 but found ${last?.size}")
    }

    private fun assertIconAfterVerse3(icons: List<Pair<Int, String>>) {
        val (start, text) = icons.single()
        assertTrue(start > text.indexOf("Majesty on high,"), "the icon should follow verse 3's text")
        assertTrue(start < text.indexOf("having become"), "the icon should come before verse 4")
    }

    @Test
    fun bookmarkSavedBeforeOpeningShowsOnlyOnVerse3() {
        runBlocking { verse3.bookmark() }

        ActivityScenario.launch<ReadingActivity>(reader(HEBREWS)).use { scenario ->
            awaitParagraph(scenario, HEBREWS_1_START)
            assertIconAfterVerse3(awaitIconCount(scenario, 1))
        }
    }

    @Test
    fun bookmarkSavedWhileReadingShowsAndRemovesOnVerse3() {
        ActivityScenario.launch<ReadingActivity>(reader(HEBREWS)).use { scenario ->
            awaitParagraph(scenario, HEBREWS_1_START)
            awaitIconCount(scenario, 0)

            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ReadingViewModel::class.java].saveBookmark(verse3.verseIndex, toBeBookmarked = true)
            }
            assertIconAfterVerse3(awaitIconCount(scenario, 1))

            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ReadingViewModel::class.java].saveBookmark(verse3.verseIndex, toBeBookmarked = false)
            }
            awaitIconCount(scenario, 0)
        }
    }
}
