package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.domain.model.WordDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeParserValidationTest {

    @Test(expected = RetryableEdgeException::class)
    fun `out of range candidate index is rejected`() {
        EdgeParser.parseAndValidateEdgeResponse(
            """[{"i":4,"edge_type":"FORM_SPELLING","reason":"拼写相似","confidence":0.9}]""",
            maxValue = 2
        )
    }

    @Test
    fun `rejected semantic edge does not suppress a later valid edge in the same cluster`() {
        val target = WordDetails(
            id = 1L,
            dictionaryId = 1L,
            spelling = "large",
            meanings = listOf(com.xty.englishhelper.domain.model.Meaning("adj.", "大的"))
        )
        val other = WordDetails(
            id = 2L,
            dictionaryId = 1L,
            spelling = "big",
            meanings = listOf(com.xty.englishhelper.domain.model.Meaning("adj.", "大的"))
        )
        val parsed = EdgeParser.parseAndValidateEdgeResponse(
            """[
                {"i":0,"edge_type":"SEMANTIC_OVERLAP","reason":"有关联但未绑定义项","confidence":0.9},
                {"i":0,"edge_type":"SEMANTIC_SYNONYM","reason":"两词在该释义中都是同义词，表示尺寸大","confidence":0.9}
            ]""".trimIndent(),
            maxValue = 1
        )

        val accepted = EdgeParser.applyHardThresholds(parsed, target, listOf(other))

        assertEquals(1, accepted.size)
        assertEquals("SEMANTIC_SYNONYM", accepted.single().edgeType.dbValue)
    }

    @Test(expected = RetryableEdgeException::class)
    fun `balanced ai grouping rejects arbitrary non json response`() {
        EdgeParser.parseJsonIntArrayOfArrays("no related groups", maxValue = 4)
    }

    @Test(expected = RetryableEdgeException::class)
    fun `balanced ai grouping rejects partially out of range group`() {
        EdgeParser.parseJsonIntArrayOfArrays("[[0,9]]", maxValue = 4)
    }

    @Test
    fun `balanced ai grouping accepts explicit empty array`() {
        assertTrue(EdgeParser.parseJsonIntArrayOfArrays("[]", maxValue = 4).isEmpty())
    }
}
