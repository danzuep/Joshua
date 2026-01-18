package me.xizzhu.android.joshua.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.xizzhu.android.joshua.data.BibleDatabase
import me.xizzhu.android.joshua.data.dao.BibleDao
import me.xizzhu.android.joshua.data.repositories.BibleRepositoryImpl
import me.xizzhu.android.joshua.domain.BibleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideBibleDatabase(@ApplicationContext context: Context): BibleDatabase {
        return Room.databaseBuilder(
            context,
            BibleDatabase::class.java,
            "bible.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideBibleDao(database: BibleDatabase): BibleDao {
        return database.bibleDao()
    }

    @Provides
    @Singleton
    fun provideBibleRepository(dao: BibleDao): BibleRepository {
        return BibleRepositoryImpl(dao)
    }
}