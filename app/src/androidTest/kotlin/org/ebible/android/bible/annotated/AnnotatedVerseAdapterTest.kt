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

package org.ebible.android.bible.annotated

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Constants
import org.ebible.android.bible.core.Settings
import org.ebible.android.bible.core.VerseIndex
import org.ebible.android.bible.tests.BaseUnitTest
import org.ebible.android.bible.tests.TestExecutor
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AnnotatedVerseAdapterTest : BaseUnitTest() {
    private lateinit var context: Context
    private lateinit var adapter: AnnotatedVerseAdapter

    @BeforeTest
    override fun setup() {
        super.setup()

        context = ApplicationProvider.getApplicationContext<Context>().apply { setTheme(R.style.AppTheme) }
        adapter = AnnotatedVerseAdapter(
            inflater = LayoutInflater.from(context),
            executor = TestExecutor()
        ) {}
    }

    @Test
    fun test_getItemViewType() {
        adapter.submitList(
            listOf(
                AnnotatedVerseItem.Header(Settings.DEFAULT, "", false),
                AnnotatedVerseItem.Bookmark(Settings.DEFAULT, VerseIndex.INVALID, "", "", "", Constants.DEFAULT_SORT_ORDER),
                AnnotatedVerseItem.Highlight(Settings.DEFAULT, VerseIndex.INVALID, "", "", "", 0, Constants.DEFAULT_SORT_ORDER),
                AnnotatedVerseItem.Note(Settings.DEFAULT, VerseIndex.INVALID, "", "", "")
            )
        ) {
            assertEquals(R.layout.item_title, adapter.getItemViewType(0))
            assertEquals(R.layout.item_annotated_verse_bookmark, adapter.getItemViewType(1))
            assertEquals(R.layout.item_annotated_verse_highlight, adapter.getItemViewType(2))
            assertEquals(R.layout.item_annotated_verse_note, adapter.getItemViewType(3))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun test_onCreateViewHolder_with_unsupported_viewType() {
        adapter.onCreateViewHolder(FrameLayout(context), 0)
    }

    @Test
    fun test_onCreateViewHolder() {
        adapter.onCreateViewHolder(FrameLayout(context), AnnotatedVerseItem.Header.VIEW_TYPE) as AnnotatedVerseViewHolder.Header
        adapter.onCreateViewHolder(FrameLayout(context), AnnotatedVerseItem.Bookmark.VIEW_TYPE) as AnnotatedVerseViewHolder.Bookmark
        adapter.onCreateViewHolder(FrameLayout(context), AnnotatedVerseItem.Highlight.VIEW_TYPE) as AnnotatedVerseViewHolder.Highlight
        adapter.onCreateViewHolder(FrameLayout(context), AnnotatedVerseItem.Note.VIEW_TYPE) as AnnotatedVerseViewHolder.Note
    }
}
