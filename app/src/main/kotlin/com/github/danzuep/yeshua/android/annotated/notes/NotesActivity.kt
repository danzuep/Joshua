/*
 * Copyright (C) 2023 Xizhi Zhu
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

package com.github.danzuep.yeshua.android.annotated.notes

import android.app.Application
import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import com.github.danzuep.yeshua.android.R
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseItem
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseActivity
import com.github.danzuep.yeshua.android.annotated.AnnotatedVerseViewModel
import com.github.danzuep.yeshua.android.core.BibleReadingManager
import com.github.danzuep.yeshua.android.core.provider.CoroutineDispatcherProvider
import com.github.danzuep.yeshua.android.core.Note
import com.github.danzuep.yeshua.android.core.Settings
import com.github.danzuep.yeshua.android.core.SettingsManager
import com.github.danzuep.yeshua.android.core.VerseAnnotationManager
import com.github.danzuep.yeshua.android.core.provider.TimeProvider
import com.github.danzuep.yeshua.android.reading.ReadingActivity
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    bibleReadingManager: BibleReadingManager,
    notesManager: VerseAnnotationManager<Note>,
    settingsManager: SettingsManager,
    coroutineDispatcherProvider: CoroutineDispatcherProvider,
    timeProvider: TimeProvider,
    application: Application
) : AnnotatedVerseViewModel<Note>(
    bibleReadingManager = bibleReadingManager,
    verseAnnotationManager = notesManager,
    noItemText = R.string.text_no_notes,
    settingsManager = settingsManager,
    coroutineDispatcherProvider = coroutineDispatcherProvider,
    timeProvider = timeProvider,
    application = application
) {
    override fun buildAnnotatedVerseItem(
        settings: Settings,
        verseAnnotation: Note,
        bookName: String,
        bookShortName: String,
        verseText: String,
        sortOrder: Int
    ): AnnotatedVerseItem = AnnotatedVerseItem.Note(
        settings = settings,
        verseIndex = verseAnnotation.verseIndex,
        bookShortName = bookShortName,
        verseText = verseText,
        note = verseAnnotation.note
    )
}

@AndroidEntryPoint
class NotesActivity : AnnotatedVerseActivity<Note, NotesViewModel>(R.string.title_notes) {
    override val viewModel: NotesViewModel by viewModels()

    override fun extrasForOpeningVerse(): Bundle = ReadingActivity.bundleForOpenNote()
}
