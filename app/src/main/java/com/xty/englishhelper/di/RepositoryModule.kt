package com.xty.englishhelper.di

import com.xty.englishhelper.data.json.JsonImportExporter
import com.xty.englishhelper.data.repository.AiModelRepositoryImpl
import com.xty.englishhelper.data.repository.AiRepositoryImpl
import com.xty.englishhelper.data.repository.ArticleAiRepositoryImpl
import com.xty.englishhelper.data.repository.ArticleRepositoryImpl
import com.xty.englishhelper.data.repository.BackgroundTaskRepositoryImpl
import com.xty.englishhelper.data.repository.DictionaryRepositoryImpl
import com.xty.englishhelper.data.repository.GitHubSyncRepositoryImpl
import com.xty.englishhelper.data.repository.CsMonitorRepositoryImpl
import com.xty.englishhelper.data.repository.GuardianRepositoryImpl
import com.xty.englishhelper.data.repository.QuestionBankAiRepositoryImpl
import com.xty.englishhelper.data.repository.QuestionBankRepositoryImpl
import com.xty.englishhelper.data.repository.RoomTransactionRunner
import com.xty.englishhelper.data.repository.StudyRepositoryImpl
import com.xty.englishhelper.data.repository.UnitRepositoryImpl
import com.xty.englishhelper.data.repository.WordPoolRepositoryImpl
import com.xty.englishhelper.data.repository.WordRepositoryImpl
import com.xty.englishhelper.domain.repository.AiModelRepository
import com.xty.englishhelper.domain.repository.AiRepository
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.CloudSyncRepository
import com.xty.englishhelper.domain.repository.CsMonitorRepository
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDictionaryRepository(impl: DictionaryRepositoryImpl): DictionaryRepository

    @Binds
    @Singleton
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindAiModelRepository(impl: AiModelRepositoryImpl): AiModelRepository

    @Binds
    @Singleton
    abstract fun bindBackgroundTaskRepository(impl: BackgroundTaskRepositoryImpl): BackgroundTaskRepository

    @Binds
    @Singleton
    abstract fun bindUnitRepository(impl: UnitRepositoryImpl): UnitRepository

    @Binds
    @Singleton
    abstract fun bindStudyRepository(impl: StudyRepositoryImpl): StudyRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    @Binds
    @Singleton
    abstract fun bindDictionaryImportExporter(impl: JsonImportExporter): DictionaryImportExporter

    @Binds
    @Singleton
    abstract fun bindArticleRepository(impl: ArticleRepositoryImpl): ArticleRepository

    @Binds
    @Singleton
    abstract fun bindArticleAiRepository(impl: ArticleAiRepositoryImpl): ArticleAiRepository

    @Binds
    @Singleton
    abstract fun bindWordPoolRepository(impl: WordPoolRepositoryImpl): WordPoolRepository

    @Binds
    @Singleton
    abstract fun bindCloudSyncRepository(impl: GitHubSyncRepositoryImpl): CloudSyncRepository

    @Binds
    @Singleton
    abstract fun bindGuardianRepository(impl: GuardianRepositoryImpl): GuardianRepository

    @Binds
    @Singleton
    abstract fun bindCsMonitorRepository(impl: CsMonitorRepositoryImpl): CsMonitorRepository

    @Binds
    @Singleton
    abstract fun bindQuestionBankRepository(impl: QuestionBankRepositoryImpl): QuestionBankRepository

    @Binds
    @Singleton
    abstract fun bindQuestionBankAiRepository(impl: QuestionBankAiRepositoryImpl): QuestionBankAiRepository
}
