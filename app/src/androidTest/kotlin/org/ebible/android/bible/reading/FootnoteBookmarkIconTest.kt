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
import android.content.Intent
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.core.repository.VerseAnnotationRepository
import org.ebible.android.bible.core.repository.local.android.AndroidBookmarkStorage
import org.ebible.android.bible.core.repository.local.android.AndroidHighlightStorage
import org.ebible.android.bible.core.repository.local.android.AndroidNoteStorage
import org.ebible.android.bible.core.repository.local.android.AndroidReadingStorage
import org.ebible.android.bible.core.repository.local.android.db.AndroidDatabase
import org.ebible.android.bible.testing.CoreVerse
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

/**
 * John 1:29 has footnotes, so its footnote and bookmark icons both go at the end of the verse. Every failure message
 * carries a dump of the stored verse text and of the icons in the rendered paragraph, to see why one is missing.
 */
class FootnoteBookmarkIconTest {
    private companion object {
        const val PARAGRAPH_29 = "the Lamb of God, who takes away"
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = AndroidDatabase(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val john1v29 = CoreVerse(
            VerseIndex(42, 0, 28),
            VerseAnnotationRepository(AndroidBookmarkStorage(database), scope),
            VerseAnnotationRepository(AndroidHighlightStorage(database), scope),
            VerseAnnotationRepository(AndroidNoteStorage(database), scope)
    )

    @Before
    fun setUp() {
        runBlocking { john1v29.removeBookmark() }
    }

    @After
    fun tearDown() {
        runBlocking { john1v29.removeBookmark() }
        scope.cancel()
    }

    private fun reader() = Intent(application, ReadingActivity::class.java).putExtra(ReadingActivity.KEY_OPEN_BOOK, 42)

    private fun findParagraph(view: View, contains: String): TextView? {
        if (view is TextView && view.text.toString().contains(contains)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findParagraph(view.getChildAt(i), contains)?.let { return it }
        }
        return null
    }

    private fun visibleText(view: View): List<String> =
            if (view is TextView && view.text.isNotBlank()) listOf(view.text.toString())
            else if (view is ViewGroup) (0 until view.childCount).flatMap { visibleText(view.getChildAt(it)) } else emptyList()

    private fun escaped(text: String) = text.map { if (it.code in 32..126) it.toString() else "\\u%04x".format(it.code) }.joinToString("")

    /** The stored text of verse 29, and the spans of the paragraph showing it. */
    private fun dump(scenario: ActivityScenario<ReadingActivity>): String {
        val stored = runBlocking {
            val storage = AndroidReadingStorage(database)
            storage.readVerses(storage.readCurrentTranslation(), 42, 0).getOrNull(28)?.text?.text
        }
        var rendered = "no paragraph shown"
        scenario.onActivity { activity ->
            rendered = "no paragraph shown; visible text: " + visibleText(activity.window.decorView).take(8).joinToString(" | ") { escaped(it.take(60)) }
            val text = findParagraph(activity.window.decorView, PARAGRAPH_29)?.text as? Spanned ?: return@onActivity
            val spans = text.getSpans(0, text.length, ImageSpan::class.java).joinToString { span ->
                "${span.drawable.javaClass.simpleName}[${text.getSpanStart(span)},${text.getSpanEnd(span)}] bounds=${span.drawable.bounds}"
            }
            rendered = "paragraph=\"${escaped(text.toString())}\" ## image spans: ${spans.ifEmpty { "none" }}"
        }
        return "stored verse 29=\"${escaped(stored.orEmpty())}\" ## $rendered"
    }

    /** The reader only binds the paragraphs on screen, so scroll to verse 29 once John 1 is shown. */
    private fun scrollToVerse29(scenario: ActivityScenario<ReadingActivity>) {
        val deadline = System.currentTimeMillis() + 90_000L
        var shown = false
        while (!shown && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity -> shown = findParagraph(activity.window.decorView, "In the beginning was the Word") != null }
            if (!shown) Thread.sleep(500)
        }
        // Scroll the verse list a little at a time until the paragraph with verse 29 is bound.
        repeat(60) {
            var found = false
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                found = findParagraph(root, PARAGRAPH_29) != null
                if (!found) findVerseList(root)?.scrollBy(0, 300)
            }
            if (found) return
            Thread.sleep(250)
        }
    }

    private fun findVerseList(view: View): androidx.recyclerview.widget.RecyclerView? {
        // The ViewPager2 around the page is a horizontal RecyclerView too, so look for the one that scrolls vertically.
        if (view is androidx.recyclerview.widget.RecyclerView && view.layoutManager?.canScrollVertically() == true) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findVerseList(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun awaitIcons(scenario: ActivityScenario<ReadingActivity>, expected: Int) {
        val deadline = System.currentTimeMillis() + 90_000L
        var count = -1
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val text = findParagraph(activity.window.decorView, PARAGRAPH_29)?.text as? Spanned
                count = text?.getSpans(0, text.length, ImageSpan::class.java)?.size ?: -1
            }
            if (count == expected) return
            Thread.sleep(500)
        }
        fail("Expected $expected image span(s) in the paragraph with John 1:29 but found $count\n${dump(scenario)}")
    }

    @Test
    fun footnoteIconShowsWithoutBookmark() {
        ActivityScenario.launch<ReadingActivity>(reader()).use { scenario ->
            scrollToVerse29(scenario)
            awaitIcons(scenario, 1)
        }
    }

    @Test
    fun footnoteAndBookmarkIconsShowWhenBookmarked() {
        runBlocking { john1v29.bookmark() }
        ActivityScenario.launch<ReadingActivity>(reader()).use { scenario ->
            scrollToVerse29(scenario)
            awaitIcons(scenario, 2)
        }
    }

    @Test
    fun bookmarkingWhileReadingAddsTheBookmarkIcon() {
        ActivityScenario.launch<ReadingActivity>(reader()).use { scenario ->
            scrollToVerse29(scenario)
            awaitIcons(scenario, 1)
            scenario.onActivity { activity ->
                androidx.lifecycle.ViewModelProvider(activity)[ReadingViewModel::class.java]
                        .saveBookmark(john1v29.verseIndex, toBeBookmarked = true)
            }
            awaitIcons(scenario, 2)
        }
    }
}
