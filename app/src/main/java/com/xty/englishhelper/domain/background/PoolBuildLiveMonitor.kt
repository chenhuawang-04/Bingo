package com.xty.englishhelper.domain.background

import com.xty.englishhelper.domain.model.BackgroundTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单个分块（chunk）当前的整理状态——颜色语义见各枚举注释。
 * 与 MAX_EDGE_RETRIES=4 对应：第 N 次请求失败 → FAILED_N；第 4 次失败即达上限、构建中止（黑）。
 */
enum class ChunkBuildStatus {
    NOT_STARTED, // 白 —— 还未开始整理
    SUCCESS,     // 绿 —— 成功
    FAILED_1,    // 黄 —— 第 1 次请求失败
    FAILED_2,    // 褐 —— 第 2 次请求失败
    FAILED_3,    // 红 —— 第 3 次请求失败
    FAILED_4     // 黑 —— 第 4 次请求失败（已达重试上限）
}

/**
 * 单个分块的一次 AI 请求（含每次重试）的记录。点击方块时逐条展示，满足“每一次每一块请求都必须体现”。
 * @param attempt 第几次尝试（从 1 起）
 * @param response 服务器返回的原始文本；网络异常等拿不到响应时为 null
 * @param error 失败原因（成功时为 null）
 * @param success 本次尝试是否成功
 */
data class ChunkAttempt(
    val attempt: Int,
    val response: String?,
    val error: String?,
    val success: Boolean
)

/** 当前词中某个分块的整理进度（一个方块）。 */
data class ChunkProgress(
    val index: Int,
    val status: ChunkBuildStatus = ChunkBuildStatus.NOT_STARTED,
    val attempts: List<ChunkAttempt> = emptyList()
)

/** “正在整理的这一个词”的全部分块网格。 */
data class LiveWordProgress(
    val dictionaryId: Long,
    val taskType: BackgroundTaskType,
    val word: String,
    val chunks: List<ChunkProgress>
)

/**
 * 词池构建的**实时**分块进度总线（仅内存态，不入库）。
 *
 * 写入方：[com.xty.englishhelper.data.repository.WordPoolRepositoryImpl] 的 QUALITY_FIRST 构建，
 *   每开始处理一个词调用 [startWord] 重置网格，每个分块每次 AI 尝试调用 [recordAttempt] 刷新该方块颜色。
 * 读取方：词池构建详情页 ViewModel（按 dictionaryId 过滤后渲染彩色方块 + 点击查看服务器返回）。
 *
 * 为何不入库：每块每次尝试的服务器原文体量很大，逐条持久化会让 background_tasks 行膨胀。
 *   因此这是“当前词”的瞬时现场——进程被杀会丢失，但构建会从断点续传并重新填充（已提交的前缀块直接置绿）。
 *
 * 线程安全：多个分块协程并发调用 [recordAttempt]，故用 [MutableStateFlow.update]（CAS 原子）。
 *   [startWord] 仅在词边界（结构化并发栅栏之后、下一词的块启动之前）由构建主协程调用，故直接赋值安全。
 */
@Singleton
class PoolBuildLiveMonitor @Inject constructor() {

    private val _liveWord = MutableStateFlow<LiveWordProgress?>(null)
    val liveWord: StateFlow<LiveWordProgress?> = _liveWord.asStateFlow()

    /**
     * 开始处理一个新词：用 [chunkCount] 个方块初始化网格。
     * 续传时 [alreadyCommitted] 个前缀块已在之前的构建中完成，直接置为成功（绿），其余为未开始（白）。
     */
    fun startWord(
        dictionaryId: Long,
        taskType: BackgroundTaskType,
        word: String,
        chunkCount: Int,
        alreadyCommitted: Int
    ) {
        val committed = alreadyCommitted.coerceIn(0, chunkCount)
        val chunks = (0 until chunkCount).map { i ->
            if (i < committed) {
                ChunkProgress(
                    index = i,
                    status = ChunkBuildStatus.SUCCESS,
                    attempts = listOf(ChunkAttempt(1, "（已在之前的构建中完成，未留存服务器响应）", null, true))
                )
            } else {
                ChunkProgress(index = i)
            }
        }
        _liveWord.value = LiveWordProgress(dictionaryId, taskType, word, chunks)
    }

    /**
     * 记录某分块的一次尝试结果，实时更新该方块颜色。
     * @param attempt 0 起的尝试序号（0 = 第 1 次）
     */
    fun recordAttempt(
        dictionaryId: Long,
        taskType: BackgroundTaskType,
        word: String,
        chunkIndex: Int,
        attempt: Int,
        response: String?,
        error: String?,
        success: Boolean
    ) {
        _liveWord.update { cur ->
            // 词边界有结构化并发栅栏，正常不会收到非当前词的回调；不匹配则原样返回（不触发 emission）。
            if (
                cur == null ||
                cur.dictionaryId != dictionaryId ||
                cur.taskType != taskType ||
                cur.word != word
            ) return@update cur
            if (chunkIndex !in cur.chunks.indices) return@update cur

            val old = cur.chunks[chunkIndex]
            val record = ChunkAttempt(
                attempt = attempt + 1,
                response = response?.take(MAX_RESPONSE_CHARS),
                error = error?.take(MAX_ERROR_CHARS),
                success = success
            )
            val newStatus = if (success) {
                ChunkBuildStatus.SUCCESS
            } else when (attempt) {
                0 -> ChunkBuildStatus.FAILED_1
                1 -> ChunkBuildStatus.FAILED_2
                2 -> ChunkBuildStatus.FAILED_3
                else -> ChunkBuildStatus.FAILED_4
            }
            val newChunks = cur.chunks.toMutableList().also {
                it[chunkIndex] = old.copy(status = newStatus, attempts = old.attempts + record)
            }
            cur.copy(chunks = newChunks)
        }
    }

    /** 清空网格（构建成功完成、或离开当前现场时调用）。失败/取消时**不调用**，以便保留现场供点击排查。 */
    fun clear() {
        _liveWord.value = null
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 8000
        const val MAX_ERROR_CHARS = 500
    }
}
