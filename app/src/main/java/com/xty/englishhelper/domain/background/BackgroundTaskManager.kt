package com.xty.englishhelper.domain.background

import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiModelSnapshot
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionGeneratePayload
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.QuestionWritingSamplePayload
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.WordReferenceSource
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class BackgroundTaskEnqueueResult {
    ENQUEUED,
    RESTARTED,
    ALREADY_PENDING,
    ALREADY_RUNNING,
    SKIPPED_SUCCESS
}

@Singleton
class BackgroundTaskManager @Inject constructor(
    private val repository: BackgroundTaskRepository,
    private val questionBankRepository: QuestionBankRepository,
    private val questionBankAiRepository: QuestionBankAiRepository,
    private val articleRepository: ArticleRepository,
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
    @Volatile
    private var maxConcurrency = 2
    @Volatile
    private var started = false

    init {
        scope.launch {
            settingsDataStore.backgroundTaskConcurrency.collect { value ->
                maxConcurrency = value.coerceIn(1, 6)
                if (started) {
                    schedule()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            repository.updateStatusByStatus(BackgroundTaskStatus.RUNNING, BackgroundTaskStatus.PENDING)
            migrateLegacyWordOrganizeTasks()
            schedule()
        }
    }

    fun enqueueWordOrganize(wordId: Long, dictionaryId: Long, spelling: String, force: Boolean = false) {
        scope.launch {
            val highQualityEnabled = settingsDataStore.getWordOrganizeHighQualityEnabled()
            val referenceSource = settingsDataStore.getWordOrganizeReferenceSource()
            val mainSnapshot = settingsDataStore.getAiConfig(AiSettingsScope.MAIN).toSnapshot()
            val referenceSnapshot = if (highQualityEnabled) {
                val referenceScope = when (referenceSource) {
                    WordReferenceSource.FAST -> AiSettingsScope.FAST
                    WordReferenceSource.SEARCH -> AiSettingsScope.SEARCH
                }
                settingsDataStore.getConfiguredAiConfig(referenceScope).toSnapshot()
            } else {
                null
            }
            val payload = WordOrganizePayload(
                wordId = wordId,
                dictionaryId = dictionaryId,
                spelling = spelling,
                highQualityEnabled = highQualityEnabled,
                referenceSource = referenceSource.name,
                mainModelSnapshot = mainSnapshot,
                referenceModelSnapshot = referenceSnapshot
            )
            enqueueTaskInternal(BackgroundTaskType.WORD_ORGANIZE, payload, "word:$wordId", force)
        }
    }

    fun enqueueWordPoolRebuild(dictionaryId: Long, strategy: PoolStrategy, force: Boolean = false) {
        val payload = WordPoolRebuildPayload(dictionaryId = dictionaryId, strategy = strategy.name)
        enqueueTaskAsync(BackgroundTaskType.WORD_POOL_REBUILD, payload, "pool:$dictionaryId:${strategy.name}", force)
    }

    suspend fun enqueueQuestionGenerateFromArticle(
        articleId: Long,
        paperTitle: String,
        questionType: QuestionType,
        variant: String?,
        force: Boolean = true
    ): BackgroundTaskEnqueueResult {
        val payload = QuestionGeneratePayload(
            articleId = articleId,
            paperTitle = paperTitle,
            questionType = questionType.name,
            variant = variant
        )
        val variantKey = variant.orEmpty()
        return enqueueTaskInternal(
            BackgroundTaskType.QUESTION_GENERATE,
            payload,
            "generate:$articleId:${questionType.name}:$variantKey",
            force
        )
    }

    fun enqueueQuestionAnswerGeneration(
        groupId: Long,
        paperTitle: String,
        sectionLabel: String,
        force: Boolean = false
    ) {
        val payload = QuestionAnswerGeneratePayload(groupId, paperTitle, sectionLabel)
        enqueueTaskAsync(BackgroundTaskType.QUESTION_ANSWER_GENERATE, payload, "answer:$groupId", force)
    }

    fun enqueueQuestionSourceVerify(
        groupId: Long,
        paperTitle: String,
        sectionLabel: String,
        sourceUrlOverride: String?,
        force: Boolean = false
    ) {
        val payload = QuestionSourceVerifyPayload(groupId, paperTitle, sectionLabel, sourceUrlOverride)
        enqueueTaskAsync(BackgroundTaskType.QUESTION_SOURCE_VERIFY, payload, "verify:$groupId", force)
    }

    fun enqueueQuestionWritingSampleSearch(
        groupId: Long,
        paperTitle: String,
        questionSnippet: String,
        force: Boolean = false
    ) {
        val payload = QuestionWritingSamplePayload(groupId, paperTitle, questionSnippet)
        enqueueTaskAsync(BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH, payload, "writing_sample:$groupId", force)
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

    private fun enqueueTaskAsync(
        type: BackgroundTaskType,
        payload: BackgroundTaskPayload,
        dedupeKey: String,
        force: Boolean
    ) {
        scope.launch {
            enqueueTaskInternal(type, payload, dedupeKey, force)
        }
    }

    private suspend fun enqueueTaskInternal(
        type: BackgroundTaskType,
        payload: BackgroundTaskPayload,
        dedupeKey: String,
        force: Boolean
    ): BackgroundTaskEnqueueResult {
        val result = dispatchLock.withLock {
            val existing = repository.getTaskByDedupeKey(dedupeKey)
            if (existing != null) {
                when (existing.status) {
                    BackgroundTaskStatus.PENDING -> return@withLock BackgroundTaskEnqueueResult.ALREADY_PENDING
                    BackgroundTaskStatus.RUNNING -> return@withLock BackgroundTaskEnqueueResult.ALREADY_RUNNING
                    BackgroundTaskStatus.SUCCESS -> {
                        if (!force) return@withLock BackgroundTaskEnqueueResult.SKIPPED_SUCCESS
                        repository.updatePayload(existing.id, type, payload)
                        repository.updateStatus(existing.id, BackgroundTaskStatus.PENDING, null)
                        repository.updateProgress(existing.id, 0, 0)
                        return@withLock BackgroundTaskEnqueueResult.RESTARTED
                    }
                    BackgroundTaskStatus.FAILED,
                    BackgroundTaskStatus.PAUSED,
                    BackgroundTaskStatus.CANCELED -> {
                        repository.updatePayload(existing.id, type, payload)
                        repository.updateStatus(existing.id, BackgroundTaskStatus.PENDING, null)
                        repository.updateProgress(existing.id, 0, 0)
                        return@withLock BackgroundTaskEnqueueResult.RESTARTED
                    }
                }
            }

            val insertedId = repository.insertTask(type, payload, dedupeKey)
            if (insertedId != -1L) {
                BackgroundTaskEnqueueResult.ENQUEUED
            } else {
                val latest = repository.getTaskByDedupeKey(dedupeKey)
                when (latest?.status) {
                    BackgroundTaskStatus.RUNNING -> BackgroundTaskEnqueueResult.ALREADY_RUNNING
                    BackgroundTaskStatus.PENDING -> BackgroundTaskEnqueueResult.ALREADY_PENDING
                    BackgroundTaskStatus.SUCCESS -> BackgroundTaskEnqueueResult.SKIPPED_SUCCESS
                    BackgroundTaskStatus.FAILED,
                    BackgroundTaskStatus.PAUSED,
                    BackgroundTaskStatus.CANCELED -> BackgroundTaskEnqueueResult.RESTARTED
                    null -> BackgroundTaskEnqueueResult.SKIPPED_SUCCESS
                }
            }
        }

        if (result == BackgroundTaskEnqueueResult.ENQUEUED || result == BackgroundTaskEnqueueResult.RESTARTED) {
            schedule()
        }
        return result
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
        if (task.payload == null) {
            scope.launch {
                repository.updateStatus(task.id, BackgroundTaskStatus.FAILED, "任务参数缺失")
                repository.updateProgress(task.id, 0, 0)
                schedule()
            }
            return
        }
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
            BackgroundTaskType.QUESTION_GENERATE -> executeQuestionGenerate(task)
            BackgroundTaskType.QUESTION_ANSWER_GENERATE -> executeAnswerGenerate(task)
            BackgroundTaskType.QUESTION_SOURCE_VERIFY -> executeSourceVerify(task)
            BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> executeWritingSampleSearch(task)
            BackgroundTaskType.UNKNOWN -> throw IllegalStateException("未知任务类型")
        }
    }

    private suspend fun executeWordOrganize(task: BackgroundTask) {
        val payload = task.payload as? WordOrganizePayload ?: throw IllegalStateException("任务参数缺失")
        val mainSnapshot = payload.mainModelSnapshot
            ?: throw IllegalStateException("旧任务缺少主模型快照，请重新开始后台整理")
        val mainApiKey = settingsDataStore.getProviderApiKey(mainSnapshot.providerName)
        if (mainApiKey.isBlank()) {
            throw IllegalStateException("API Key 未配置")
        }
        val referenceSource = runCatching { WordReferenceSource.valueOf(payload.referenceSource) }
            .getOrDefault(WordReferenceSource.FAST)
        val referenceSnapshot = if (payload.highQualityEnabled) {
            payload.referenceModelSnapshot
                ?: throw IllegalStateException("旧任务缺少参考模型快照，请重新开始后台整理")
        } else {
            null
        }
        val result = organizeWordWithAi(
            payload.spelling,
            mainApiKey,
            mainSnapshot.model,
            mainSnapshot.baseUrl,
            mainSnapshot.provider,
            highQualityEnabledOverride = payload.highQualityEnabled,
            referenceSourceOverride = referenceSource,
            referenceModelSnapshotOverride = referenceSnapshot
        ) { progress ->
            repository.updateProgress(task.id, progress.current, progress.total)
        }
        val currentWord = wordRepository.getWordById(payload.wordId) ?: throw IllegalStateException("单词不存在")
        val merged = currentWord.copy(
            phonetic = currentWord.phonetic.ifBlank { result.phonetic },
            meanings = currentWord.meanings.ifEmpty { result.meanings },
            rootExplanation = currentWord.rootExplanation.ifBlank { result.rootExplanation },
            decomposition = currentWord.decomposition.ifEmpty { result.decomposition },
            synonyms = currentWord.synonyms.ifEmpty { result.synonyms },
            similarWords = currentWord.similarWords.ifEmpty { result.similarWords },
            cognates = currentWord.cognates.ifEmpty { result.cognates },
            inflections = currentWord.inflections.ifEmpty { result.inflections },
            updatedAt = System.currentTimeMillis()
        )
        wordRepository.updateWord(merged)
    }

    private fun SettingsDataStore.AiConfig.toSnapshot(): AiModelSnapshot {
        return AiModelSnapshot(
            providerName = providerName,
            provider = provider,
            model = model,
            baseUrl = baseUrl
        )
    }

    private suspend fun migrateLegacyWordOrganizeTasks() {
        val tasks = repository.observeAllTasks().first()
            .filter { task ->
                task.type == BackgroundTaskType.WORD_ORGANIZE &&
                    task.status in setOf(
                        BackgroundTaskStatus.PENDING,
                        BackgroundTaskStatus.PAUSED,
                        BackgroundTaskStatus.FAILED
                    )
            }

        tasks.forEach { task ->
            val payload = task.payload as? WordOrganizePayload ?: return@forEach
            if (!payload.requiresSnapshotMigration()) return@forEach

            val migratedPayload = payload.copy(
                mainModelSnapshot = payload.mainModelSnapshot ?: settingsDataStore.getAiConfig(AiSettingsScope.MAIN).toSnapshot(),
                referenceModelSnapshot = if (payload.highQualityEnabled) {
                    payload.referenceModelSnapshot ?: buildReferenceSnapshot(
                        runCatching { WordReferenceSource.valueOf(payload.referenceSource) }
                            .getOrDefault(WordReferenceSource.FAST)
                    )
                } else {
                    null
                }
            )
            repository.updatePayload(task.id, task.type, migratedPayload)
        }
    }

    private suspend fun buildReferenceSnapshot(referenceSource: WordReferenceSource): AiModelSnapshot {
        val referenceScope = when (referenceSource) {
            WordReferenceSource.FAST -> AiSettingsScope.FAST
            WordReferenceSource.SEARCH -> AiSettingsScope.SEARCH
        }
        return settingsDataStore.getConfiguredAiConfig(referenceScope).toSnapshot()
    }

    private fun WordOrganizePayload.requiresSnapshotMigration(): Boolean {
        if (mainModelSnapshot == null) return true
        return highQualityEnabled && referenceModelSnapshot == null
    }

    private suspend fun executeQuestionGenerate(task: BackgroundTask) {
        val payload = task.payload as? QuestionGeneratePayload ?: throw IllegalStateException("任务参数缺失")
        val questionType = runCatching { QuestionType.valueOf(payload.questionType) }.getOrElse {
            throw IllegalStateException("题型无效")
        }
        val article = articleRepository.getArticleByIdOnce(payload.articleId)
            ?: throw IllegalStateException("文章不存在")
        val paragraphs = articleRepository.getParagraphs(article.id)

        val config = settingsDataStore.getAiConfig(AiSettingsScope.MAIN)
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("主模型未配置")
        }

        val articleText = buildArticleText(article, paragraphs)
        if (articleText.isBlank()) {
            throw IllegalStateException("文章内容为空，无法出题")
        }

        repository.updateProgress(task.id, 0, 1)
        val scanResult = questionBankAiRepository.generateQuestionsFromArticle(
            articleTitle = article.title,
            articleText = articleText,
            questionType = questionType.name,
            variant = payload.variant,
            apiKey = config.apiKey,
            model = config.model,
            baseUrl = config.baseUrl,
            provider = config.provider
        )

        val rawGroup = scanResult.questionGroups.firstOrNull()
            ?: throw IllegalStateException("出题失败：未返回题组")

        val normalizedGroup = normalizeGeneratedGroup(
            rawGroup = rawGroup,
            questionType = questionType,
            variant = payload.variant,
            article = article
        )
        validateGeneratedGroup(normalizedGroup, questionType, payload.variant)

        val now = System.currentTimeMillis()
        val finalTitle = payload.paperTitle.ifBlank { buildDefaultPaperTitle(article.title, now) }
        val paper = ExamPaper(
            uid = UUID.randomUUID().toString(),
            title = finalTitle,
            createdAt = now,
            updatedAt = now
        )

        val sentenceOptionsJson = if (
            questionType == QuestionType.SENTENCE_INSERTION ||
            questionType == QuestionType.COMMENT_OPINION_MATCH ||
            questionType == QuestionType.SUBHEADING_MATCH ||
            questionType == QuestionType.INFORMATION_MATCH
        ) {
            buildSentenceInsertionExtraData(normalizedGroup.sentenceOptions)
        } else null

        val passageParagraphs = if (
            questionType == QuestionType.INFORMATION_MATCH &&
            normalizedGroup.passageParagraphs.isEmpty() &&
            normalizedGroup.sentenceOptions.isNotEmpty()
        ) {
            normalizedGroup.sentenceOptions
        } else {
            normalizedGroup.passageParagraphs
        }

        val group = QuestionGroup(
            uid = UUID.randomUUID().toString(),
            examPaperId = 0,
            questionType = questionType,
            sectionLabel = normalizedGroup.sectionLabel?.takeIf { it.isNotBlank() }
                ?: defaultSectionLabel(questionType, payload.variant),
            orderInPaper = 0,
            directions = normalizedGroup.directions,
            passageText = passageParagraphs.joinToString("\n"),
            sourceInfo = normalizedGroup.sourceInfo,
            sourceUrl = normalizedGroup.sourceUrl,
            wordCount = normalizedGroup.wordCount,
            difficultyLevel = normalizedGroup.difficultyLevel?.let { level ->
                com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { it.name == level }
            },
            difficultyScore = normalizedGroup.difficultyScore,
            createdAt = now,
            updatedAt = now,
            paragraphs = passageParagraphs.mapIndexed { index, text ->
                ArticleParagraph(paragraphIndex = index, text = text)
            },
            items = normalizedGroup.questions.mapIndexed { index, q ->
                QuestionItem(
                    questionGroupId = 0,
                    questionNumber = if (q.questionNumber > 0) q.questionNumber else index + 1,
                    questionText = q.questionText,
                    optionA = q.optionA.ifBlank { null },
                    optionB = q.optionB.ifBlank { null },
                    optionC = q.optionC.ifBlank { null },
                    optionD = q.optionD.ifBlank { null },
                    orderInGroup = index,
                    wordCount = q.wordCount,
                    difficultyLevel = q.difficultyLevel?.let { level ->
                        com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { it.name == level }
                    },
                    difficultyScore = q.difficultyScore,
                    extraData = sentenceOptionsJson
                )
            }
        )

        val paperId = questionBankRepository.saveScannedPaper(paper, listOf(group))
        val groupList = questionBankRepository.getGroupsByPaper(paperId).first()
        val firstGroup = groupList.firstOrNull()
        if (firstGroup != null) {
            if (!article.isSaved) {
                articleRepository.markArticleSaved(article.id)
                parseArticle(article.id)
            }
            questionBankRepository.linkSourceArticle(firstGroup.id, article.id)
            val sourceUrl = article.domain.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (!sourceUrl.isNullOrBlank()) {
                questionBankRepository.updateSourceUrl(firstGroup.id, sourceUrl)
            }
            questionBankRepository.updateSourceVerification(firstGroup.id, 1, null)

            if (firstGroup.questionType == QuestionType.WRITING) {
                val snippet = firstGroup.items.firstOrNull()?.questionText?.take(300).orEmpty()
                enqueueQuestionWritingSampleSearch(
                    groupId = firstGroup.id,
                    paperTitle = finalTitle,
                    questionSnippet = snippet
                )
            } else {
                enqueueQuestionAnswerGeneration(
                    groupId = firstGroup.id,
                    paperTitle = finalTitle,
                    sectionLabel = firstGroup.sectionLabel.orEmpty()
                )
            }
        }
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
        val passageForAnswer = if (group.questionType == QuestionType.PARAGRAPH_ORDER) {
            buildString {
                if (!group.directions.isNullOrBlank()) {
                    append("Directions:\n").append(group.directions).append("\n\n")
                }
                append("Paragraphs:\n").append(group.passageText)
            }
        } else {
            group.passageText
        }

        val results = questionBankAiRepository.generateAnswers(
            passageForAnswer,
            items,
            group.questionType.name,
            config.apiKey,
            config.model,
            config.baseUrl,
            config.providerName,
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
        if (!result.matched) {
            questionBankRepository.updateSourceVerification(group.id, -1, result.errorMessage)
            throw IllegalStateException(result.errorMessage ?: "来源未匹配")
        }

        if (!result.sourceUrl.isNullOrBlank()) {
            questionBankRepository.updateSourceUrl(group.id, result.sourceUrl)
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
            questionBankRepository.updateSourceVerification(group.id, 1, null)
        } catch (e: Exception) {
            questionBankRepository.updateSourceVerification(group.id, -1, "来源文章创建失败")
            throw IllegalStateException("来源文章创建失败: ${e.message}", e)
        }
        repository.updateProgress(task.id, 1, 1)
    }

    private suspend fun executeWritingSampleSearch(task: BackgroundTask) {
        val payload = task.payload as? QuestionWritingSamplePayload ?: throw IllegalStateException("任务参数缺失")
        val group = questionBankRepository.getGroupById(payload.groupId) ?: throw IllegalStateException("题组不存在")
        val items = questionBankRepository.getItemsByGroup(group.id)
        if (items.isEmpty()) {
            repository.updateProgress(task.id, 0, 0)
            return
        }
        val config = settingsDataStore.getAiConfig(AiSettingsScope.SEARCH)
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("搜索模型未配置")
        }
        repository.updateProgress(task.id, 0, 1)
        val mainItem = items.first()
        val questionText = if (mainItem.questionText.isNotBlank()) {
            mainItem.questionText
        } else {
            payload.questionSnippet
        }
        val result = questionBankAiRepository.searchWritingSample(
            payload.paperTitle.ifBlank { group.sectionLabel.orEmpty() },
            questionText,
            config.apiKey,
            config.model,
            config.baseUrl,
            config.provider
        )
        if (!result.matched || result.sampleText.isNullOrBlank() || result.sourceUrl.isNullOrBlank()) {
            throw IllegalStateException(result.errorMessage ?: "未找到真实范文")
        }
        questionBankRepository.updateWritingSample(
            mainItem.id,
            result.sampleText,
            "WEB",
            result.sampleTitle,
            result.sourceUrl,
            result.sourceInfo
        )
        questionBankRepository.markHasAiAnswer(group.id)
        repository.updateProgress(task.id, 1, 1)
    }

    private fun buildArticleText(article: com.xty.englishhelper.domain.model.Article, paragraphs: List<ArticleParagraph>): String {
        val text = if (paragraphs.isNotEmpty()) {
            paragraphs
                .filter { it.paragraphType != com.xty.englishhelper.domain.model.ParagraphType.IMAGE }
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        } else {
            article.content
        }
        val normalized = text
            .split(Regex("\\n\\s*\\n"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return if (normalized.length > 6000) normalized.take(6000) else normalized
    }

    private fun buildDefaultPaperTitle(title: String, now: Long): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))
        val safeTitle = title.ifBlank { "未命名文章" }
        return "文章出题 - $safeTitle - $date"
    }

    private fun normalizeGeneratedGroup(
        rawGroup: com.xty.englishhelper.domain.repository.ScannedQuestionGroup,
        questionType: QuestionType,
        variant: String?,
        article: com.xty.englishhelper.domain.model.Article
    ): com.xty.englishhelper.domain.repository.ScannedQuestionGroup {
        val sourceUrl = article.domain.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val sourceInfo = article.source.ifBlank { article.title }
        val sectionLabel = rawGroup.sectionLabel?.takeIf { it.isNotBlank() }
            ?: defaultSectionLabel(questionType, variant)
        val directions = rawGroup.directions?.takeIf { it.isNotBlank() } ?: defaultDirections(questionType, variant)
        val questions = rawGroup.questions.mapIndexed { index, q ->
            q.copy(questionNumber = if (q.questionNumber > 0) q.questionNumber else index + 1)
        }
        return rawGroup.copy(
            questionType = questionType.name,
            sectionLabel = sectionLabel,
            directions = directions,
            sourceUrl = sourceUrl,
            sourceInfo = sourceInfo,
            questions = questions
        )
    }

    private fun validateGeneratedGroup(
        group: com.xty.englishhelper.domain.repository.ScannedQuestionGroup,
        questionType: QuestionType,
        variant: String?
    ) {
        val passageText = group.passageParagraphs.joinToString("\n")
        val blankRegex = Regex("__(\\d+)__")
        val blankMatches = blankRegex.findAll(passageText).toList()
        val blankCount = blankMatches.size
        val blankNumbers = blankMatches.mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.toSet()
        val questionNumbers = group.questions.mapNotNull { q ->
            q.questionNumber.takeIf { it > 0 }
        }
        val translationMarkerRegex = Regex("\\(\\((\\d+)\\)\\)")

        when (questionType.name) {
            "READING_COMPREHENSION" -> {
                if (group.passageParagraphs.isEmpty()) throw IllegalStateException("阅读理解未生成文章段落")
                if (group.questions.size != 5) throw IllegalStateException("阅读理解题数必须为 5")
            }
            "CLOZE" -> {
                if (group.passageParagraphs.isEmpty()) throw IllegalStateException("完形填空未生成文章")
                if (group.questions.size != 20) throw IllegalStateException("完形填空题数必须为 20")
                if (blankCount < group.questions.size) throw IllegalStateException("完形填空未标注足够的空位")
                if (questionNumbers.any { it !in blankNumbers }) {
                    throw IllegalStateException("完形填空空位编号与题号不一致")
                }
            }
            "TRANSLATION" -> {
                if (group.passageParagraphs.isEmpty()) throw IllegalStateException("翻译未生成文章")
                if (group.questions.isEmpty()) throw IllegalStateException("翻译题未生成题目")
                if (variant == "ENG1" && group.questions.size != 5) {
                    throw IllegalStateException("翻译（英语一）题数必须为 5")
                }
                if (variant == "ENG2" && group.questions.size != 1) {
                    throw IllegalStateException("翻译（英语二）题数必须为 1")
                }
                if (variant == "ENG1") {
                    val markerCount = translationMarkerRegex.findAll(passageText).count()
                    if (markerCount < 5) throw IllegalStateException("翻译（英语一）未标注划线句段")
                }
            }
            "WRITING" -> {
                if (group.questions.isEmpty() || group.questions.first().questionText.isBlank()) {
                    throw IllegalStateException("写作题干缺失")
                }
            }
            "PARAGRAPH_ORDER" -> {
                if (group.passageParagraphs.size < 8) throw IllegalStateException("段落排序需至少 8 段")
                if (group.questions.size != 5) throw IllegalStateException("段落排序题数必须为 5")
            }
            "SENTENCE_INSERTION" -> {
                if (group.sentenceOptions.size < 7) throw IllegalStateException("句子插入需 7 个候选句")
                if (group.questions.size != 5) throw IllegalStateException("句子插入题数必须为 5")
                if (blankCount < group.questions.size) throw IllegalStateException("句子插入未标注足够的空位")
                if (questionNumbers.any { it !in blankNumbers }) {
                    throw IllegalStateException("句子插入空位编号与题号不一致")
                }
            }
            "COMMENT_OPINION_MATCH", "SUBHEADING_MATCH" -> {
                if (group.sentenceOptions.size < 7) throw IllegalStateException("匹配题需 7 个选项")
                if (group.questions.size != 5) throw IllegalStateException("匹配题题数必须为 5")
            }
            "INFORMATION_MATCH" -> {
                if (group.passageParagraphs.size < 7 && group.sentenceOptions.size < 7) {
                    throw IllegalStateException("信息匹配需 7 个选项")
                }
                if (group.questions.size != 5) throw IllegalStateException("信息匹配题数必须为 5")
            }
        }
    }

    private fun defaultSectionLabel(questionType: QuestionType, variant: String?): String {
        return when (questionType) {
            QuestionType.TRANSLATION -> if (variant == "ENG1") "翻译（英语一）" else "翻译（英语二）"
            QuestionType.WRITING -> if (variant == "SMALL") "写作（小作文）" else "写作（大作文）"
            QuestionType.READING_COMPREHENSION -> "阅读理解"
            QuestionType.CLOZE -> "完形填空"
            QuestionType.PARAGRAPH_ORDER -> "段落排序"
            QuestionType.SENTENCE_INSERTION -> "句子插入"
            QuestionType.COMMENT_OPINION_MATCH -> "评论观点匹配"
            QuestionType.SUBHEADING_MATCH -> "小标题匹配"
            QuestionType.INFORMATION_MATCH -> "信息匹配"
            else -> questionType.displayName
        }
    }

    private fun defaultDirections(questionType: QuestionType, variant: String?): String {
        return when (questionType) {
            QuestionType.READING_COMPREHENSION -> "Read the passage and answer the questions."
            QuestionType.CLOZE -> "Read the passage and choose the best word for each blank."
            QuestionType.TRANSLATION -> if (variant == "ENG1") "Translate the underlined sentences into Chinese." else "Translate the following passage into Chinese."
            QuestionType.WRITING -> if (variant == "SMALL") "Write an application letter of about 100 words." else "Write an essay of 160-200 words."
            QuestionType.PARAGRAPH_ORDER -> "Reorder the paragraphs to form a coherent passage. Fill in the blanks."
            QuestionType.SENTENCE_INSERTION -> "Choose the best sentence for each blank."
            QuestionType.COMMENT_OPINION_MATCH -> "Match each comment with the best summary opinion."
            QuestionType.SUBHEADING_MATCH -> "Match each paragraph with the most suitable heading."
            QuestionType.INFORMATION_MATCH -> "Match each statement with the correct information."
            else -> ""
        }
    }

    private fun buildSentenceInsertionExtraData(options: List<String>): String {
        val arr = org.json.JSONArray()
        options.filter { it.isNotBlank() }.forEach { arr.put(it) }
        val obj = org.json.JSONObject()
        obj.put("options", arr)
        return obj.toString()
    }
}
