package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PoolQualityScorerTest {

    @Test
    fun `accumulator score matches legacy full-edge scoring`() {
        val edge1 = WordEdgeEntity(
            id = 1L,
            wordIdA = 1L,
            wordIdB = 2L,
            edgeType = "SEMANTIC_SYNONYM",
            dictionaryId = 1L,
            status = "core",
            learningValue = 5,
            confidence = 0.92,
            reason = "高频同义替换，语域一致",
            evidenceSource = "高频替换",
            difficultyCefr = "B1"
        )
        val edge2 = WordEdgeEntity(
            id = 2L,
            wordIdA = 1L,
            wordIdB = 3L,
            edgeType = "FORM_SPELLING",
            dictionaryId = 1L,
            status = "warning",
            learningValue = 4,
            confidence = 0.66,
            reason = "拼写相近，口语场景易混",
            evidenceSource = "易混词",
            difficultyCefr = "A2"
        )
        val edge3 = WordEdgeEntity(
            id = 3L,
            wordIdA = 2L,
            wordIdB = 3L,
            edgeType = "USAGE_COLLOCATION",
            dictionaryId = 1L,
            status = "support",
            learningValue = 3,
            confidence = 0.58,
            reason = "学术语域常见搭配",
            evidenceSource = "常见搭配",
            difficultyCefr = "B2"
        )
        val members = listOf(1L, 2L, 3L)
        val edgesByWordId = mapOf(
            1L to listOf(edge1, edge2),
            2L to listOf(edge1, edge3),
            3L to listOf(edge2, edge3)
        )

        val legacyScore = PoolQualityScorer.computePoolQualityScore(members, edgesByWordId)
        val accumulator = PoolQualityScorer.Accumulator().apply {
            accept(edge1)
            accept(edge2)
            accept(edge3)
        }

        assertEquals(legacyScore, accumulator.score())
    }
}
