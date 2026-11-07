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

package com.github.danzuep.yeshua.android

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.github.danzuep.yeshua.android.core.provider.CoroutineDispatcherProvider
import com.github.danzuep.yeshua.android.core.provider.DefaultCoroutineDispatcherProvider
import com.github.danzuep.yeshua.android.core.provider.DefaultTimeProvider
import com.github.danzuep.yeshua.android.core.provider.TimeProvider
import com.github.danzuep.yeshua.android.core.repository.local.android.db.AndroidDatabase
import com.github.danzuep.yeshua.android.core.repository.remote.android.HttpTranslationService
import com.github.danzuep.yeshua.android.core.serializer.android.BackupJsonSerializer
import javax.inject.Singleton
import com.github.danzuep.yeshua.android.core.BackupManager
import com.github.danzuep.yeshua.android.core.BibleReadingManager
import com.github.danzuep.yeshua.android.core.Bookmark
import com.github.danzuep.yeshua.android.core.Highlight
import com.github.danzuep.yeshua.android.core.Note
import com.github.danzuep.yeshua.android.core.ReadingProgressManager
import com.github.danzuep.yeshua.android.core.SearchManager
import com.github.danzuep.yeshua.android.core.SettingsManager
import com.github.danzuep.yeshua.android.core.TranslationManager
import com.github.danzuep.yeshua.android.core.VerseAnnotationManager
import com.github.danzuep.yeshua.android.core.repository.BibleReadingRepository
import com.github.danzuep.yeshua.android.core.repository.ReadingProgressRepository
import com.github.danzuep.yeshua.android.core.repository.SettingsRepository
import com.github.danzuep.yeshua.android.core.repository.TranslationRepository
import com.github.danzuep.yeshua.android.core.repository.VerseAnnotationRepository
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidBookmarkStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidHighlightStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidNoteStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidReadingProgressStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidReadingStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidSettingsStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidTranslationStorage

@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    @Provides
    @Singleton
    fun provideNavigator(): Navigator = Navigator()
}

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = appScope

    @Provides
    @Singleton
    fun provideCoroutineDispatcherProvider(): CoroutineDispatcherProvider = DefaultCoroutineDispatcherProvider()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = DefaultTimeProvider()
}

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {
    @Provides
    @Singleton
    fun provideBackupManager(
        bookmarkRepository: VerseAnnotationRepository<Bookmark>,
        highlightRepository: VerseAnnotationRepository<Highlight>,
        noteRepository: VerseAnnotationRepository<Note>,
        readingProgressRepository: ReadingProgressRepository
    ): BackupManager = BackupManager(BackupJsonSerializer(), bookmarkRepository, highlightRepository, noteRepository, readingProgressRepository)

    @Provides
    @Singleton
    fun provideBibleReadingManager(
        bibleReadingRepository: BibleReadingRepository,
        translationRepository: TranslationRepository,
        appScope: CoroutineScope
    ): BibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, appScope)

    @Provides
    @Singleton
    fun provideBookmarkManager(bookmarkRepository: VerseAnnotationRepository<Bookmark>): VerseAnnotationManager<Bookmark> = VerseAnnotationManager(bookmarkRepository)

    @Provides
    @Singleton
    fun provideHighlightManager(highlightRepository: VerseAnnotationRepository<Highlight>): VerseAnnotationManager<Highlight> = VerseAnnotationManager(highlightRepository)

    @Provides
    @Singleton
    fun provideNoteManager(noteRepository: VerseAnnotationRepository<Note>): VerseAnnotationManager<Note> = VerseAnnotationManager(noteRepository)

    @Provides
    @Singleton
    fun provideReadingProgressManager(
        bibleReadingRepository: BibleReadingRepository,
        readingProgressRepository: ReadingProgressRepository,
        timeProvider: TimeProvider,
        appScope: CoroutineScope,
    ): ReadingProgressManager = ReadingProgressManager(bibleReadingRepository, readingProgressRepository, timeProvider, appScope)

    @Provides
    @Singleton
    fun provideSearchManager(
        bibleReadingRepository: BibleReadingRepository,
        bookmarkRepository: VerseAnnotationRepository<Bookmark>,
        highlightRepository: VerseAnnotationRepository<Highlight>,
        noteRepository: VerseAnnotationRepository<Note>
    ): SearchManager = SearchManager(bibleReadingRepository, bookmarkRepository, highlightRepository, noteRepository)

    @Provides
    @Singleton
    fun provideSettingsManager(settingsRepository: SettingsRepository): SettingsManager = SettingsManager(settingsRepository)

    @Provides
    @Singleton
    fun provideTranslationManager(translationRepository: TranslationRepository): TranslationManager = TranslationManager(translationRepository)
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAndroidDatabase(app: Application): AndroidDatabase = AndroidDatabase(app)

    @Provides
    @Singleton
    fun provideBibleReadingRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): BibleReadingRepository =
        BibleReadingRepository(AndroidReadingStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideBookmarkRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): VerseAnnotationRepository<Bookmark> =
        VerseAnnotationRepository(AndroidBookmarkStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideHighlightRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): VerseAnnotationRepository<Highlight> =
        VerseAnnotationRepository(AndroidHighlightStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideNoteRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): VerseAnnotationRepository<Note> =
        VerseAnnotationRepository(AndroidNoteStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideReadingProgressRepository(androidDatabase: AndroidDatabase): ReadingProgressRepository =
        ReadingProgressRepository(AndroidReadingProgressStorage(androidDatabase))

    @Provides
    @Singleton
    fun provideSettingsRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): SettingsRepository =
        SettingsRepository(AndroidSettingsStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideTranslationRepository(app: Application, androidDatabase: AndroidDatabase, timeProvider: TimeProvider, appScope: CoroutineScope): TranslationRepository =
        TranslationRepository(AndroidTranslationStorage(androidDatabase), HttpTranslationService(app), timeProvider, appScope)
}
