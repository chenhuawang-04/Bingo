package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.WordPool

interface WordPoolRepository {
    suspend fun getPoolsForWord(wordId: Long): List<WordPool>

    suspend fun rebuildPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    )

    suspend fun getPoolCount(dictionaryId: Long): Int

    /** Brainstorm: returns wordId -> Set<poolId> for all strategies */
    suspend fun getWordToPoolsMap(dictionaryId: Long): Map<Long, Set<Long>>

    /** Brainstorm: returns poolId -> Set<wordId> */
    suspend fun getPoolToMembersMap(dictionaryId: Long): Map<Long, Set<Long>>

    /** Returns (strategy, algorithmVersion) pairs for existing pools */
    suspend fun getPoolVersionInfo(dictionaryId: Long): List<Pair<String, String>>
}
