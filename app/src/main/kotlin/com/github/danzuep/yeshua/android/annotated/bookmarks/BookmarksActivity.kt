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
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import com.github.danzuep.yeshua.android.R
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseItem
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseActivity
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseViewModel
import com.github.danzuep.yeshua.android.core.BibleReadingManager
import com.github.danzuep.yeshua.android.core.Bookmark
import com.github.danzuep.yeshua.android.core.Constants
import com.github.danzuep.yeshua.android.core.Settings
import com.github.danzuep.yeshua.android.core.provider.CoroutineDispatcherProvider
import com.github.danzuep.yeshua.android.core.SettingsManager
import com.github.danzuep.yeshua.android.core.VerseAnnotationManager
import com.github.danzuep.yeshua.android.core.provider.TimeProvider
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    bibleReadingManager: BibleReadingManager,
    bookmarksManager: VerseAnnotationManager<Bookmark>,
    settingsManager: SettingsManager,
    coroutineDispatcherProvider: CoroutineDispatcherProvider,
    timeProvider: TimeProvider,
    application: Application
) : AnnotatedVerseViewModel<Bookmark>(
    bibleReadingManager = bibleReadingManager,
    verseAnnotationManager = bookmarksManager,
    noItemText = R.string.text_no_bookmarks,
    settingsManager = settingsManager,
    coroutineDispatcherProvider = coroutineDispatcherProvider,
    timeProvider = timeProvider,
    application = application
) {
    override fun buildAnnotatedVerseItem(
        settings: Settings,
        verseAnnotation: Bookmark,
        bookName: String,
        bookShortName: String,
        verseText: String,
        @Constants.SortOrder sortOrder: Int
    ): AnnotatedVerseItem = AnnotatedVerseItem.Bookmark(
        settings = settings,
        verseIndex = verseAnnotation.verseIndex,
        bookName = bookName,
        bookShortName = bookShortName,
        verseText = verseText,
        sortOrder = sortOrder
    )
}

@AndroidEntryPoint
class BookmarksActivity : AnnotatedVerseActivity<Bookmark, BookmarksViewModel>(R.string.title_bookmarks) {
    override val viewModel: BookmarksViewModel by viewModels()
}
