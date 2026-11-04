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

package org.ebible.android.bible

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ebible.android.bible.core.repository.BibleReadingRepository

/** Debug builds only: lets instrumented tests reach the app's own singleton repository, which caches the reading position. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugEntryPoints {
    fun bibleReadingRepository(): BibleReadingRepository
}
