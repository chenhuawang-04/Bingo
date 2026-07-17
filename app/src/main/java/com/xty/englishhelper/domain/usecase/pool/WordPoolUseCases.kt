package com.xty.englishhelper.domain.usecase.pool

import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
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
        startIndex: Int = -1,
        rebuildMode: RebuildMode = RebuildMode.INCREMENTAL,
        resumeProgressMessage: String? = null,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onProgress: (Int, Int, String?) -> Unit = { _, _, _ -> }
    ) = repo.rebuildPools(dictionaryId, strategy, startIndex, rebuildMode, resumeProgressMessage, isCancelled, isPaused, onProgress)
}

class GetPoolCountUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long): Int =
        repo.getPoolCount(dictionaryId)
}

/** 该词典已建边总数，用于判断「词池提纯」是否可用。 */
class GetPoolEdgeCountUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long): Int =
        repo.getEdgeCount(dictionaryId)
}

class GetPoolVersionInfoUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long): List<Pair<String, String>> =
        repo.getPoolVersionInfo(dictionaryId)
}

class AuditQualityFirstPoolsUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long) = repo.auditQualityFirstPools(dictionaryId)
}

class RepairQualityFirstPoolsUseCase @Inject constructor(
    private val repo: WordPoolRepository
) {
    suspend operator fun invoke(dictionaryId: Long) =
        repo.repairQualityFirstPoolsFromExistingEdges(dictionaryId)
}
