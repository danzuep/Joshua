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

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.ebible.android.bible.core.provider.CoroutineDispatcherProvider
import org.ebible.android.bible.core.provider.DefaultCoroutineDispatcherProvider
import org.ebible.android.bible.core.provider.DefaultTimeProvider
import org.ebible.android.bible.core.provider.TimeProvider
import org.ebible.android.bible.core.repository.local.android.db.AndroidDatabase
import org.ebible.android.bible.core.repository.remote.android.HttpTranslationService
import org.ebible.android.bible.core.serializer.android.BackupJsonSerializer
import javax.inject.Singleton
import org.ebible.android.bible.core.BackupManager
import org.ebible.android.bible.core.BibleReadingManager
import org.ebible.android.bible.core.Bookmark
import org.ebible.android.bible.core.Highlight
import org.ebible.android.bible.core.Note
import org.ebible.android.bible.core.ReadingProgressManager
import org.ebible.android.bible.core.SearchManager
import org.ebible.android.bible.core.SettingsManager
import org.ebible.android.bible.core.TranslationManager
import org.ebible.android.bible.core.VerseAnnotationManager
import org.ebible.android.bible.core.repository.BibleReadingRepository
import org.ebible.android.bible.core.repository.ReadingProgressRepository
import org.ebible.android.bible.core.repository.SettingsRepository
import org.ebible.android.bible.core.repository.TranslationRepository
import org.ebible.android.bible.core.repository.VerseAnnotationRepository
import org.ebible.android.bible.core.repository.local.android.AndroidBookmarkStorage
import org.ebible.android.bible.core.repository.local.android.AndroidHighlightStorage
import org.ebible.android.bible.core.repository.local.android.AndroidNoteStorage
import org.ebible.android.bible.core.repository.local.android.AndroidReadingProgressStorage
import org.ebible.android.bible.core.repository.local.android.AndroidReadingStorage
import org.ebible.android.bible.core.repository.local.android.AndroidSettingsStorage
import org.ebible.android.bible.core.repository.local.android.AndroidTranslationStorage

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
