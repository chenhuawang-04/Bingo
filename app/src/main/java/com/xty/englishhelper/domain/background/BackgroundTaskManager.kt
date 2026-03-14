package com.xty.englishhelper.domain.background

import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.ai.OrganizeWordWithAiUseCase
import com.xty.englishhelper.domain.usecase.article.CreateArticleUseCase
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import com.xty.englishhelper.domain.usecase.pool.RebuildWordPoolsUseCase
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.article.SmartParagraphSplitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundTaskManager @Inject constructor(
    private val repository: BackgroundTaskRepository,
    private val questionBankRepository: QuestionBankRepository,
    private val questionBankAiRepository: QuestionBankAiRepository,
    private val wordRepository: WordRepository,
    private val organizeWordWithAi: OrganizeWordWithAiUseCase,
    private val rebuildWordPools: RebuildWordPoolsUseCase,
    private val createArticle: CreateArticleUseCase,
    private val parseArticle: ParseArticleUseCase,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val dispatchLock = Mutex()
    private val maxConcurrency = 2

    fun start() {
        scope.launch {
            repository.updateStatusByStatus(BackgroundTaskStatus.RUNNING, BackgroundTaskStatus.PENDING)
            schedule()
        }
    }

    fun enqueueWordOrganize(wordId: Long, dictionaryId: Long, spelling: String, force: Boolean = false) {
        val payload = WordOrganizePayload(wordId = wordId, dictionaryId = dictionaryId, spelling = spelling)
        enqueueTask(BackgroundTaskType.WORD_ORGANIZE, payload, "word:$wordId", force)
    }

    fun enqueueWordPoolRebuild(dictionaryId: Long, strategy: PoolStrategy, force: Boolean = false) {
        val payload = WordPoolRebuildPayload(dictionaryId = dictionaryId, strategy = strategy.name)
        enqueueTask(BackgroundTaskType.WORD_POOL_REBUILD, payload, "pool:$dictionaryId:${strategy.name}", force)
    }

    fun enqueueQuestionAnswerGeneration(
        groupId: Long,
        paperTitle: String,
        sectionLabel: String,
        force: Boolean = false
    ) {
        val payload = QuestionAnswerGeneratePayload(groupId, paperTitle, sectionLabel)
        enqueueTask(BackgroundTaskType.QUESTION_ANSWER_GENERATE, payload, "answer:$groupId", force)
    }

    fun enqueueQuestionSourceVerify(
        groupId: Long,
        paperTitle: String,
        sectionLabel: String,
        sourceUrlOverride: String?,
        force: Boolean = false
    ) {
        val payload = QuestionSourceVerifyPayload(groupId, paperTitle, sectionLabel, sourceUrlOverride)
        enqueueTask(BackgroundTaskType.QUESTION_SOURCE_VERIFY, payload, "verify:$groupId", force)
    }

    fun pauseTask(taskId: Long) {
        scope.launch {
            repository.updateStatus(taskId, BackgroundTaskStatus.PAUSED, "已暂停")
            runningJobs.remove(taskId)?.cancel()
        }
    }

    fun resumeTask(taskId: Long) {
        scope.launch {
            repository.updateStatus(taskId, BackgroundTaskStatus.PENDING, null)
            repository.updateProgress(taskId, 0, 0)
            schedule()
        }
    }

    fun restartTask(taskId: Long) {
        resumeTask(taskId)
    }

    fun cancelTask(taskId: Long) {
        scope.launch {
            repository.updateStatus(taskId, BackgroundTaskStatus.CANCELED, "已停止")
            runningJobs.remove(taskId)?.cancel()
        }
    }

    fun deleteTask(taskId: Long) {
        scope.launch {
            runningJobs.remove(taskId)?.cancel()
            repository.deleteTask(taskId)
        }
    }

    fun pauseAll() {
        scope.launch {
            repository.updateStatusByStatus(BackgroundTaskStatus.PENDING, BackgroundTaskStatus.PAUSED)
            repository.updateStatusByStatus(BackgroundTaskStatus.RUNNING, BackgroundTaskStatus.PAUSED)
            runningJobs.values.forEach { it.cancel() }
            runningJobs.clear()
        }
    }

    fun cancelAll() {
        scope.launch {
            repository.updateStatusByStatus(BackgroundTaskStatus.PENDING, BackgroundTaskStatus.CANCELED)
            repository.updateStatusByStatus(BackgroundTaskStatus.RUNNING, BackgroundTaskStatus.CANCELED)
            runningJobs.values.forEach { it.cancel() }
            runningJobs.clear()
        }
    }

    fun resumeAll() {
        scope.launch {
            repository.updateStatusByStatus(BackgroundTaskStatus.PAUSED, BackgroundTaskStatus.PENDING)
            schedule()
        }
    }

    fun retryFailed() {
        scope.launch {
            repository.updateStatusByStatus(BackgroundTaskStatus.FAILED, BackgroundTaskStatus.PENDING)
            schedule()
        }
    }

    fun clearFinished() {
        scope.launch {
            repository.deleteTasksByStatuses(
                listOf(BackgroundTaskStatus.SUCCESS, BackgroundTaskStatus.FAILED, BackgroundTaskStatus.CANCELED)
            )
        }
    }

    private fun enqueueTask(
        type: BackgroundTaskType,
        payload: BackgroundTaskPayload,
        dedupeKey: String,
        force: Boolean
    ) {
        scope.launch {
            val existing = repository.getTaskByDedupeKey(dedupeKey)
            if (existing != null) {
                when (existing.status) {
                    BackgroundTaskStatus.PENDING,
                    BackgroundTaskStatus.RUNNING -> return@launch
                    BackgroundTaskStatus.SUCCESS -> {
                        if (!force) return@launch
                        repository.updatePayload(existing.id, type, payload)
                        repository.updateStatus(existing.id, BackgroundTaskStatus.PENDING, null)
                        repository.updateProgress(existing.id, 0, 0)
                    }
                    BackgroundTaskStatus.FAILED,
                    BackgroundTaskStatus.PAUSED,
                    BackgroundTaskStatus.CANCELED -> {
                        repository.updatePayload(existing.id, type, payload)
                        repository.updateStatus(existing.id, BackgroundTaskStatus.PENDING, null)
                        repository.updateProgress(existing.id, 0, 0)
                    }
                }
            } else {
                repository.insertTask(type, payload, dedupeKey)
            }
            schedule()
        }
    }

    private suspend fun schedule() {
        dispatchLock.withLock {
            val running = runningJobs.size
            val slots = maxConcurrency - running
            if (slots <= 0) return
            val tasks = repository.getTasksByStatuses(listOf(BackgroundTaskStatus.PENDING), slots)
            tasks.forEach { launchTask(it) }
        }
    }

    private fun launchTask(task: BackgroundTask) {
        if (runningJobs.containsKey(task.id)) return
        if (task.type == BackgroundTaskType.UNKNOWN) {
            scope.launch {
                repository.updateStatus(task.id, BackgroundTaskStatus.CANCELED, "未知任务类型")
                repository.updateProgress(task.id, 0, 0)
                schedule()
            }
            return
        }
        val job = scope.launch {
            repository.incrementAttempt(task.id)
            repository.updateStatus(task.id, BackgroundTaskStatus.RUNNING, null)
            try {
                executeTask(task)
                repository.updateStatus(task.id, BackgroundTaskStatus.SUCCESS, null)
            } catch (e: CancellationException) {
                val current = repository.getTaskById(task.id)
                if (current?.status == BackgroundTaskStatus.PAUSED || current?.status == BackgroundTaskStatus.CANCELED) {
                    return@launch
                }
                repository.updateStatus(task.id, BackgroundTaskStatus.CANCELED, "已停止")
            } catch (e: Exception) {
                repository.updateStatus(task.id, BackgroundTaskStatus.FAILED, e.message ?: "任务失败")
            } finally {
                runningJobs.remove(task.id)
                schedule()
            }
        }
        runningJobs[task.id] = job
    }

    private suspend fun executeTask(task: BackgroundTask) {
        when (task.type) {
            BackgroundTaskType.WORD_ORGANIZE -> executeWordOrganize(task)
            BackgroundTaskType.WORD_POOL_REBUILD -> executeWordPoolRebuild(task)
            BackgroundTaskType.QUESTION_ANSWER_GENERATE -> executeAnswerGenerate(task)
            BackgroundTaskType.QUESTION_SOURCE_VERIFY -> executeSourceVerify(task)
            BackgroundTaskType.UNKNOWN -> throw IllegalStateException("未知任务类型")
        }
    }

    private suspend fun executeWordOrganize(task: BackgroundTask) {
        val payload = task.payload as? WordOrganizePayload ?: throw IllegalStateException("任务参数缺失")
        val config = settingsDataStore.getAiConfig(AiSettingsScope.MAIN)
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("API Key 未配置")
        }
        repository.updateProgress(task.id, 0, 1)
        val result = organizeWordWithAi(
            payload.spelling,
            config.apiKey,
            config.model,
            config.baseUrl,
            config.provider
        )
        val currentWord = wordRepository.getWordById(payload.wordId) ?: throw IllegalStateException("单词不存在")
        val merged = currentWord.copy(
            phonetic = currentWord.phonetic.ifBlank { result.phonetic },
            meanings = currentWord.meanings.ifEmpty { result.meanings },
            rootExplanation = currentWord.rootExplanation.ifBlank { result.rootExplanation },
            decomposition = currentWord.decomposition.ifEmpty { result.decomposition },
            synonyms = currentWord.synonyms.ifEmpty { result.synonyms },
            similarWords = currentWord.similarWords.ifEmpty { result.similarWords },
            cognates = currentWord.cognates.ifEmpty { result.cognates },
            inflections = currentWord.inflections.ifEmpty { result.inflections }
        )
        wordRepository.updateWord(merged)
        repository.updateProgress(task.id, 1, 1)
    }

    private suspend fun executeAnswerGenerate(task: BackgroundTask) {
        val payload = task.payload as? QuestionAnswerGeneratePayload ?: throw IllegalStateException("任务参数缺失")
        val group = questionBankRepository.getGroupById(payload.groupId) ?: throw IllegalStateException("题组不存在")
        val items = questionBankRepository.getItemsByGroup(group.id)
        if (items.isEmpty()) {
            repository.updateProgress(task.id, 0, 0)
            return
        }
        val config = settingsDataStore.getFastAiConfig()
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("快速模型未配置")
        }
        repository.updateProgress(task.id, 0, items.size)
        val results = questionBankAiRepository.generateAnswers(
            group.passageText,
            items,
            config.apiKey,
            config.model,
            config.baseUrl,
            config.provider
        )
        var done = 0
        for (result in results) {
            val item = items.find { it.questionNumber == result.questionNumber } ?: continue
            questionBankRepository.updateAnswer(
                item.id,
                result.answer,
                "AI",
                result.explanation,
                result.difficultyLevel,
                result.difficultyScore
            )
            done += 1
            repository.updateProgress(task.id, done, items.size)
        }
        repository.updateProgress(task.id, items.size, items.size)
        questionBankRepository.markHasAiAnswer(group.id)
    }

    private suspend fun executeWordPoolRebuild(task: BackgroundTask) {
        val payload = task.payload as? WordPoolRebuildPayload ?: throw IllegalStateException("任务参数缺失")
        val strategy = runCatching { PoolStrategy.valueOf(payload.strategy) }.getOrElse {
            throw IllegalStateException("词池策略无效")
        }
        repository.updateProgress(task.id, 0, 0)
        var lastCurrent = 0
        var lastTotal = 0
        rebuildWordPools(payload.dictionaryId, strategy) { current, total ->
            lastCurrent = current
            lastTotal = total
            scope.launch {
                repository.updateProgress(task.id, current, total)
            }
        }
        if (lastTotal > 0) {
            repository.updateProgress(task.id, lastTotal.coerceAtLeast(lastCurrent), lastTotal)
        }
    }

    private suspend fun executeSourceVerify(task: BackgroundTask) {
        val payload = task.payload as? QuestionSourceVerifyPayload ?: throw IllegalStateException("任务参数缺失")
        val group = questionBankRepository.getGroupById(payload.groupId) ?: throw IllegalStateException("题组不存在")
        val config = settingsDataStore.getAiConfig(AiSettingsScope.SEARCH)
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("搜索模型未配置")
        }
        repository.updateProgress(task.id, 0, 1)
        val result = questionBankAiRepository.verifySource(
            group.passageText,
            payload.sourceUrlOverride ?: group.sourceUrl.orEmpty(),
            config.apiKey,
            config.model,
            config.baseUrl,
            config.provider
        )
        if (result.matched) {
            questionBankRepository.updateSourceVerification(group.id, 1, null)
            if (!result.sourceUrl.isNullOrBlank()) {
                questionBankRepository.updateSourceUrl(group.id, result.sourceUrl)
                questionBankRepository.updateSourceVerification(group.id, 1, null)
            }
            try {
                val rawParagraphs = result.articleParagraphs
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                val rawContent = result.articleContent?.trim().orEmpty()
                val content = if (rawContent.isBlank() && rawParagraphs.isNotEmpty()) {
                    rawParagraphs.joinToString("\n\n")
                } else {
                    rawContent
                }
                val rawParagraphTextLen = rawParagraphs.sumOf { it.length }
                val useContentParagraphs = content.isNotBlank() &&
                    (rawParagraphs.isEmpty() || rawParagraphTextLen < content.length * 0.6f)
                val finalParagraphs = if (useContentParagraphs) {
                    SmartParagraphSplitter.split(content)
                } else {
                    rawParagraphs
                }
                val articleId = createArticle(
                    title = result.articleTitle ?: group.sectionLabel ?: "来源文章",
                    content = content,
                    sourceType = ArticleSourceType.AI,
                    author = result.articleAuthor ?: "",
                    source = result.sourceUrl ?: group.sourceUrl ?: "",
                    summary = result.articleSummary ?: "",
                    paragraphs = finalParagraphs.mapIndexed { i, text ->
                        ArticleParagraph(paragraphIndex = i, text = text)
                    },
                    categoryId = ArticleCategoryDefaults.SOURCE_ID
                )
                parseArticle(articleId)
                questionBankRepository.linkSourceArticle(group.id, articleId)
            } catch (_: Exception) {
            }
            repository.updateProgress(task.id, 1, 1)
        } else {
            questionBankRepository.updateSourceVerification(group.id, -1, result.errorMessage)
            throw IllegalStateException(result.errorMessage ?: "来源未匹配")
        }
    }
}
