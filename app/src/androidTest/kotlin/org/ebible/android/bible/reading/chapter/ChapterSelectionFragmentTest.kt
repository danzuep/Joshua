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

package org.ebible.android.bible.reading.chapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.ebible.android.bible.R
import org.ebible.android.bible.core.Bible
import org.ebible.android.bible.databinding.FragmentChapterSelectionBinding
import org.ebible.android.bible.reading.ReadingActivity
import org.ebible.android.bible.tests.BaseFragmentTest
import org.ebible.android.bible.tests.MockContents
import org.ebible.android.bible.tests.getProperty
import org.junit.runner.RunWith
import org.ebible.android.bible.tests.assertDialogShowing

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ChapterSelectionFragmentTest : BaseFragmentTest() {
    @BindValue
    val viewModel: ChapterSelectionViewModel = mockk(relaxed = true)

    @Test
    fun `test onViewStateUpdated(), with null error`() {
        every { viewModel.viewState() } returns flowOf(ChapterSelectionViewModel.ViewState(
            currentBookIndex = 1,
            currentChapterIndex = 2,
            chapterSelectionItems = MockContents.kjvBookNames.mapIndexed { index, bookName ->
                ChapterSelectionItem(bookIndex = index, bookName = bookName, chapterCount = Bible.getChapterCount(index))
            },
            error = null,
        ))
        withFragment<ChapterSelectionFragment, ReadingActivity> { fragment ->
            val viewBinding: FragmentChapterSelectionBinding = fragment.getProperty("viewBinding")
            val chapterListView: ChapterListView = viewBinding.chapterSelectionView.findViewById(R.id.chapter_list_view)
            val adapter: ChapterListAdapter = chapterListView.getProperty("adapter")
            assertEquals(66, adapter.groupCount)
            repeat(66) { i -> assertEquals(i == 1, chapterListView.isGroupExpanded(i)) }
        }
    }

    @Test
    fun `test onViewStateUpdated(), with ChapterSelectionError error`() {
        every { viewModel.viewState() } returns flowOf(ChapterSelectionViewModel.ViewState(
            currentBookIndex = -1,
            currentChapterIndex = -1,
            chapterSelectionItems = emptyList(),
            error = ChapterSelectionViewModel.ViewState.Error.ChapterSelectionError(1, 2),
        ))
        withFragment<ChapterSelectionFragment, ReadingActivity>(afterLaunch = { assertDialogShowing() }) {}
    }
}
