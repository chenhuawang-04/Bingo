package com.xty.englishhelper.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.BackgroundTaskDao
import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.ArticleCategoryDao
import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.dao.StudyDao
import com.xty.englishhelper.data.local.dao.UnitDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "english_helper.db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19
            )
            .build()
    }

    @Provides
    fun provideDictionaryDao(db: AppDatabase): DictionaryDao = db.dictionaryDao()

    @Provides
    fun provideWordDao(db: AppDatabase): WordDao = db.wordDao()

    @Provides
    fun provideUnitDao(db: AppDatabase): UnitDao = db.unitDao()

    @Provides
    fun provideStudyDao(db: AppDatabase): StudyDao = db.studyDao()

    @Provides
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideArticleCategoryDao(db: AppDatabase): ArticleCategoryDao = db.articleCategoryDao()

    @Provides
    fun provideWordPoolDao(db: AppDatabase): WordPoolDao = db.wordPoolDao()

    @Provides
    fun provideQuestionBankDao(db: AppDatabase): QuestionBankDao = db.questionBankDao()

    @Provides
    fun provideBackgroundTaskDao(db: AppDatabase): BackgroundTaskDao = db.backgroundTaskDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
