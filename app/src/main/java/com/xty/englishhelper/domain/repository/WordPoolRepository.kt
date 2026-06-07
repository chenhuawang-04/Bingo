package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordPool

interface WordPoolRepository {
    suspend fun getPoolsForWord(wordId: Long): List<WordPool>

    suspend fun rebuildPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        startIndex: Int = -1,
        rebuildMode: RebuildMode = RebuildMode.INCREMENTAL,
        resumeProgressMessage: String? = null,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onProgress: (current: Int, total: Int, currentWord: String?) -> Unit = { _, _, _ -> }
    )

    suspend fun getPoolCount(dictionaryId: Long): Int

    /** Brainstorm: returns wordId -> Set<poolId> for all strategies */
    suspend fun getWordToPoolsMap(dictionaryId: Long): Map<Long, Set<Long>>

    /** Brainstorm: returns poolId -> Set<wordId> */
    suspend fun getPoolToMembersMap(dictionaryId: Long): Map<Long, Set<Long>>

    /** Returns (strategy, algorithmVersion) pairs for existing pools */
    suspend fun getPoolVersionInfo(dictionaryId: Long): List<Pair<String, String>>

    /** Graph adjacency: wordId -> Map<neighborId, Set<EdgeType>> */
    suspend fun getWordEdgeAdjacency(dictionaryId: Long): Map<Long, Map<Long, Set<EdgeType>>>

    /**
     * TEMPORARY: Classify words into entry_type (word/root/phrase) using AI.
     * Processes batches of 50 words. Returns total classified count.
     * THIS FEATURE SHOULD BE REMOVED after all dictionaries are classified.
     */
    suspend fun classifyEntryTypes(
        dictionaryId: Long,
        isCancelled: () -> Boolean,
        onProgress: (classified: Int, total: Int) -> Unit
    ): Int

    /**
     * 手动填块：取出断点词第 [chunkIndex] 组（0 起）的上下文——候选词列表 + 完整提示词——供详情页展示，
     * 让用户照着 i 序号构造 JSON（或复制提示词到外部工具生成）。
     * [chunkIndex] 应为 progress_message 中的"已提交块数"（即下一待整理 / 失败的那块）。
     * [totalChunks] 用于与当前 chunkSize 推算出的总块数比对，防止窗口大小变更后坐标系错位。
     * 返回 null 表示无法定位（词不存在 / chunkSize 已变更 / 越界 / 该块无候选词）。
     */
    suspend fun getManualChunkContext(
        dictionaryId: Long,
        wordSpelling: String,
        chunkIndex: Int,
        totalChunks: Int
    ): ManualChunkContext?

    /**
     * 手动填块：用用户粘贴的 JSON（与边生成 AI 返回同格式）落库断点词第 [chunkIndex] 组的边。
     * 复用 EdgeParser 校验链；空数组 `[]` 合法（该块判定无边，可用于直接跳过敏感块）。
     * 先删该块覆盖的前驱区残边再插入，故重复填写幂等。
     * 仅负责"落库边 + 刷新实时网格"，不推进任务进度（由 BackgroundTaskManager 负责）。
     */
    suspend fun manualFillChunk(
        dictionaryId: Long,
        wordSpelling: String,
        chunkIndex: Int,
        totalChunks: Int,
        json: String
    ): ManualFillResult
}

/** 手动填块时展示给用户的某个分块上下文。 */
data class ManualChunkContext(
    val targetSpelling: String,
    val chunkIndex: Int,          // 0 起；界面显示为"第 chunkIndex+1 组"
    val totalChunks: Int,
    val candidates: List<ManualChunkCandidate>,
    val promptText: String        // 该块完整提示词，供"复制"后到外部工具生成 JSON
)

/** 分块窗口内的一个候选词；[index] 即 JSON 中 "i" 字段应使用的序号。 */
data class ManualChunkCandidate(
    val index: Int,
    val spelling: String,
    val pos: String,
    val definition: String
)

/** 手动填块结果。[wordComplete] 为 true 表示该词所有块已齐，可触发自动续传到下一个词。 */
data class ManualFillResult(
    val success: Boolean,
    val insertedEdges: Int = 0,
    val wordComplete: Boolean = false,
    val error: String? = null
)
