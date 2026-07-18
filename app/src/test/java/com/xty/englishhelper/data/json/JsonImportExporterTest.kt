package com.xty.englishhelper.data.json

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.DictionaryPoolBackup
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordEdgeBackup
import com.xty.englishhelper.domain.model.WordPoolBackup
import com.xty.englishhelper.domain.model.WordPoolStrategyBackup
import com.xty.englishhelper.domain.model.WordPhrase
import com.xty.englishhelper.domain.model.WordPhraseSyncItem
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordPhraseTag
import com.xty.englishhelper.domain.model.WordStudyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class JsonImportExporterTest {

    private lateinit var exporter: JsonImportExporter

    @Before
    fun setUp() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        exporter = JsonImportExporter(moshi, DictionaryWordUidNormalizer())
    }

    @Test
    fun `round-trip export then import preserves data`() {
        val dictionary = Dictionary(
            dictionaryUid = "dict-uid-1",
            name = "Test Dict",
            description = "A test",
            color = 0xFF123456.toInt(),
            createdAt = 11L,
            updatedAt = 22L
        )
        val words = listOf(
            WordDetails(
                id = 1,
                dictionaryId = 1,
                spelling = "apple",
                phonetic = "/ˈæp.əl/",
                wordUid = "uid-1",
                createdAt = 111L,
                updatedAt = 222L,
                normalizedSpelling = "apple",
                meanings = listOf(Meaning("n.", "苹果")),
                rootExplanation = "from Latin",
                synonyms = listOf(SynonymInfo(word = "fruit", explanation = "general term")),
                similarWords = listOf(SimilarWordInfo(word = "ape", meaning = "猿", explanation = "少一个字母")),
                cognates = listOf(CognateInfo(word = "applet", meaning = "小程序", sharedRoot = "appl"))
            ),
            WordDetails(
                id = 2,
                dictionaryId = 1,
                spelling = "banana",
                phonetic = "/bəˈnæn.ə/",
                wordUid = "uid-2",
                createdAt = 333L,
                updatedAt = 444L,
                normalizedSpelling = "banana",
                meanings = listOf(Meaning("n.", "香蕉"))
            )
        )
        val units = listOf(
            StudyUnit(
                id = 1,
                dictionaryId = 1,
                unitUid = "unit-uid-1",
                name = "Unit 1",
                defaultRepeatCount = 3,
                createdAt = 33L,
                updatedAt = 44L
            )
        )
        val unitWordMap = mapOf(1L to listOf("uid-1", "uid-2"))
        val studyStates = listOf(
            WordStudyState(
                wordId = 1,
                studyMode = StudyMode.NORMAL,
                state = 2,
                stability = 3.173,
                difficulty = 5.71,
                due = 1000L,
                lastReviewAt = 500L,
                reps = 3,
                lapses = 1
            ),
            WordStudyState(
                wordId = 1,
                studyMode = StudyMode.BRAINSTORM,
                state = 3,
                stability = 4.0,
                difficulty = 2.5,
                due = 2000L,
                lastReviewAt = 1500L,
                reps = 5,
                lapses = 0
            )
        )
        val wordIdToUid = mapOf(1L to "uid-1", 2L to "uid-2")
        val wordPhraseSnapshot = WordPhraseSyncSnapshot(
            tags = listOf(
                WordPhraseTag(
                    tagUid = "tag-writing",
                    dictionaryId = 1,
                    name = "写作表达",
                    normalizedName = "写作表达",
                    description = "可用于作文",
                    createdAt = 700L,
                    updatedAt = 701L
                )
            ),
            phrases = listOf(
                WordPhraseSyncItem(
                    wordUid = "uid-1",
                    tagUids = listOf("tag-writing"),
                    phrase = WordPhrase(
                        phraseUid = "phrase-apple-1",
                        wordId = 1,
                        dictionaryId = 1,
                        phrase = "the apple of one's eye",
                        normalizedPhrase = "the apple of one's eye",
                        meaning = "掌上明珠",
                        example = "His daughter is the apple of his eye.",
                        usageNote = "常用于人物关系描写。",
                        confidence = 0.95f,
                        practiceCount = 6,
                        createdAt = 800L,
                        updatedAt = 801L,
                        organizedAt = 802L
                    )
                )
            )
        )

        val json = exporter.exportToJson(
            dictionary = dictionary,
            words = words,
            units = units,
            unitWordMap = unitWordMap,
            studyStates = studyStates,
            wordIdToUid = wordIdToUid,
            wordPhraseSnapshot = wordPhraseSnapshot
        )

        // Verify schemaVersion in JSON
        assertTrue(json.contains("\"schemaVersion\": 12"))

        val result = exporter.importFromJson(json)

        assertEquals("Test Dict", result.dictionary.name)
        assertEquals("A test", result.dictionary.description)
        assertEquals("dict-uid-1", result.dictionary.dictionaryUid)
        assertEquals(0xFF123456.toInt(), result.dictionary.color)
        assertEquals(11L, result.dictionary.createdAt)
        assertEquals(22L, result.dictionary.updatedAt)
        assertEquals(2, result.words.size)

        val importedApple = result.words[0]
        assertEquals("apple", importedApple.spelling)
        assertEquals("/ˈæp.əl/", importedApple.phonetic)
        assertEquals("uid-1", importedApple.wordUid)
        assertEquals(111L, importedApple.createdAt)
        assertEquals(222L, importedApple.updatedAt)
        assertEquals(1, importedApple.meanings.size)
        assertEquals("n.", importedApple.meanings[0].pos)
        assertEquals("苹果", importedApple.meanings[0].definition)
        assertEquals(1, importedApple.synonyms.size)
        assertEquals(1, importedApple.similarWords.size)
        assertEquals(1, importedApple.cognates.size)

        // Units use wordUids
        assertEquals(1, result.units.size)
        assertEquals("unit-uid-1", result.units[0].unitUid)
        assertEquals("Unit 1", result.units[0].name)
        assertEquals(3, result.units[0].repeatCount)
        assertEquals(33L, result.units[0].createdAt)
        assertEquals(44L, result.units[0].updatedAt)
        assertEquals(listOf("uid-1", "uid-2"), result.units[0].wordUids)

        // Study states use FSRS fields and preserve mode separation
        assertEquals(2, result.studyStates.size)
        val normal = result.studyStates.first { it.studyMode == StudyMode.NORMAL }
        val brainstorm = result.studyStates.first { it.studyMode == StudyMode.BRAINSTORM }
        assertEquals("uid-1", normal.wordUid)
        assertEquals(2, normal.state)
        assertEquals(3.173, normal.stability, 0.001)
        assertEquals(5.71, normal.difficulty, 0.001)
        assertEquals(1000L, normal.due)
        assertEquals(500L, normal.lastReviewAt)
        assertEquals(3, normal.reps)
        assertEquals(1, normal.lapses)
        assertEquals("uid-1", brainstorm.wordUid)
        assertEquals(3, brainstorm.state)
        assertEquals(4.0, brainstorm.stability, 0.001)
        assertEquals(2.5, brainstorm.difficulty, 0.001)
        assertEquals(2000L, brainstorm.due)
        assertEquals(1500L, brainstorm.lastReviewAt)
        assertEquals(5, brainstorm.reps)
        assertEquals(0, brainstorm.lapses)

        assertEquals(1, result.wordPhraseSnapshot.tags.size)
        assertEquals("tag-writing", result.wordPhraseSnapshot.tags.single().tagUid)
        assertEquals(1, result.wordPhraseSnapshot.phrases.size)
        val importedPhrase = result.wordPhraseSnapshot.phrases.single()
        assertEquals("uid-1", importedPhrase.wordUid)
        assertEquals("phrase-apple-1", importedPhrase.phrase.phraseUid)
        assertEquals("the apple of one's eye", importedPhrase.phrase.phrase)
        assertEquals(6, importedPhrase.phrase.practiceCount)
        assertEquals(listOf("tag-writing"), importedPhrase.tagUids)
    }

    @Test
    fun `import rejects missing schemaVersion`() {
        val json = """{"name":"Test","description":"","words":[]}"""
        try {
            exporter.importFromJson(json)
            fail("Expected exception for missing schemaVersion")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun `import rejects wrong schemaVersion`() {
        val json = """{"name":"Test","description":"","schemaVersion":1,"words":[]}"""
        try {
            exporter.importFromJson(json)
            fail("Expected exception for wrong schemaVersion")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun `import rejects old schemaVersion 3`() {
        val json = """{"name":"Test","description":"","schemaVersion":3,"words":[]}"""
        try {
            exporter.importFromJson(json)
            fail("Expected exception for old schemaVersion")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun `import rejects duplicate normalized spellings`() {
        val json = """
        {
            "name": "Test",
            "description": "",
            "schemaVersion": 4,
            "words": [
                {"spelling": "Hello", "phonetic": "", "wordUid": "uid1"},
                {"spelling": "hello", "phonetic": "", "wordUid": "uid2"}
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for duplicate spelling")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("重复拼写"))
        }
    }

    @Test
    fun `import rejects empty spelling`() {
        val json = """
        {
            "name": "Test",
            "description": "",
            "schemaVersion": 4,
            "words": [
                {"spelling": "", "phonetic": "", "wordUid": "uid1"}
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for empty spelling")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("spelling"))
        }
    }

    @Test
    fun `export produces schemaVersion 12`() {
        val dictionary = Dictionary(name = "Test", description = "")
        val json = exporter.exportToJson(dictionary, emptyList(), emptyList(), emptyMap(), emptyList(), emptyMap())
        assertTrue(json.contains("\"schemaVersion\": 12"))
    }

    @Test
    fun `schema 12 round trip preserves pools edges and empty strategy tombstones`() {
        val dictionary = Dictionary(name = "Test", description = "")
        val words = listOf(
            WordDetails(dictionaryId = 1L, spelling = "adapt", normalizedSpelling = "adapt", wordUid = "uid-a"),
            WordDetails(dictionaryId = 1L, spelling = "adopt", normalizedSpelling = "adopt", wordUid = "uid-b")
        )
        val backup = DictionaryPoolBackup(
            strategies = listOf(
                WordPoolStrategyBackup(
                    strategy = "QUALITY_FIRST",
                    updatedAt = 50L,
                    pools = listOf(
                        WordPoolBackup(
                            focusWordUid = "uid-a",
                            memberWordUids = listOf("uid-a", "uid-b"),
                            algorithmVersion = "QF_v4",
                            updatedAt = 50L,
                            qualityScore = 88
                        )
                    )
                ),
                WordPoolStrategyBackup(strategy = "BALANCED", updatedAt = 60L, pools = emptyList())
            ),
            edges = listOf(
                WordEdgeBackup(
                    wordUidA = "uid-a",
                    wordUidB = "uid-b",
                    edgeType = "FORM_SPELLING",
                    status = "support",
                    learningValue = 4,
                    relationStrength = 5,
                    confidence = 0.0,
                    evidenceSource = "user_note",
                    createdAt = 10L,
                    updatedAt = 20L
                )
            )
        )

        val json = exporter.exportToJson(
            dictionary,
            words,
            emptyList(),
            emptyMap(),
            emptyList(),
            emptyMap(),
            poolBackup = backup
        )
        val imported = exporter.importFromJson(json)

        assertEquals(backup, imported.poolBackup)
    }

    @Test
    fun `import still accepts schemaVersion 4 for backward compatibility`() {
        val json = """
        {
            "name": "Legacy",
            "description": "schema4",
            "schemaVersion": 4,
            "words": [
                {
                    "spelling": "apple",
                    "phonetic": "/ˈæp.əl/",
                    "wordUid": "legacy-uid"
                }
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)

        assertEquals("Legacy", result.dictionary.name)
        assertEquals(1, result.words.size)
        assertEquals("legacy-uid", result.words.single().wordUid)
        assertTrue(result.dictionary.dictionaryUid.isNotBlank())
    }

    @Test
    fun `import rejects duplicate wordUids`() {
        val json = """
        {
            "name": "Test",
            "description": "",
            "schemaVersion": 4,
            "words": [
                {"spelling": "Hello", "phonetic": "", "wordUid": "same-uid"},
                {"spelling": "World", "phonetic": "", "wordUid": "same-uid"}
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for duplicate wordUid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("wordUid"))
        }
    }

    @Test
    fun `import schema 5 without timestamps still assigns timestamps`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 5,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "legacy-uid"}
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)

        assertEquals(1, result.words.size)
        assertTrue(result.words.single().createdAt > 0)
        assertTrue(result.words.single().updatedAt > 0)
    }

    @Test
    fun `import canonicalizes blank wordUid when no relations depend on it`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 5,
            "words": [
                {"spelling": "apple", "phonetic": ""}
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)

        assertEquals(1, result.words.size)
        assertTrue(result.words.single().wordUid.startsWith("legacy-"))
    }

    @Test
    fun `import schema 7 without dictionary and unit ids backfills stable ids`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 7,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "units": [
                {"name": "Unit 1", "repeatCount": 2, "wordUids": ["uid-1"]}
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)

        assertTrue(result.dictionary.dictionaryUid.isNotBlank())
        assertEquals(1, result.units.size)
        assertTrue(result.units.single().unitUid.isNotBlank())
        assertTrue(result.units.single().updatedAt > 0)
    }

    @Test
    fun `import remaps blank relation references when exactly one blank word exists`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 5,
            "words": [
                {"spelling": "apple", "phonetic": ""}
            ],
            "units": [
                {"name": "Unit 1", "repeatCount": 2, "wordUids": [""]}
            ],
            "studyStates": [
                {"wordUid": "", "mode": "BRAINSTORM", "state": 2, "stability": 1.0, "difficulty": 2.0, "due": 1, "lastReviewAt": 1, "reps": 1, "lapses": 0}
            ],
            "wordPools": [
                {"memberWordUids": ["", ""], "strategy": "BALANCED", "algorithmVersion": "BALANCED_v1"}
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)
        val generatedUid = result.words.single().wordUid

        assertEquals(listOf(generatedUid), result.units.single().wordUids)
        assertEquals(generatedUid, result.studyStates.single().wordUid)
        assertEquals(StudyMode.BRAINSTORM, result.studyStates.single().studyMode)
    }

    @Test
    fun `import schema 4 study states default to normal mode`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 4,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "studyStates": [
                {"wordUid": "uid-1", "state": 2, "stability": 1.0, "difficulty": 2.0, "due": 1, "lastReviewAt": 1, "reps": 1, "lapses": 0}
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)

        assertEquals(1, result.studyStates.size)
        assertEquals(StudyMode.NORMAL, result.studyStates.single().studyMode)
    }

    @Test
    fun `import rejects ambiguous blank relation references`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 5,
            "words": [
                {"spelling": "apple", "phonetic": ""},
                {"spelling": "banana", "phonetic": ""}
            ],
            "units": [
                {"name": "Unit 1", "repeatCount": 2, "wordUids": [""]}
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for ambiguous blank wordUid reference")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("空 wordUid"))
        }
    }

    @Test
    fun `import rejects unknown relation references`() {
        val json = """
        {
            "name": "Legacy",
            "description": "",
            "schemaVersion": 5,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "studyStates": [
                {"wordUid": "uid-missing", "state": 2, "stability": 1.0, "difficulty": 2.0, "due": 1, "lastReviewAt": 1, "reps": 1, "lapses": 0}
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for unknown wordUid reference")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不存在的 wordUid"))
        }
    }

    @Test
    fun `import rejects phrase with missing tag reference`() {
        val json = """
        {
            "name": "Phrase Test",
            "description": "",
            "schemaVersion": 9,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "phraseTags": [
                {"tagUid": "tag-writing", "name": "写作表达"}
            ],
            "wordPhrases": [
                {
                    "phraseUid": "phrase-1",
                    "wordUid": "uid-1",
                    "phrase": "the apple of one's eye",
                    "tagUids": ["tag-missing"]
                }
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for missing tagUid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("tagUid"))
        }
    }

    @Test
    fun `import schema 9 phrase without practiceCount defaults to zero`() {
        val json = """
        {
            "name": "Phrase Test",
            "description": "",
            "schemaVersion": 9,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "phraseTags": [
                {"tagUid": "tag-writing", "name": "写作表达"}
            ],
            "wordPhrases": [
                {
                    "phraseUid": "phrase-1",
                    "wordUid": "uid-1",
                    "phrase": "take part in",
                    "tagUids": ["tag-writing"]
                }
            ]
        }
        """.trimIndent()

        val result = exporter.importFromJson(json)

        assertEquals(0, result.wordPhraseSnapshot.phrases.single().phrase.practiceCount)
    }

    @Test
    fun `import rejects negative phrase practice count`() {
        val json = """
        {
            "name": "Phrase Test",
            "description": "",
            "schemaVersion": 10,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "phraseTags": [
                {"tagUid": "tag-writing", "name": "写作表达"}
            ],
            "wordPhrases": [
                {
                    "phraseUid": "phrase-1",
                    "wordUid": "uid-1",
                    "phrase": "take part in",
                    "practiceCount": -1,
                    "tagUids": ["tag-writing"]
                }
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for negative practiceCount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("practiceCount"))
        }
    }

    @Test
    fun `import rejects oversized phrase and tag fields`() {
        val longPhrase = "a".repeat(121)
        val json = """
        {
            "name": "Phrase Test",
            "description": "",
            "schemaVersion": 9,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "phraseTags": [
                {"tagUid": "tag-writing", "name": "写作表达"}
            ],
            "wordPhrases": [
                {
                    "phraseUid": "phrase-1",
                    "wordUid": "uid-1",
                    "phrase": "$longPhrase",
                    "tagUids": ["tag-writing"]
                }
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for oversized phrase")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("过长"))
        }
    }

    @Test
    fun `import rejects duplicate normalized phrase per word`() {
        val json = """
        {
            "name": "Phrase Test",
            "description": "",
            "schemaVersion": 9,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "phraseTags": [
                {"tagUid": "tag-writing", "name": "写作表达"}
            ],
            "wordPhrases": [
                {"phraseUid": "phrase-1", "wordUid": "uid-1", "phrase": "take part in", "tagUids": ["tag-writing"]},
                {"phraseUid": "phrase-2", "wordUid": "uid-1", "phrase": " take   part in.", "tagUids": ["tag-writing"]}
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for duplicate phrase")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("重复词组/短语"))
        }
    }

    @Test
    fun `import rejects invalid phrase source and confidence`() {
        val json = """
        {
            "name": "Phrase Test",
            "description": "",
            "schemaVersion": 9,
            "words": [
                {"spelling": "apple", "phonetic": "", "wordUid": "uid-1"}
            ],
            "phraseTags": [
                {"tagUid": "tag-writing", "name": "写作表达"}
            ],
            "wordPhrases": [
                {
                    "phraseUid": "phrase-1",
                    "wordUid": "uid-1",
                    "phrase": "take part in",
                    "confidence": 2.0,
                    "source": "REMOTE",
                    "tagUids": ["tag-writing"]
                }
            ]
        }
        """.trimIndent()

        try {
            exporter.importFromJson(json)
            fail("Expected exception for invalid phrase metadata")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("confidence") || e.message!!.contains("来源"))
        }
    }
}
