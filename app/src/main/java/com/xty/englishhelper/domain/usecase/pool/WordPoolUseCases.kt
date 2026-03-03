package com.xty.englishhelper.domain.usecase.pool

import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.repository.WordPoolRepository
import javax.inject.Inject

class GetWordPoolsUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(wordId: Long): List<WordPool> =
        repo.getPoolsForWord(wordId)
}

class RebuildWordPoolsUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(
        dictionaryId: Long,
        strategy: PoolStrategy,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = repo.rebuildPools(dictionaryId, strategy, onProgress)
}

class GetPoolCountUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long): Int =
        repo.getPoolCount(dictionaryId)
}

class GetPoolVersionInfoUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long): List<Pair<String, String>> =
        repo.getPoolVersionInfo(dictionaryId)
}
