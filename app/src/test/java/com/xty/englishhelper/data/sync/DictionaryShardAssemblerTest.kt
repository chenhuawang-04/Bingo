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
import com.xty.englishhelper.data.json.WordEdgeJsonModel
import com.xty.englishhelper.data.json.WordPhraseJsonModel
import com.xty.englishhelper.data.json.WordPhraseTagJsonModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import com.xty.englishhelper.data.json.WordPoolStrategyJsonModel
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
        assertEquals(model.dictionaryUid, sharded.entry.dictionaryUid)
        assertEquals(model.name, assembled.name)
        assertEquals(model.description, assembled.description)
        assertEquals(model.dictionaryUid, assembled.dictionaryUid)
        assertEquals(model.schemaVersion, sharded.index.dictionarySchemaVersion)
        assertEquals(model.schemaVersion, assembled.schemaVersion)
        assertEquals(model.color, assembled.color)
        assertEquals(model.createdAt, assembled.createdAt)
        assertEquals(model.updatedAt, assembled.updatedAt)
        assertEquals(model.units, assembled.units)
        assertEquals(model.wordPools, assembled.wordPools)
        assertEquals(model.wordPoolStrategies, assembled.wordPoolStrategies)
        assertEquals(model.wordEdges.sortedBy { it.wordUidB }, assembled.wordEdges.sortedBy { it.wordUidB })
        assertEquals(model.phraseTags, assembled.phraseTags)
        assertEquals(
            model.words.sortedBy { it.wordUid },
            assembled.words.sortedBy { it.wordUid }
        )
        assertEquals(
            model.studyStates.sortedBy { it.wordUid },
            assembled.studyStates.sortedBy { it.wordUid }
        )
        assertEquals(
            model.wordPhrases.sortedBy { it.phraseUid },
            assembled.wordPhrases.sortedBy { it.phraseUid }
        )
        assertEquals(model.wordPhrases.size, sharded.index.totalWordPhrases)
        assertEquals(model.wordEdges.size, sharded.index.totalWordEdges)
        assertTrue(sharded.index.wordEdges.isEmpty())
        assertEquals(model.wordEdges.size, sharded.chunks.sumOf { it.payload.wordEdges.size })
        assertEquals(model.phraseTags, sharded.index.phraseTags)
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

    @Test
    fun `streaming accumulator preserves each edge exactly once`() {
        val model = buildDictionary(wordCount = 120)
        val sharded = assembler.shard(model)
        val accumulator = assembler.newAccumulator(sharded.index)

        sharded.chunks.forEach { chunk -> accumulator.accept(chunk.ref, chunk.payload) }
        val assembled = accumulator.build()

        assertEquals(model.wordEdges.size, assembled.wordEdges.size)
        assertEquals(
            model.wordEdges.sortedBy { it.wordUidB },
            assembled.wordEdges.sortedBy { it.wordUidB }
        )
    }

    @Test
    fun `buildFolderPath stays stable for same dictionary uid`() {
        val first = assembler.buildFolderPath("dict-uid-1", "First Name")
        val second = assembler.buildFolderPath("dict-uid-1", "Second Name")

        assertEquals(first, second)
        assertTrue(first.contains("dict-uid-1"))
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
            dictionaryUid = "dict-uid-1",
            name = "Large Dictionary",
            description = "Dictionary for shard testing",
            color = 0xFF123456.toInt(),
            schemaVersion = 12,
            createdAt = 111L,
            updatedAt = 222L,
            words = words,
            units = listOf(
                UnitJsonModel(
                    unitUid = "unit-uid-1",
                    name = "Unit 1",
                    repeatCount = 2,
                    createdAt = 333L,
                    updatedAt = 444L,
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
            ),
            wordPoolStrategies = listOf(
                WordPoolStrategyJsonModel(
                    strategy = "BALANCED",
                    updatedAt = 7000L,
                    pools = listOf(
                        WordPoolJsonModel(
                            focusWordUid = words.first().wordUid,
                            memberWordUids = words.take(5).map { it.wordUid },
                            strategy = "BALANCED",
                            algorithmVersion = "BALANCED_v1",
                            updatedAt = 7000L
                        )
                    )
                ),
                WordPoolStrategyJsonModel(
                    strategy = "QUALITY_FIRST",
                    updatedAt = 7100L,
                    pools = emptyList()
                )
            ),
            wordEdges = (2..wordCount).map { index ->
                WordEdgeJsonModel(
                    wordUidA = "uid-1",
                    wordUidB = "uid-$index",
                    edgeType = "SEMANTIC_SYNONYM",
                    confidence = 0.8,
                    updatedAt = 8000L + index
                )
            },
            phraseTags = listOf(
                WordPhraseTagJsonModel(
                    tagUid = "tag-writing",
                    name = "写作表达",
                    normalizedName = "写作表达",
                    description = "作文可用表达"
                )
            ),
            wordPhrases = words.take(wordCount / 2).mapIndexed { index, word ->
                WordPhraseJsonModel(
                    phraseUid = "phrase-${index + 1}",
                    wordUid = word.wordUid,
                    phrase = "phrase for ${word.spelling}",
                    normalizedPhrase = "phrase for ${word.spelling}",
                    meaning = "短语-${index + 1}",
                    example = "${word.spelling} appears in a useful phrase.",
                    practiceCount = index + 1,
                    tagUids = listOf("tag-writing"),
                    updatedAt = 9000L + index
                )
            }
        )
    }
}
