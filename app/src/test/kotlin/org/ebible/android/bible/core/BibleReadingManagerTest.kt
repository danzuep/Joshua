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

import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ebible.android.bible.core.repository.BibleReadingRepository
import org.ebible.android.bible.core.repository.TranslationRepository
import org.ebible.android.bible.tests.BaseUnitTest
import org.ebible.android.bible.tests.MockContents
import kotlin.test.*

class BibleReadingManagerTest : BaseUnitTest() {
    private lateinit var bibleReadingRepository: BibleReadingRepository
    private lateinit var translationRepository: TranslationRepository

    @BeforeTest
    override fun setup() {
        super.setup()

        bibleReadingRepository = mockk()

        translationRepository = mockk()
        every { translationRepository.downloadedTranslations } returns emptyFlow()
        coEvery { bibleReadingRepository.readBookIndicesWithVerses(any()) } returns setOf(0)
        every { bibleReadingRepository.currentVerseIndex } returns flowOf(VerseIndex(0, 0, 0))
    }

    @Test
    fun `test updateDownloadedTranslations() with no translations downloaded`() = runTest {
        coEvery { bibleReadingRepository.saveCurrentTranslation("") } returns Unit
        coEvery { bibleReadingRepository.clearParallelTranslation() } returns Unit

        val bibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, testScope)
        bibleReadingManager.updateDownloadedTranslations(emptyList())
        coVerifySequence {
            bibleReadingRepository.saveCurrentTranslation("")
            bibleReadingRepository.clearParallelTranslation()
        }
    }

    @Test
    fun `test updateDownloadedTranslations() for first downloaded translation`() = runTest {
        coEvery { bibleReadingRepository.saveCurrentTranslation(MockContents.cuvShortName) } returns Unit
        coEvery { bibleReadingRepository.currentTranslation } returns flowOf("")
        coEvery { bibleReadingRepository.parallelTranslations } returns flowOf(emptyList())

        val bibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, testScope)
        bibleReadingManager.updateDownloadedTranslations(listOf(MockContents.cuvTranslationInfo))
        coVerifySequence {
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.saveCurrentTranslation(MockContents.cuvShortName)
            bibleReadingRepository.readBookIndicesWithVerses(MockContents.cuvShortName)
            bibleReadingRepository.currentVerseIndex
            bibleReadingRepository.parallelTranslations
        }
    }

    @Test
    fun `test updateDownloadedTranslations() with translations already downloaded`() = runTest {
        coEvery { bibleReadingRepository.currentTranslation } returns flowOf(MockContents.kjvShortName)
        coEvery { bibleReadingRepository.parallelTranslations } returns flowOf(emptyList())

        val bibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, testScope)
        bibleReadingManager.updateDownloadedTranslations(listOf(MockContents.cuvDownloadedTranslationInfo, MockContents.kjvDownloadedTranslationInfo))
        coVerifySequence {
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.parallelTranslations
        }
    }

    @Test
    fun `test reorderTranslations()`() = runTest {
        coEvery { bibleReadingRepository.saveCurrentTranslation(any()) } returns Unit
        coEvery { bibleReadingRepository.saveParallelTranslations(any()) } returns Unit

        val bibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, testScope)
        bibleReadingManager.reorderTranslations(listOf(MockContents.cuvShortName, MockContents.kjvShortName, MockContents.cuvShortName))

        coVerifySequence {
            bibleReadingRepository.saveCurrentTranslation(MockContents.cuvShortName)
            bibleReadingRepository.readBookIndicesWithVerses(MockContents.cuvShortName)
            bibleReadingRepository.currentVerseIndex
            bibleReadingRepository.saveParallelTranslations(listOf(MockContents.kjvShortName))
        }
    }

    @Test
    fun `test updateDownloadedTranslations() with parallel translations`() = runTest {
        coEvery { bibleReadingRepository.currentTranslation } returns flowOf(MockContents.kjvShortName)
        coEvery { bibleReadingRepository.parallelTranslations } returns flowOf(listOf(MockContents.cuvShortName))
        coEvery { bibleReadingRepository.saveParallelTranslations(any()) } returns Unit
        coEvery { bibleReadingRepository.saveCurrentTranslation(any()) } returns Unit

        val bibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, testScope)
        bibleReadingManager.updateDownloadedTranslations(listOf(
                MockContents.cuvDownloadedTranslationInfo, MockContents.kjvDownloadedTranslationInfo
        ))
        // Two more translations are downloaded.
        bibleReadingManager.updateDownloadedTranslations(listOf(
                MockContents.cuvDownloadedTranslationInfo,
                MockContents.kjvDownloadedTranslationInfo,
                MockContents.bbeDownloadedTranslationInfo,
                MockContents.msgDownloadedTranslationInfo
        ))
        // One translation is removed.
        bibleReadingManager.updateDownloadedTranslations(listOf(
                MockContents.cuvDownloadedTranslationInfo, MockContents.kjvDownloadedTranslationInfo, MockContents.bbeDownloadedTranslationInfo
        ))
        // One translation, which is also a parallel, is removed. Should update the parallel.
        bibleReadingManager.updateDownloadedTranslations(listOf(
                MockContents.kjvDownloadedTranslationInfo, MockContents.bbeDownloadedTranslationInfo
        ))
        coEvery { bibleReadingRepository.parallelTranslations } returns flowOf(emptyList())
        // One more translation installed, and current translation removed. Should update the current.
        bibleReadingManager.updateDownloadedTranslations(listOf(
                MockContents.bbeDownloadedTranslationInfo, MockContents.msgDownloadedTranslationInfo
        ))

        coVerifySequence {
            // Two translations are downloaded.
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.parallelTranslations

            // Two more translations are downloaded. The new one becomes current, the previous current moves to parallel.
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.saveCurrentTranslation(MockContents.bbeShortName)
            bibleReadingRepository.readBookIndicesWithVerses(MockContents.bbeShortName)
            bibleReadingRepository.currentVerseIndex
            bibleReadingRepository.parallelTranslations
            bibleReadingRepository.saveParallelTranslations(listOf(MockContents.kjvShortName, MockContents.cuvShortName))

            // One translation is removed.
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.parallelTranslations

            // One translation, which is also a parallel, is removed. Should update the parallel.
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.parallelTranslations
            bibleReadingRepository.saveParallelTranslations(emptyList())

            // One more translation installed, and current translation removed. Should update the current.
            bibleReadingRepository.currentTranslation
            bibleReadingRepository.saveCurrentTranslation(MockContents.msgShortName)
            bibleReadingRepository.readBookIndicesWithVerses(MockContents.msgShortName)
            bibleReadingRepository.currentVerseIndex
            bibleReadingRepository.parallelTranslations
        }
    }
}
