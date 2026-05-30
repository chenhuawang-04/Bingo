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
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
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
}
