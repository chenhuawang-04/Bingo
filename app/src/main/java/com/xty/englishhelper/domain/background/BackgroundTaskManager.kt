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
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.OnlineArticleScanScorePayload
import com.xty.englishhelper.domain.model.SyncTaskPayload
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionGeneratePayload
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.QuestionWritingSamplePayload
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.domain.model.WordReferenceSource
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordPoolReviewPayload
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.AtlanticRepository
import com.xty.englishhelper.domain.repository.CsMonitorRepository
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.ManualChunkContext
import com.xty.englishhelper.domain.repository.ManualFillResult
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.article.OnlineReadingCatalog
import com.xty.englishhelper.domain.article.OnlineReadingSection
import com.xty.englishhelper.domain.article.OnlineArticleSourceUrl
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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

internal data class PoolTaskMutexKey(
    val dictionaryId: Long,
    val strategy: String
)

internal fun poolTaskMutexKey(task: BackgroundTask): PoolTaskMutexKey? {
    return when (task.type) {
        BackgroundTaskType.WORD_POOL_REVIEW -> {
            val payload = task.payload as? WordPoolReviewPayload ?: return null
            PoolTaskMutexKey(payload.dictionaryId, payload.strategy)
        }

        BackgroundTaskType.WORD_POOL_REBUILD -> {
            val payload = task.payload as? WordPoolRebuildPayload ?: return null
            if (payload.strategy != PoolStrategy.QUALITY_FIRST.name) return null
            PoolTaskMutexKey(payload.dictionaryId, payload.strategy)
        }

        else -> null
    }
}

internal fun selectLaunchablePendingTasks(
    pendingTasks: List<BackgroundTask>,
    runningTasks: Collection<BackgroundTask>,
    slots: Int
): List<BackgroundTask> {
    if (slots <= 0) return emptyList()
    val occupiedKeys = runningTasks.mapNotNull(::poolTaskMutexKey).toMutableSet()
    val selected = mutableListOf<BackgroundTask>()
    pendingTasks.forEach { task ->
        if (selected.size >= slots) return@forEach
        val mutexKey = poolTaskMutexKey(task)
        if (mutexKey != null && mutexKey in occupiedKeys) return@forEach
        selected += task
        if (mutexKey != null) occupiedKeys += mutexKey
    }
    return selected
}

