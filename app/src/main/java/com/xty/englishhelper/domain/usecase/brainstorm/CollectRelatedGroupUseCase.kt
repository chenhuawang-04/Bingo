package com.xty.englishhelper.domain.usecase.brainstorm

import com.xty.englishhelper.domain.repository.WordPoolRepository
import javax.inject.Inject

class CollectRelatedGroupUseCase @Inject constructor(
    private val wordPoolRepository: WordPoolRepository
) {
    /**
     * 从 startWordId 出发，BFS 遍历边图，
     * 收集所有在 wordIds 范围内且未被 processedWordIds 覆盖的关联词。
     * 遇到已背过的词时停止该分支的扩展（视为边界）。
     */
    suspend operator fun invoke(
        dictionaryId: Long,
        startWordId: Long,
        wordIds: Set<Long>,
        processedWordIds: Set<Long>
    ): List<Long> {
        val edgeMap = wordPoolRepository.getWordEdgeAdjacency(dictionaryId)
        val group = mutableListOf<Long>()
        val visited = mutableSetOf(startWordId)
        var frontier = listOf(startWordId)

        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Long>()
            for (wordId in frontier) {
                val neighbors = edgeMap[wordId]?.keys ?: emptySet()
                for (neighborId in neighbors) {
                    if (neighborId in visited) continue
                    if (neighborId !in wordIds) continue
                    if (neighborId in processedWordIds) continue
                    visited.add(neighborId)
                    group.add(neighborId)
                    next.add(neighborId)
                }
            }
            frontier = next
        }
        return group
    }
}
