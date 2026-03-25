package com.xty.englishhelper.data.json

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails
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
        val dictionary = Dictionary(name = "Test Dict", description = "A test")
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
            StudyUnit(id = 1, dictionaryId = 1, name = "Unit 1", defaultRepeatCount = 3)
        )
        val unitWordMap = mapOf(1L to listOf("uid-1", "uid-2"))
        val studyStates = listOf(
            WordStudyState(
                wordId = 1,
                state = 2,
                stability = 3.173,
                difficulty = 5.71,
                due = 1000L,
                lastReviewAt = 500L,
                reps = 3,
                lapses = 1
            )
        )
        val wordIdToUid = mapOf(1L to "uid-1", 2L to "uid-2")

        val json = exporter.exportToJson(dictionary, words, units, unitWordMap, studyStates, wordIdToUid)

        // Verify schemaVersion in JSON
        assertTrue(json.contains("\"schemaVersion\": 6"))

        val result = exporter.importFromJson(json)

        assertEquals("Test Dict", result.dictionary.name)
        assertEquals("A test", result.dictionary.description)
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
        assertEquals("Unit 1", result.units[0].name)
        assertEquals(3, result.units[0].repeatCount)
        assertEquals(listOf("uid-1", "uid-2"), result.units[0].wordUids)

        // Study states use FSRS fields
        assertEquals(1, result.studyStates.size)
        assertEquals("uid-1", result.studyStates[0].wordUid)
        assertEquals(2, result.studyStates[0].state)
        assertEquals(3.173, result.studyStates[0].stability, 0.001)
        assertEquals(5.71, result.studyStates[0].difficulty, 0.001)
        assertEquals(1000L, result.studyStates[0].due)
        assertEquals(500L, result.studyStates[0].lastReviewAt)
        assertEquals(3, result.studyStates[0].reps)
        assertEquals(1, result.studyStates[0].lapses)
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
    fun `export produces schemaVersion 6`() {
        val dictionary = Dictionary(name = "Test", description = "")
        val json = exporter.exportToJson(dictionary, emptyList(), emptyList(), emptyMap(), emptyList(), emptyMap())
        assertTrue(json.contains("\"schemaVersion\": 6"))
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
                {"wordUid": "", "state": 2, "stability": 1.0, "difficulty": 2.0, "due": 1, "lastReviewAt": 1, "reps": 1, "lapses": 0}
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
}