@Singleton
class BackgroundTaskManager @Inject constructor(
    private val repository: BackgroundTaskRepository,
    private val questionBankRepository: QuestionBankRepository,
    private val questionBankAiRepository: QuestionBankAiRepository,
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository,
    private val guardianRepository: GuardianRepository,
    private val csMonitorRepository: CsMonitorRepository,
    private val atlanticRepository: AtlanticRepository,
    private val wordRepository: WordRepository,
    private val organizeWordWithAi: OrganizeWordWithAiUseCase,
    private val rebuildWordPools: RebuildWordPoolsUseCase,
    private val wordPoolRepository: WordPoolRepository,
    private val createArticle: CreateArticleUseCase,
    private val parseArticle: ParseArticleUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val syncEngine: com.xty.englishhelper.data.sync.SyncEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val runningTaskInfos = ConcurrentHashMap<Long, BackgroundTask>()
    private val dispatchLock = Mutex()
    // Per-task pause/unpause/cancel handlers for pool rebuild (flag-based, not job cancel)
    private val pauseHandlers = ConcurrentHashMap<Long, () -> Unit>()
    private val unpauseHandlers = ConcurrentHashMap<Long, () -> Unit>()
    private val cancelHandlers = ConcurrentHashMap<Long, () -> Unit>()
    // 托管模式：词池任务因构建中止 FAILED 后，挂起的"10 分钟后自动续传"任务（按 taskId 索引，便于用户介入时取消）。
    private val managedResumeJobs = ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val managedResumeDelayMs = 10L * 60 * 1000  // 终止 10 分钟后自动续传
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
            scheduleManagedResumesOnStart()
            schedule()
        }
    }

    fun enqueueWordOrganize(
        wordId: Long,
        dictionaryId: Long,
        spelling: String,
        force: Boolean = false,
        referenceHints: List<String> = emptyList()
    ) {
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
                referenceHints = referenceHints
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(6),
                highQualityEnabled = highQualityEnabled,
                referenceSource = referenceSource.name,
                mainModelSnapshot = mainSnapshot,
                referenceModelSnapshot = referenceSnapshot
            )
            enqueueTaskInternal(BackgroundTaskType.WORD_ORGANIZE, payload, "word:$wordId", force)
        }
    }

    fun enqueueWordPoolRebuild(
        dictionaryId: Long,
        strategy: PoolStrategy,
        force: Boolean = false,
        rebuildMode: RebuildMode = RebuildMode.INCREMENTAL
    ) {
        val payload = WordPoolRebuildPayload(
            dictionaryId = dictionaryId,
            strategy = strategy.name,
            rebuildMode = rebuildMode.name
        )
        enqueueTaskAsync(BackgroundTaskType.WORD_POOL_REBUILD, payload, "pool:$dictionaryId:${strategy.name}", force)
    }

    /**
     * 词池审核（手动触发，独立于整理）。每次整跑，故一律 force=true：已有审核任务（SUCCESS/FAILED/…）会被重置重跑。
     * 与 WORD_POOL_REBUILD 仍使用不同 dedupeKey，但调度器会阻止同词典同策略的 QUALITY_FIRST 构建/审核并发落库。
     */
    fun enqueueWordPoolReview(
        dictionaryId: Long,
        strategy: PoolStrategy,
        force: Boolean = true
    ) {
        val payload = WordPoolReviewPayload(
            dictionaryId = dictionaryId,
            strategy = strategy.name
        )
        enqueueTaskAsync(BackgroundTaskType.WORD_POOL_REVIEW, payload, "poolreview:$dictionaryId:${strategy.name}", force)
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

    fun enqueueOnlineArticleScanScore(
        force: Boolean = false,
        rescoreAfterHours: Int = 24,
        forceRefresh: Boolean = false
    ) {
        val payload = OnlineArticleScanScorePayload(
            startedAt = System.currentTimeMillis(),
            rescoreAfterHours = rescoreAfterHours.coerceIn(1, 24 * 30),
            forceRefresh = forceRefresh
        )
        enqueueTaskAsync(
            type = BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE,
            payload = payload,
            dedupeKey = "online:scan_score",
            force = force
        )
    }

    fun enqueueCloudSync(
        force: Boolean = false,
        syncMode: String = "SMART",
        triggeredBy: String = "manual"
    ) {
        val payload = SyncTaskPayload(
            startedAt = System.currentTimeMillis(),
            syncMode = syncMode,
            triggeredBy = triggeredBy
        )
        enqueueTaskAsync(
            type = BackgroundTaskType.CLOUD_SYNC,
            payload = payload,
            dedupeKey = "cloud_sync",
            force = force
        )
    }

    fun pauseTask(taskId: Long) {
        scope.launch {
            repository.updateStatus(taskId, BackgroundTaskStatus.PAUSED, "已暂停")
            // For pool rebuild: use flag-based pause (don't cancel job, let in-flight requests complete)
            val handler = pauseHandlers[taskId]
            if (handler != null) {
                handler()
            } else {
                // Other task types: cancel the job
                runningJobs.remove(taskId)?.cancel()
            }
        }
    }

    fun resumeTask(taskId: Long) {
        scope.launch {
            cancelManagedResume(taskId)
            val task = repository.getTaskById(taskId)
            repository.updateStatus(taskId, BackgroundTaskStatus.PENDING, null)
            // For pool rebuild: unpause the running job
            val unpauseHandler = unpauseHandlers[taskId]
            if (unpauseHandler != null) {
                unpauseHandler()
            } else {
                // Other task types: re-schedule
                if (task?.type != BackgroundTaskType.WORD_POOL_REBUILD) {
                    repository.updateProgress(taskId, 0, 0)
                }
                schedule()
            }
        }
    }

    fun restartTask(taskId: Long) {
        scope.launch {
            cancelManagedResume(taskId)
            val task = repository.getTaskById(taskId)
            repository.updateStatus(taskId, BackgroundTaskStatus.PENDING, null)
            // 词池构建：保留进度，让“重启/重试”从断点续传（与 enqueueTaskInternal、resumeTask 的处理一致）。
            // 否则会清零 progressCurrent 并把 progress_message 置 null → 续传点丢失 → 整本从头重来。
            // （这正是“无论怎么修，从后台任务页点重试仍从头开始”的第三个、也是最隐蔽的入口。）
            // 要彻底重建词池请用词典页「完全重建」（独立的 force=true 路径，会清零进度并清库）。
            if (task?.type != BackgroundTaskType.WORD_POOL_REBUILD) {
                repository.updateProgress(taskId, 0, 0)
            }
            schedule()
        }
    }

    fun cancelTask(taskId: Long) {
        scope.launch {
            cancelManagedResume(taskId)
            repository.updateStatus(taskId, BackgroundTaskStatus.CANCELED, "已停止")
            // Invoke cancel handler (sets flag) before cancelling job
            cancelHandlers.remove(taskId)?.invoke()
            unpauseHandlers.remove(taskId)  // Clean up unpause handler too
            runningJobs.remove(taskId)?.cancel()
        }
    }

    fun deleteTask(taskId: Long) {
        scope.launch {
            cancelManagedResume(taskId)
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
                        // 词池“增量”构建：保留已完成进度，让重试从断点续传（重试 ≈ 继续）；
                        // 其余任务（含词池 FULL 重建）一律重置进度从头开始。
                        val resumeIncrementalPool = type == BackgroundTaskType.WORD_POOL_REBUILD &&
                            (payload as? WordPoolRebuildPayload)?.rebuildMode == RebuildMode.INCREMENTAL.name
                        if (!resumeIncrementalPool) {
                            repository.updateProgress(existing.id, 0, 0)
                        }
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
            val pendingTasks = repository.getTasksByStatuses(listOf(BackgroundTaskStatus.PENDING), 100)
            val launchableTasks = selectLaunchablePendingTasks(
                pendingTasks = pendingTasks,
                runningTasks = runningTaskInfos.values,
                slots = slots
            )
            launchableTasks.forEach { launchTask(it) }
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
        runningTaskInfos[task.id] = task
        val job = scope.launch {
            repository.incrementAttempt(task.id)
            repository.updateStatus(task.id, BackgroundTaskStatus.RUNNING, null)
            try {
                executeTask(task)
                repository.updateStatus(task.id, BackgroundTaskStatus.SUCCESS, null)
                cancelManagedResume(task.id)
            } catch (e: CancellationException) {
                val current = repository.getTaskById(task.id)
                if (current?.status == BackgroundTaskStatus.PAUSED || current?.status == BackgroundTaskStatus.CANCELED) {
                    return@launch
                }
                repository.updateStatus(task.id, BackgroundTaskStatus.CANCELED, "已停止")
            } catch (e: Exception) {
                repository.updateStatus(task.id, BackgroundTaskStatus.FAILED, e.message ?: "任务失败")
                // 托管模式：词池构建中止后定时自动续传（仅有续传进度时；缺配置类错误不反复重试）。
                maybeScheduleManagedResume(task.id)
            } finally {
                runningJobs.remove(task.id)
                runningTaskInfos.remove(task.id)
                schedule()
            }
        }
        runningJobs[task.id] = job
    }

    private suspend fun executeTask(task: BackgroundTask) {
        when (task.type) {
            BackgroundTaskType.WORD_ORGANIZE -> executeWordOrganize(task)
            BackgroundTaskType.WORD_POOL_REBUILD -> executeWordPoolRebuild(task)
            BackgroundTaskType.WORD_POOL_REVIEW -> executeWordPoolReview(task)
            BackgroundTaskType.QUESTION_GENERATE -> executeQuestionGenerate(task)
            BackgroundTaskType.QUESTION_ANSWER_GENERATE -> executeAnswerGenerate(task)
            BackgroundTaskType.QUESTION_SOURCE_VERIFY -> executeSourceVerify(task)
            BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> executeWritingSampleSearch(task)
            BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE -> executeOnlineArticleScanScore(task)
            BackgroundTaskType.CLOUD_SYNC -> executeCloudSync(task)
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
            supplementalReferenceHints = payload.referenceHints,
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
        val rebuildMode = runCatching { RebuildMode.valueOf(payload.rebuildMode) }
            .getOrDefault(RebuildMode.INCREMENTAL)

        // 续传点（**与模式无关**）：只要已有"已完成词数"就从那里继续。FULL 被中断后续传同样适用——
        // 是否清库由构建侧的 hasResumableWork 决定，与此处解耦。全新「完全重建」走 enqueue 的 SUCCESS+force
        // 已把进度清零，故 progressCurrent=0 → startIndex=-1 → 从头构建。
        val startIndex = if (task.progressCurrent > 0) task.progressCurrent else -1
        Log.i(
            "BackgroundTaskManager",
            "词池构建启动 task=${task.id} mode=$rebuildMode progressCurrent=${task.progressCurrent} " +
                "startIndex=$startIndex progressMessage=${task.progressMessage ?: "<none>"}"
        )

        val paused = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        pauseHandlers[task.id] = { paused.set(true) }
        unpauseHandlers[task.id] = { paused.set(false) }
        cancelHandlers[task.id] = { cancelled.set(true) }

        // 进度仅由单一节流写入器持久化：构建侧（含多个并发块）只更新内存中的最新快照，
        // 写入器每隔 ~300ms 落库一次最新值。彻底消除原先 fire-and-forget 写入导致的乱序/抖动。
        val latest = AtomicReference<Triple<Int, Int, String?>?>(null)

        try {
            coroutineScope {
                val writer = launch {
                    var written: Triple<Int, Int, String?>? = null
                    while (isActive) {
                        delay(300)
                        val snap = latest.get()
                        if (snap != null && snap != written) {
                            repository.updateProgress(task.id, snap.first, snap.second, snap.third)
                            written = snap
                        }
                    }
                }
                try {
                    rebuildWordPools(
                        dictionaryId = payload.dictionaryId,
                        strategy = strategy,
                        startIndex = startIndex,
                        rebuildMode = rebuildMode,
                        // 块级续传：把上次落库的进度消息传给构建侧解析续传块（**与模式无关**地传递）。
                        // 解析在 resolveStartChunk 内严格校验（断点词拼写 + 总块数）；全新构建时本就为 null。
                        // 独立于 startIndex 的 progressCurrent>0 守卫——断点词失败时 progressCurrent 仍为 0，但块续传仍要生效。
                        resumeProgressMessage = task.progressMessage,
                        isCancelled = { cancelled.get() },
                        isPaused = { paused.get() },
                        onProgress = { current, total, currentWord ->
                            latest.set(Triple(current, total, currentWord))
                        }
                    )
                } finally {
                    writer.cancel()
                }
            }
            // 成功：最终写一次 100%（清空 chunk 详情）。
            latest.get()?.let { (_, total, _) ->
                if (total > 0) repository.updateProgress(task.id, total, total, null)
            }
        } catch (e: CancellationException) {
            // 取消 / 进程结束：用 NonCancellable 落最后一次进度，供下次续传。
            withContext(NonCancellable) {
                latest.get()?.let { (current, total, msg) ->
                    if (total > 0) repository.updateProgress(task.id, current, total, msg)
                }
            }
            throw e
        } catch (e: Exception) {
            // 构建中止（如服务器多次返回不合规数据触发 PoolBuildDataException）：
            // 先用 NonCancellable 落库最后进度，以便修复后从断点续传（INCREMENTAL 重试会保留进度），
            // 再上抛由 launchTask 标记为 FAILED 并显示错误信息——这就是“暂停并报告”。
            // 必须保留 msg（含 word|已提交块数|总块数|边数）：块级续传据此从断点词的当前块继续，置 null 会退化成整词重做。
            withContext(NonCancellable) {
                latest.get()?.let { (current, total, msg) ->
                    if (total > 0) repository.updateProgress(task.id, current, total, msg)
                }
            }
            Log.w(
                "BackgroundTaskManager",
                "词池构建中止 task=${task.id}：落库续传点 progressCurrent=${latest.get()?.first} " +
                    "progressMessage=${latest.get()?.third ?: "<none>"}；重试将从此处续传。原因：${e.message?.take(160)}"
            )
            throw e
        } finally {
            pauseHandlers.remove(task.id)
            unpauseHandlers.remove(task.id)
            cancelHandlers.remove(task.id)
        }
    }

    // ── 词池审核（手动触发，独立于整理） ──

    /**
     * 词池审核：用 REVIEWER AI 逐条复查全部边，复查完用结果重建词池。
     * 每次整跑（无续传坐标系，故无 startIndex / resumeMessage）；进度按总审核边数上报，可暂停 / 取消。
     */
    private suspend fun executeWordPoolReview(task: BackgroundTask) {
        val payload = task.payload as? WordPoolReviewPayload ?: throw IllegalStateException("任务参数缺失")
        val strategy = runCatching { PoolStrategy.valueOf(payload.strategy) }.getOrElse {
            throw IllegalStateException("词池策略无效")
        }

        val paused = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        pauseHandlers[task.id] = { paused.set(true) }
        unpauseHandlers[task.id] = { paused.set(false) }
        cancelHandlers[task.id] = { cancelled.set(true) }

        // 与构建一致：单一节流写入器（每 ~300ms 落库最新进度快照）。
        val latest = AtomicReference<Triple<Int, Int, String?>?>(null)

        try {
            coroutineScope {
                val writer = launch {
                    var written: Triple<Int, Int, String?>? = null
                    while (isActive) {
                        delay(300)
                        val snap = latest.get()
                        if (snap != null && snap != written) {
                            repository.updateProgress(task.id, snap.first, snap.second, snap.third)
                            written = snap
                        }
                    }
                }
                try {
                    wordPoolRepository.reviewPools(
                        dictionaryId = payload.dictionaryId,
                        strategy = strategy,
                        isCancelled = { cancelled.get() },
                        isPaused = { paused.get() },
                        onProgress = { current, total, message ->
                            latest.set(Triple(current, total, message))
                        }
                    )
                } finally {
                    writer.cancel()
                }
            }
            // 成功：最终写一次 100%。
            latest.get()?.let { (_, total, _) ->
                if (total > 0) repository.updateProgress(task.id, total, total, "审核完成")
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                latest.get()?.let { (current, total, msg) ->
                    if (total > 0) repository.updateProgress(task.id, current, total, msg)
                }
            }
            throw e
        } catch (e: Exception) {
            // 审核中止：落最后进度后上抛，由 launchTask 标记 FAILED。审核重试即整跑（不依赖续传点）。
            withContext(NonCancellable) {
                latest.get()?.let { (current, total, msg) ->
                    if (total > 0) repository.updateProgress(task.id, current, total, msg)
                }
            }
            Log.w("BackgroundTaskManager", "词池审核中止 task=${task.id}：${e.message?.take(160)}")
            throw e
        } finally {
            pauseHandlers.remove(task.id)
            unpauseHandlers.remove(task.id)
            cancelHandlers.remove(task.id)
        }
    }

    // ── 手动填块编排（详情页调用；只在 FAILED 时可用） ──

    /** 取出当前 FAILED 词池任务"下一待填块"的上下文（候选词 + 提示词）。非 FAILED / 无待填块 / 无法定位 → null。 */
    suspend fun getPoolManualChunkContext(taskId: Long): ManualChunkContext? {
        val task = repository.getTaskById(taskId) ?: return null
        if (task.type != BackgroundTaskType.WORD_POOL_REBUILD) return null
        if (task.status != BackgroundTaskStatus.FAILED) return null
        val payload = task.payload as? WordPoolRebuildPayload ?: return null
        val progress = parsePoolProgress(task.progressMessage) ?: return null
        if (progress.committedChunks >= progress.totalChunks) return null
        return wordPoolRepository.getManualChunkContext(
            payload.dictionaryId, progress.word, progress.committedChunks, progress.totalChunks
        )
    }

    /**
     * 用用户 JSON 手动落库当前 FAILED 词池任务的"下一待填块"；成功则把进度块数推进 1。
     * 若该词所有块已齐（wordComplete）→ 自动续传到下一个词。返回结果供 UI 提示。
     */
    suspend fun manualFillPoolChunk(taskId: Long, json: String): ManualFillResult {
        val task = repository.getTaskById(taskId)
            ?: return ManualFillResult(false, error = "任务不存在")
        if (task.type != BackgroundTaskType.WORD_POOL_REBUILD) {
            return ManualFillResult(false, error = "任务类型不符")
        }
        if (task.status != BackgroundTaskStatus.FAILED) {
            return ManualFillResult(false, error = "仅在构建失败时可手动填入")
        }
        val payload = task.payload as? WordPoolRebuildPayload
            ?: return ManualFillResult(false, error = "任务参数缺失")
        val progress = parsePoolProgress(task.progressMessage)
            ?: return ManualFillResult(false, error = "无法定位断点块（进度信息缺失）")
        if (progress.committedChunks >= progress.totalChunks) {
            return ManualFillResult(false, error = "该词已无待填块")
        }
        val result = wordPoolRepository.manualFillChunk(
            payload.dictionaryId, progress.word, progress.committedChunks, progress.totalChunks, json
        )
        if (!result.success) return result

        // 推进续传点：块数 +1、边数累加；progressCurrent/Total（词级）不变。
        val newMessage = "${progress.word}|${progress.committedChunks + 1}|${progress.totalChunks}|${progress.edges + result.insertedEdges}"
        repository.updateProgress(taskId, task.progressCurrent, task.progressTotal, newMessage)

        if (result.wordComplete) {
            // 该词全部块已齐 → 自动续传（保留进度，从断点继续，会跳过该词进入下一个词）。
            cancelManagedResume(taskId)
            repository.updateStatus(taskId, BackgroundTaskStatus.PENDING, null)
            schedule()
        }
        return result
    }

    private data class PoolProgress(val word: String, val committedChunks: Int, val totalChunks: Int, val edges: Int)

    /** 解析 progress_message "word|committed|total|edges"；任一字段缺失 / 非法 → null。 */
    private fun parsePoolProgress(message: String?): PoolProgress? {
        if (message.isNullOrBlank()) return null
        val parts = message.split("|")
        if (parts.size < 3) return null
        val word = parts[0]
        if (word.isBlank()) return null
        val committed = parts[1].toIntOrNull() ?: return null
        val total = parts[2].toIntOrNull() ?: return null
        val edges = parts.getOrNull(3)?.toIntOrNull() ?: 0
        if (total <= 0 || committed < 0 || committed > total) return null
        return PoolProgress(word, committed, total, edges)
    }

    // ── 托管模式：构建中止后定时自动续传（无上限） ──

    /** FAILED 后按需排程自动续传（仅词池任务、托管模式开、且有可续传进度时）。 */
    private suspend fun maybeScheduleManagedResume(taskId: Long) {
        if (!settingsDataStore.getPoolManagedMode()) return
        val task = repository.getTaskById(taskId) ?: return
        if (task.type != BackgroundTaskType.WORD_POOL_REBUILD) return
        if (task.status != BackgroundTaskStatus.FAILED) return
        // 仅对"有续传进度的构建中止"托管；缺 API Key 等配置错误（无任何进度）不反复重试。
        val hasProgress = task.progressCurrent > 0 || !task.progressMessage.isNullOrBlank()
        if (!hasProgress) return
        scheduleManagedResume(taskId, managedResumeDelayMs)
    }

    /** 排程一次延迟自动续传（覆盖该任务已有的排程）。期间用户介入会改状态，触发时再校验。 */
    private fun scheduleManagedResume(taskId: Long, delayMs: Long) {
        managedResumeJobs.remove(taskId)?.cancel()
        val job = scope.launch {
            try {
                delay(delayMs)
                if (!settingsDataStore.getPoolManagedMode()) return@launch
                val task = repository.getTaskById(taskId) ?: return@launch
                if (task.type != BackgroundTaskType.WORD_POOL_REBUILD) return@launch
                // 期间用户可能已手动续传 / 取消 / 删除 → 状态不再是 FAILED 就放弃。
                if (task.status != BackgroundTaskStatus.FAILED) return@launch
                Log.i("BackgroundTaskManager", "托管模式：自动续传词池任务 $taskId（终止约 ${delayMs / 60000} 分钟后）")
                repository.updateStatus(taskId, BackgroundTaskStatus.PENDING, null)
                schedule()
            } finally {
                managedResumeJobs.remove(taskId)
            }
        }
        managedResumeJobs[taskId] = job
    }

    /** 应用启动时：为已 FAILED 的词池任务按 updatedAt + 10min 重新排程（托管模式开时）。 */
    private suspend fun scheduleManagedResumesOnStart() {
        if (!settingsDataStore.getPoolManagedMode()) return
        val failed = repository.getTasksByStatuses(listOf(BackgroundTaskStatus.FAILED), 100)
        failed.filter { it.type == BackgroundTaskType.WORD_POOL_REBUILD }
            .filter { it.progressCurrent > 0 || !it.progressMessage.isNullOrBlank() }
            .forEach { task ->
                val elapsed = System.currentTimeMillis() - task.updatedAt
                val remaining = (managedResumeDelayMs - elapsed).coerceIn(5_000L, managedResumeDelayMs)
                scheduleManagedResume(task.id, remaining)
            }
    }

    private fun cancelManagedResume(taskId: Long) {
        managedResumeJobs.remove(taskId)?.cancel()
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

    private suspend fun executeOnlineArticleScanScore(task: BackgroundTask) {
        val payload = task.payload as? OnlineArticleScanScorePayload
            ?: throw IllegalStateException("任务参数缺失")
        val config = settingsDataStore.getFastAiConfig()
        if (config.apiKey.isBlank() || config.model.isBlank()) {
            throw IllegalStateException("快速模型未配置，无法执行在线文章评分")
        }

        val modelKey = "${config.providerName}|${config.baseUrl.trimEnd('/')}|${config.model}"
        val sectionPlan = OnlineReadingSource.entries.flatMap { source ->
            OnlineReadingCatalog.sectionsFor(source).map { section -> source to section }
        }
        var total = 0
        var progress = 0
        val recentThreshold = payload.rescoreAfterHours.toLong() * 60L * 60L * 1000L
        val scannedUrls = mutableSetOf<String>()

        // First pass: count all candidates to set accurate total
        val allCandidates = mutableListOf<Triple<OnlineReadingSource, OnlineReadingSection, OnlineScanCandidate>>()
        for ((source, section) in sectionPlan) {
            val candidates = runCatching {
                fetchOnlineScanCandidates(source, section.key)
            }.getOrElse { e ->
                Log.w("BackgroundTaskManager", "Scan section failed: ${source.key}/${section.key}", e)
                emptyList()
            }
            for (candidate in candidates) {
                val normalizedUrl = OnlineArticleSourceUrl.normalize(candidate.url).ifBlank { candidate.url }
                if (scannedUrls.add(normalizedUrl)) {
                    allCandidates.add(Triple(source, section, candidate))
                }
            }
        }
        total = allCandidates.size.coerceAtLeast(1)
        scannedUrls.clear()
        repository.updateProgress(task.id, progress, total)

        for ((source, section, candidate) in allCandidates) {
            progress += 1
            repository.updateProgress(task.id, progress.coerceAtMost(total), total)

            val normalizedUrl = OnlineArticleSourceUrl.normalize(candidate.url).ifBlank { candidate.url }

            try {
                val now = System.currentTimeMillis()
                val existing = articleRepository.getArticleBySourceUrl(candidate.url)
                val isRecent = existing?.suitabilityUpdatedAt?.let { now - it < recentThreshold } == true
                if (!payload.forceRefresh && existing?.suitabilityScore != null && isRecent) {
                    continue
                }

                val detail = fetchOnlineArticleDetail(source, candidate.url)
                val excerpt = buildOnlineEvaluationExcerpt(detail.paragraphs)
                if (excerpt.isBlank()) continue
                val wordCount = countOnlineWords(detail.paragraphs)

                val result = articleAiRepository.evaluateArticleSuitability(
                    title = candidate.title,
                    excerpt = excerpt,
                    trailText = candidate.trailText,
                    source = source.label,
                    section = section.label,
                    wordCount = wordCount,
                    url = candidate.url,
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    provider = config.provider
                )

                val updated = articleRepository.updateSuitabilityBySourceUrl(
                    sourceUrl = candidate.url,
                    score = result.score,
                    reason = result.reason,
                    evaluatedAt = now,
                    modelKey = modelKey
                )

                if (updated == 0) {
                    articleRepository.upsertArticle(
                        com.xty.englishhelper.domain.model.Article(
                            title = candidate.title,
                            content = "",
                            articleUid = UUID.randomUUID().toString(),
                            sourceType = ArticleSourceType.MANUAL,
                            sourceTypeV2 = ArticleSourceTypeV2.ONLINE,
                            parseStatus = ArticleParseStatus.DONE,
                            summary = candidate.trailText.orEmpty(),
                            author = detail.author.ifBlank { candidate.author.orEmpty() },
                            source = source.label,
                            coverImageUrl = detail.coverImageUrl ?: candidate.thumbnailUrl,
                            domain = normalizedUrl,
                            isSaved = false,
                            wordCount = wordCount,
                            suitabilityScore = result.score,
                            suitabilityReason = result.reason,
                            suitabilityUpdatedAt = now,
                            suitabilityModel = modelKey
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w("BackgroundTaskManager", "Scan article failed: ${candidate.url}", e)
            }
        }
    }

    private data class OnlineScanCandidate(
        val title: String,
        val url: String,
        val trailText: String?,
        val thumbnailUrl: String?,
        val author: String?
    )

    private data class OnlineScanDetail(
        val author: String,
        val coverImageUrl: String?,
        val paragraphs: List<ArticleParagraph>
    )

    private suspend fun executeCloudSync(task: BackgroundTask) {
        val payload = task.payload as? SyncTaskPayload
            ?: throw IllegalStateException("任务参数缺失")

        val mode = when (payload.syncMode) {
            "FORCE_UPLOAD" -> com.xty.englishhelper.data.sync.SyncMode.FORCE_UPLOAD
            "FORCE_DOWNLOAD" -> com.xty.englishhelper.data.sync.SyncMode.FORCE_DOWNLOAD
            else -> com.xty.englishhelper.data.sync.SyncMode.SMART
        }

        syncEngine.sync(mode)
    }

    private suspend fun fetchOnlineScanCandidates(
        source: OnlineReadingSource,
        sectionKey: String
    ): List<OnlineScanCandidate> {
        return when (source) {
            OnlineReadingSource.GUARDIAN -> guardianRepository.getSectionArticles(sectionKey).map {
                OnlineScanCandidate(it.title, it.url, it.trailText, it.thumbnailUrl, it.author)
            }
            OnlineReadingSource.CSMONITOR -> csMonitorRepository.getSectionArticles(sectionKey).map {
                OnlineScanCandidate(it.title, it.url, it.trailText, it.thumbnailUrl, it.author)
            }
            OnlineReadingSource.ATLANTIC -> atlanticRepository.getSectionArticles(sectionKey).map {
                OnlineScanCandidate(it.title, it.url, it.trailText, it.thumbnailUrl, it.author)
            }
        }
    }

    private suspend fun fetchOnlineArticleDetail(
        source: OnlineReadingSource,
        articleUrl: String
    ): OnlineScanDetail {
        return when (source) {
            OnlineReadingSource.GUARDIAN -> {
                val detail = guardianRepository.getArticleDetail(articleUrl)
                OnlineScanDetail(
                    author = detail.author,
                    coverImageUrl = detail.coverImageUrl,
                    paragraphs = detail.paragraphs
                )
            }
            OnlineReadingSource.CSMONITOR -> {
                val detail = csMonitorRepository.getArticleDetail(articleUrl)
                OnlineScanDetail(
                    author = detail.author,
                    coverImageUrl = detail.coverImageUrl,
                    paragraphs = detail.paragraphs
                )
            }
            OnlineReadingSource.ATLANTIC -> {
                val detail = atlanticRepository.getArticleDetail(articleUrl)
                OnlineScanDetail(
                    author = detail.author,
                    coverImageUrl = detail.coverImageUrl,
                    paragraphs = detail.paragraphs
                )
            }
        }
    }

    private fun buildOnlineEvaluationExcerpt(paragraphs: List<ArticleParagraph>): String {
        if (paragraphs.isEmpty()) return ""
        val selected = if (paragraphs.size <= 3) paragraphs else paragraphs.take(3)
        val text = selected.joinToString("\n\n") { it.text.trim() }.trim()
        return if (text.length > 2200) text.take(2200) else text
    }

    private fun countOnlineWords(paragraphs: List<ArticleParagraph>): Int {
        if (paragraphs.isEmpty()) return 0
        return paragraphs
            .asSequence()
            .flatMap { it.text.split(Regex("\\s+")).asSequence() }
            .count { it.isNotBlank() }
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
