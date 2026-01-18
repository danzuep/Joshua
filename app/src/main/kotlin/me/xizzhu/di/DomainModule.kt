package me.xizzhu.android.joshua.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.xizzhu.android.joshua.domain.BibleRepository
import me.xizzhu.android.joshua.domain.usecases.GetBooksUseCase
import me.xizzhu.android.joshua.domain.usecases.GetTranslationsUseCase
import me.xizzhu.android.joshua.domain.usecases.GetVersesUseCase
import me.xizzhu.android.joshua.domain.usecases.SearchVersesUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideGetTranslationsUseCase(repository: BibleRepository): GetTranslationsUseCase {
        return GetTranslationsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetBooksUseCase(repository: BibleRepository): GetBooksUseCase {
        return GetBooksUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetVersesUseCase(repository: BibleRepository): GetVersesUseCase {
        return GetVersesUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideSearchVersesUseCase(repository: BibleRepository): SearchVersesUseCase {
        return SearchVersesUseCase(repository)
    }
}