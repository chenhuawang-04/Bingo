package com.xty.englishhelper.domain.pool

import com.xty.englishhelper.domain.model.EdgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPoolEngineRelationTest {

    @Test
    fun `balanced build exposes typed local relation evidence`() {
        val candidates = listOf(
            PoolCandidate(
                index = 0,
                wordId = 1L,
                spelling = "adapt",
                meanings = listOf("适应"),
                synonymSpellings = listOf("adjust"),
                similarSpellings = emptyList(),
                cognateSpellings = emptyList(),
                associatedWordIds = emptyList()
            ),
            PoolCandidate(
                index = 1,
                wordId = 2L,
                spelling = "adopt",
                meanings = listOf("采用"),
                synonymSpellings = emptyList(),
                similarSpellings = emptyList(),
                cognateSpellings = emptyList(),
                associatedWordIds = emptyList()
            ),
            PoolCandidate(
                index = 2,
                wordId = 3L,
                spelling = "adjust",
                meanings = listOf("调整"),
                synonymSpellings = emptyList(),
                similarSpellings = emptyList(),
                cognateSpellings = emptyList(),
                associatedWordIds = emptyList()
            )
        )

        val build = WordPoolEngine().buildPoolsWithRelations(candidates)

        assertTrue(build.relations.any {
            it.indexA == 0 && it.indexB == 1 && it.edgeType == EdgeType.FORM_SPELLING &&
                it.evidenceSource == "balanced_local"
        })
        assertTrue(build.relations.any {
            it.indexA == 0 && it.indexB == 2 && it.edgeType == EdgeType.SEMANTIC_SYNONYM &&
                it.evidenceSource == "balanced_local"
        })
    }

    @Test
    fun `ai merge splits oversized components using persisted relations`() {
        val candidates = (0 until 16).map { index ->
            PoolCandidate(
                index = index,
                wordId = index.toLong() + 1,
                spelling = "word-$index",
                meanings = emptyList(),
                synonymSpellings = emptyList(),
                similarSpellings = emptyList(),
                cognateSpellings = emptyList(),
                associatedWordIds = emptyList()
            )
        }
        val localRelations = (1 until 15).map { leaf ->
            BalancedPoolRelation(
                indexA = 0,
                indexB = leaf,
                edgeType = EdgeType.SEMANTIC_SYNONYM,
                evidenceSource = "balanced_local"
            )
        }
        val existing = BalancedPoolBuild(
            pools = listOf(BuiltPool(memberIndices = (0 until 15).toList(), coreIndex = 0)),
            relations = localRelations
        )

        val merged = WordPoolEngine().mergeAiGroupsWithRelations(
            candidates = candidates,
            existingBuild = existing,
            aiGroups = listOf(listOf(14, 15))
        )

        val persistedEdges = merged.relations.map { relation ->
            QualityPoolEdge(relation.indexA.toLong(), relation.indexB.toLong())
        }
        assertEquals((0L..15L).toSet(), merged.pools.flatMap { it.memberIndices }.map(Int::toLong).toSet())
        assertTrue(merged.pools.all { it.memberIndices.size in 2..15 })
        assertTrue(merged.pools.all { pool ->
            QualityFirstPoolPlanner.isConnectedPool(pool.memberIndices.map(Int::toLong), persistedEdges)
        })
    }

    @Test
    fun `shared meaning produces a linear spanning relation set instead of a clique`() {
        val candidates = (0 until 200).map { index ->
            PoolCandidate(
                index = index,
                wordId = index.toLong() + 1,
                spelling = "term-${index.toString().padStart(4, '0')}",
                meanings = listOf("共同语义片段用于规模测试"),
                synonymSpellings = emptyList(),
                similarSpellings = emptyList(),
                cognateSpellings = emptyList(),
                associatedWordIds = emptyList()
            )
        }

        val build = WordPoolEngine().buildPoolsWithRelations(candidates)

        assertEquals(candidates.indices.toSet(), build.pools.flatMap { it.memberIndices }.toSet())
        assertTrue(build.relations.size <= candidates.size - 1)
    }
}
