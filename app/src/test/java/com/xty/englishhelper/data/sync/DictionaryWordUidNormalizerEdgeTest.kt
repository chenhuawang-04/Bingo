package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.WordEdgeJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
import org.junit.Test

class DictionaryWordUidNormalizerEdgeTest {
    private val normalizer = DictionaryWordUidNormalizer()

    @Test(expected = IllegalArgumentException::class)
    fun `blank edge endpoint is rejected even with one legacy blank word uid`() {
        normalizer.normalize(
            DictionaryJsonModel(
                name = "Legacy",
                words = listOf(
                    WordJsonModel(spelling = "adapt", wordUid = ""),
                    WordJsonModel(spelling = "adopt", wordUid = "uid-b")
                ),
                wordEdges = listOf(
                    WordEdgeJsonModel(
                        wordUidA = "",
                        wordUidB = "uid-b",
                        edgeType = "FORM_SPELLING"
                    )
                )
            )
        )
    }
}
