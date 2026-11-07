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

package com.github.danzuep.yeshua.android.annotated.bookmarks

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseItem
import com.github.danzuep.yeshua.android.core.BibleReadingManager
import com.github.danzuep.yeshua.android.core.Bookmark
import com.github.danzuep.yeshua.android.core.Constants
import com.github.danzuep.yeshua.android.core.Settings
import com.github.danzuep.yeshua.android.core.SettingsManager
import com.github.danzuep.yeshua.android.core.VerseAnnotationManager
import com.github.danzuep.yeshua.android.core.VerseIndex
import com.github.danzuep.yeshua.android.tests.BaseUnitTest
import com.github.danzuep.yeshua.android.tests.MockContents
import com.github.danzuep.yeshua.android.tests.TestTimeProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarksViewModelTest : BaseUnitTest() {
    private lateinit var bibleReadingManager: BibleReadingManager
    private lateinit var bookmarksManager: VerseAnnotationManager<Bookmark>
    private lateinit var settingsManager: SettingsManager
    private lateinit var application: Application
    private lateinit var bookmarksViewModel: BookmarksViewModel

    @BeforeTest
    override fun setup() {
        super.setup()

        bibleReadingManager = mockk<BibleReadingManager>().apply { every { currentTranslation() } returns emptyFlow() }
        bookmarksManager = mockk<VerseAnnotationManager<Bookmark>>().apply { every { sortOrder() } returns emptyFlow() }
        settingsManager = mockk<SettingsManager>().apply { every { settings() } returns emptyFlow() }
        application = mockk()
        bookmarksViewModel = BookmarksViewModel(bibleReadingManager, bookmarksManager, settingsManager, testCoroutineDispatcherProvider, TestTimeProvider(), application)
    }

    @Test
    fun `test buildAnnotatedVerseItem()`() {
        assertEquals(
            AnnotatedVerseItem.Bookmark(
                Settings.DEFAULT,
                MockContents.kjvVerses[0].verseIndex,
                MockContents.kjvBookNames[0],
                MockContents.kjvBookShortNames[0],
                MockContents.kjvVerses[0].text.text,
                Constants.SORT_BY_BOOK
            ),
            bookmarksViewModel.buildAnnotatedVerseItem(
                settings = Settings.DEFAULT,
                verseAnnotation = Bookmark(VerseIndex(0, 0, 0), 1L),
                bookName = MockContents.kjvBookNames[0],
                bookShortName = MockContents.kjvBookShortNames[0],
                verseText = MockContents.kjvVerses[0].text.text,
                sortOrder = Constants.SORT_BY_BOOK
            )
        )
    }
}
