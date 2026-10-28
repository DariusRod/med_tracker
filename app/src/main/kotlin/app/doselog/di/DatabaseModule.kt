package app.doselog.di

import android.content.Context
import androidx.room.Room
import app.doselog.data.db.AppDatabase
import app.doselog.data.db.DailyScoreDao
import app.doselog.data.db.ItemDao
import app.doselog.data.db.ItemOptionDao
import app.doselog.data.db.LogEntryDao
import app.doselog.data.db.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideItemOptionDao(db: AppDatabase): ItemOptionDao = db.itemOptionDao()

    @Provides
    fun provideLogEntryDao(db: AppDatabase): LogEntryDao = db.logEntryDao()

    @Provides
    fun provideDailyScoreDao(db: AppDatabase): DailyScoreDao = db.dailyScoreDao()
}
