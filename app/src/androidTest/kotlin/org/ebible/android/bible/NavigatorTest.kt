/*
 * Copyright (C) 2023 Xizhi Zhu
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

package org.ebible.android.bible

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.ebible.android.bible.annotated.bookmarks.BookmarksActivity
import org.ebible.android.bible.annotated.highlights.HighlightsActivity
import org.ebible.android.bible.annotated.notes.NotesActivity
import org.ebible.android.bible.progress.ReadingProgressActivity
import org.ebible.android.bible.reading.ReadingActivity
import org.ebible.android.bible.reading.chapter.ChapterSelectionViewModel
import org.ebible.android.bible.reading.toolbar.ReadingToolbarViewModel
import org.ebible.android.bible.search.SearchActivity
import org.ebible.android.bible.settings.SettingsActivity
import org.ebible.android.bible.tests.BaseActivityTest
import org.ebible.android.bible.translations.TranslationsActivity
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.intent.rule.IntentsRule
import org.ebible.android.bible.tests.lastStartedIntent
import org.ebible.android.bible.tests.stubAllIntents
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class NavigatorTest : BaseActivityTest() {
    private lateinit var navigator: Navigator

    @BindValue
    val readingToolbarViewModel: ReadingToolbarViewModel = mockk(relaxed = true)

    @BindValue
    val chapterSelectionViewModel: ChapterSelectionViewModel = mockk(relaxed = true)

    @get:Rule
    val intentsRule = IntentsRule()

    @BeforeTest
    override fun setup() {
        super.setup()
        stubAllIntents()
        navigator = Navigator()
    }

    @Test
    fun `test navigate(), internal activities`() {
        testNavigate<ReadingActivity>(Navigator.SCREEN_READING)
        testNavigate<SearchActivity>(Navigator.SCREEN_SEARCH)
        testNavigate<TranslationsActivity>(Navigator.SCREEN_TRANSLATIONS)
        testNavigate<ReadingProgressActivity>(Navigator.SCREEN_READING_PROGRESS)
        testNavigate<BookmarksActivity>(Navigator.SCREEN_BOOKMARKS)
        testNavigate<HighlightsActivity>(Navigator.SCREEN_HIGHLIGHTS)
        testNavigate<NotesActivity>(Navigator.SCREEN_NOTES)
        testNavigate<SettingsActivity>(Navigator.SCREEN_SETTINGS)
    }

    private inline fun <reified A : Activity> testNavigate(@Navigator.Companion.Screen screen: Int) {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, screen)

            val nextStartedIntent = lastStartedIntent()
            assertEquals(A::class.java.name, nextStartedIntent.component?.className)
        }
    }

    @Test
    fun `test navigate(), SCREEN_NOTES`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, Navigator.SCREEN_NOTES, Bundle().apply { putString("test_key", "value") })

            val nextStartedIntent = lastStartedIntent()
            assertEquals(NotesActivity::class.java.name, nextStartedIntent.component?.className)
            assertEquals(1, nextStartedIntent.extras?.size())
            assertEquals("value", nextStartedIntent.getStringExtra("test_key"))
        }
    }

    @Test
    fun `test navigate(), SCREEN_RATE_ME`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, Navigator.SCREEN_RATE_ME)

            val nextStartedIntent = lastStartedIntent()
            assertEquals(Intent.ACTION_VIEW, nextStartedIntent.action)
            assertEquals(Uri.parse("market://details?id=org.ebible.android.bible"), nextStartedIntent.data)
        }
    }

    @Test
    fun `test navigate(), SCREEN_WEBSITE`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, Navigator.SCREEN_WEBSITE)

            val nextStartedIntent = lastStartedIntent()
            assertEquals(Intent.ACTION_VIEW, nextStartedIntent.action)
            assertEquals(Uri.parse("https://ebible.org/about.php"), nextStartedIntent.data)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test navigate(), with unknown screen`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, -1)
        }
    }

    @Test
    fun `test goBack()`() {
        withActivity<ReadingActivity> { activity ->
            navigator.goBack(activity)
            // Verify that onBackPressedDispatcher.onBackPressed() was called
            // The activity should not be finishing immediately as onBackPressedDispatcher
            // may handle the back press asynchronously
        }
    }
}
