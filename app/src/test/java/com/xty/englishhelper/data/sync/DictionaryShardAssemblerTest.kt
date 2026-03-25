package com.xty.englishhelper.data.sync

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.json.CognateJsonModel
import com.xty.englishhelper.data.json.DecompositionPartJsonModel
import com.xty.englishhelper.data.json.DictionaryCloudEntryJsonModel
import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.InflectionJsonModel
import com.xty.englishhelper.data.json.MeaningJsonModel
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.SynonymJsonModel
import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.data.json.UnitJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DictionaryShardAssemblerTest {

    private val assembler = DictionaryShardAssembler(Moshi.Builder().build())

    @Test
    fun `shard then assemble preserves dictionary content`() {
        val model = buildDictionary(wordCount = 120)

        val sharded = assembler.shard(model)
        val assembled = assembler.assemble(
            index = sharded.index,
            chunksByPath = sharded.chunks.associate { it.path to it.payload }
        )

        assertTrue(sharded.chunks.size > 1)
        assertEquals(model.name, assembled.name)
        assertEquals(model.description, assembled.description)
        assertEquals(model.units, assembled.units)
        assertEquals(model.wordPools, assembled.wordPools)
        assertEquals(
            model.words.sortedBy { it.wordUid },
            assembled.words.sortedBy { it.wordUid }
        )
        assertEquals(
            model.studyStates.sortedBy { it.wordUid },
            assembled.studyStates.sortedBy { it.wordUid }
        )
    }

    @Test
    fun `manifest dictionary count prefers new dictionary entries`() {
        val manifest = SyncManifest(
            dictionaries = listOf("legacy-a.json", "legacy-b.json"),
            dictionaryEntries = listOf(
                DictionaryCloudEntryJsonModel(
                    name = "TOEFL",
                    format = DictionaryCloudEntryJsonModel.FORMAT_SHARDED,
                    path = "dictionaries/toefl__abcd/index.json",
                    totalWords = 3000,
                    chunkCount = 9
                )
            )
        )

        assertEquals(1, manifest.dictionaryCount)
    }

    @Test
    fun `validateChunk rejects modified payload`() {
        val model = buildDictionary(wordCount = 10)
        val sharded = assembler.shard(model)
        val chunk = sharded.chunks.first()
        val tampered = chunk.payload.copy(words = chunk.payload.words.dropLast(1))

        try {
            assembler.validateChunk(chunk.ref, tampered)
            fail("Expected chunk validation to fail")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("word count mismatch"))
        }
    }

    private fun buildDictionary(wordCount: Int): DictionaryJsonModel {
        val words = (1..wordCount).map { index ->
            WordJsonModel(
                spelling = "word$index",
                phonetic = "/wɜːd$index/",
                wordUid = "uid-$index",
                meanings = listOf(MeaningJsonModel(pos = "n.", definition = "definition-$index")),
                rootExplanation = "root-$index " + "x".repeat(4000),
                decomposition = listOf(
                    DecompositionPartJsonModel(
                        segment = "seg$index",
                        role = "ROOT",
                        meaning = "meaning-$index"
                    )
                ),
                synonyms = listOf(
                    SynonymJsonModel(
                        word = "syn$index",
                        explanation = "synonym explanation $index"
                    )
                ),
                cognates = listOf(
                    CognateJsonModel(
                        word = "cog$index",
                        meaning = "cognate-$index",
                        sharedRoot = "shared-$index"
                    )
                ),
                inflections = listOf(
                    InflectionJsonModel(
                        form = "word${index}s",
                        formType = "plural"
                    )
                )
            )
        }

        val studyStates = (1..wordCount).map { index ->
            StudyStateJsonModel(
                wordUid = "uid-$index",
                state = 2,
                step = 3,
                stability = 2.5,
                difficulty = 4.0,
                due = 1000L + index,
                lastReviewAt = 2000L + index,
                reps = index,
                lapses = index % 3
            )
        }

        return DictionaryJsonModel(
            name = "Large Dictionary",
            description = "Dictionary for shard testing",
            schemaVersion = 6,
            words = words,
            units = listOf(
                UnitJsonModel(
                    name = "Unit 1",
                    repeatCount = 2,
                    wordUids = words.take(30).map { it.wordUid }
                )
            ),
            studyStates = studyStates,
            wordPools = listOf(
                WordPoolJsonModel(
                    focusWordUid = words.first().wordUid,
                    memberWordUids = words.take(5).map { it.wordUid },
                    strategy = "BALANCED",
                    algorithmVersion = "BALANCED_v1"
                )
            )
        )
    }
}
