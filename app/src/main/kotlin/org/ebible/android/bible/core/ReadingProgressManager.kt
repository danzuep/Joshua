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

package org.ebible.android.bible.core

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ebible.android.bible.core.provider.TimeProvider
import org.ebible.android.bible.core.repository.BibleReadingRepository
import org.ebible.android.bible.core.repository.ReadingProgressRepository
import org.ebible.android.bible.utils.filterIsValid
import co.touchlab.kermit.Logger

class ReadingProgressManager(
        private val bibleReadingRepository: BibleReadingRepository,
        private val readingProgressRepository: ReadingProgressRepository,
        private val timeProvider: TimeProvider,
        private val appScope: CoroutineScope,
) {
    companion object {
        private val TAG: String = ReadingProgressManager::class.java.simpleName

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val TIME_SPENT_THRESHOLD_IN_MILLIS = 2500L
    }

    private var currentVerseIndexObserver: Flow<VerseIndex>? = null

    private var currentVerseIndex: VerseIndex = VerseIndex.INVALID
    private var lastTimestamp: Long = 0L

    fun startTracking() {
        if (currentVerseIndexObserver != null) {
            return
        }

        lastTimestamp = timeProvider.elapsedRealtime
        currentVerseIndexObserver = bibleReadingRepository.currentVerseIndex
        appScope.launch(Dispatchers.Main) {
            currentVerseIndexObserver?.filterIsValid()
                    ?.collect {
                        trackReadingProgress()
                        currentVerseIndex = it
                        lastTimestamp = timeProvider.elapsedRealtime
                    }
        }
    }

    private suspend fun trackReadingProgress() {
        try {
            val verseIndex = currentVerseIndex
            if (!verseIndex.isValid()
                    || lastTimestamp == 0L
                    || bibleReadingRepository.currentTranslation.first().isEmpty()) {
                return
            }

            val now = timeProvider.elapsedRealtime
            val timeSpentInMillis = now - lastTimestamp
            if (timeSpentInMillis < TIME_SPENT_THRESHOLD_IN_MILLIS) {
                return
            }

            readingProgressRepository.trackReadingProgress(
                    verseIndex.bookIndex, verseIndex.chapterIndex, timeSpentInMillis, now)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to track reading progress" }
        }
    }

    fun stopTracking() {
        // uses appScope to make sure this will be executed without being canceled
        // uses Dispatchers.Main.immediate to make sure this will be executed immediately
        appScope.launch(Dispatchers.Main.immediate) {
            trackReadingProgress()
            currentVerseIndex = VerseIndex.INVALID
            lastTimestamp = 0L
            currentVerseIndexObserver = null
        }
    }

    suspend fun read(): ReadingProgress = readingProgressRepository.read()
}
