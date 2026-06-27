package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.EdgeNeighbor
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordGraph
import com.xty.englishhelper.domain.model.WordGraphEdgeDetail
import com.xty.englishhelper.domain.model.WordPool

interface WordPoolRepository {
    suspend fun getPoolsForWord(wordId: Long): List<WordPool>

    /**
     * 轻量预览：仅取当前词满足最低置信度门槛的邻居，用于背词页预答案态的关系图。
     * 同一邻居可能存在多条不同 edge_type，会聚合到一个预览节点中。
     */
    suspend fun getWordEdgePreviews(
        dictionaryId: Long,
        wordId: Long,
        minConfidence: Double
    ): List<WordEdgeNeighborPreview>

    /**
     * 用户手动确认当前词与另一词相关时，仅把两词之间已存在的边提升为强关联：
     * - confidence 置为 1.0
     * - 不触发 QUALITY_FIRST 词池重算
     * 返回 false 表示当前两词之间尚无任何边。
     */
    suspend fun confirmWordRelation(
        dictionaryId: Long,
        wordId: Long,
        relatedWordId: Long
    ): Boolean

    /**
     * 用户在背词页手动补便签、但当前两词尚无边时，只为这对词直连一条边：
     * - 若已存在边，则仅把其 confidence 置为 1.0
     * - 若不存在边，则基于拼写/词根启发式落一条边
     * - 不触发 QUALITY_FIRST 词池重算
     */
    suspend fun organizeWordNoteRelation(
        dictionaryId: Long,
        wordId: Long,
        relatedWordId: Long
    )

    /**
     * 装配整部词典的「关系图」用于可视化：全部词作节点、[com.xty.englishhelper.data.local.entity.WordEdgeEntity]
     * 作彩色 typed 边、边图的连通分量作簇。节点只取轻量投影（id+拼写），释义点击时再用 [getWordDetail] 懒加载。
     */
    suspend fun getWordRelationGraph(dictionaryId: Long): WordGraph

    /** 懒加载单个词的完整释义（点击关系图节点时取用）。 */
    suspend fun getWordDetail(wordId: Long): WordDetails?

    /** 懒加载关系图中单条边的完整解释信息（点击关系边时取用）。 */
    suspend fun getWordGraphEdgeDetail(edgeId: Long): WordGraphEdgeDetail?

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

    /** 该词典已建边总数（用于判断是否可发起「词池提纯」）。 */
    suspend fun getEdgeCount(dictionaryId: Long): Int

    /**
     * 词池提纯（手动触发，独立于整理）：用 REVIEWER AI 逐条评估该词典的全部边，
     * 据裁决降低劣质边置信度或调整状态；不删除边，也不重建词池。
     * 每次整跑（无块级续传）；可暂停 / 取消；[onProgress] 上报 (已提纯边数, 总边数, 文案)。
     */
    suspend fun reviewPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onProgress: (current: Int, total: Int, message: String?) -> Unit = { _, _, _ -> }
    )

    /** Brainstorm: returns wordId -> Set<poolId> for all strategies */
    suspend fun getWordToPoolsMap(dictionaryId: Long): Map<Long, Set<Long>>

    /** Brainstorm: returns poolId -> Set<wordId> */
    suspend fun getPoolToMembersMap(dictionaryId: Long): Map<Long, Set<Long>>

    /** Returns (strategy, algorithmVersion) pairs for existing pools */
    suspend fun getPoolVersionInfo(dictionaryId: Long): List<Pair<String, String>>

    /** Graph adjacency: wordId -> Map<neighborId, Set<EdgeType>> */
    suspend fun getWordEdgeAdjacency(dictionaryId: Long): Map<Long, Map<Long, Set<EdgeType>>>

    /**
     * 富边邻接（头脑风暴选词 / 展示用）：wordId -> 该词全部关联邻居（[EdgeNeighbor] 携带
     * strength / confidence / learningValue / status / reason / example / register / cefr / warning）。
     * 双向定向：每条边在两端各出现一次，邻居取对端。仅跳过自环与未知类型，**不做质量过滤**——
     * 阈值与排序交由上层选词逻辑决定。复用 [com.xty.englishhelper.data.local.dao.WordEdgeDao.getAllEdges]
     * 投影，零新查询、零 DB 迁移。
     */
    suspend fun getWordEdgeAdjacencyDetailed(dictionaryId: Long): Map<Long, List<EdgeNeighbor>>

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

data class WordEdgeNeighborPreview(
    val neighborId: Long,
    val spelling: String,
    val edgeTypes: Set<EdgeType>
)
