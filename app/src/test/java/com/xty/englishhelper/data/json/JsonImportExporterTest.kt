package com.xty.englishhelper.data.json

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
        exporter = JsonImportExporter(moshi)
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
                normalizedSpelling = "banana",
                meanings = listOf(Meaning("n.", "香蕉"))
            )
        )
        val units = listOf(
            StudyUnit(id = 1, dictionaryId = 1, name = "Unit 1", defaultRepeatCount = 3)
        )
        val unitWordMap = mapOf(1L to listOf("uid-1", "uid-2"))
        val studyStates = listOf(
            WordStudyState(wordId = 1, remainingReviews = 5, easeLevel = 2, nextReviewAt = 1000L, lastReviewedAt = 500L)
        )
        val wordIdToUid = mapOf(1L to "uid-1", 2L to "uid-2")

        val json = exporter.exportToJson(dictionary, words, units, unitWordMap, studyStates, wordIdToUid)

        // Verify schemaVersion in JSON
        assertTrue(json.contains("\"schemaVersion\": 3"))

        val result = exporter.importFromJson(json)

        assertEquals("Test Dict", result.dictionary.name)
        assertEquals("A test", result.dictionary.description)
        assertEquals(2, result.words.size)

        val importedApple = result.words[0]
        assertEquals("apple", importedApple.spelling)
        assertEquals("/ˈæp.əl/", importedApple.phonetic)
        assertEquals("uid-1", importedApple.wordUid)
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

        // Study states use wordUid
        assertEquals(1, result.studyStates.size)
        assertEquals("uid-1", result.studyStates[0].wordUid)
        assertEquals(5, result.studyStates[0].remainingReviews)
        assertEquals(2, result.studyStates[0].easeLevel)
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
    fun `import rejects old schemaVersion 2`() {
        val json = """{"name":"Test","description":"","schemaVersion":2,"words":[]}"""
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
            "schemaVersion": 3,
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
            "schemaVersion": 3,
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
    fun `export produces schemaVersion 3`() {
        val dictionary = Dictionary(name = "Test", description = "")
        val json = exporter.exportToJson(dictionary, emptyList(), emptyList(), emptyMap(), emptyList(), emptyMap())
        assertTrue(json.contains("\"schemaVersion\": 3"))
    }

    @Test
    fun `import rejects duplicate wordUids`() {
        val json = """
        {
            "name": "Test",
            "description": "",
            "schemaVersion": 3,
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
}
