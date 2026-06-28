package com.xty.englishhelper.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration31To32WordPhraseTest {

    private val testDbName = "migration-31-32-word-phrase-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate31To32_addsPhraseTablesWithoutTouchingWords() {
        val db = helper.createDatabase(testDbName, 31)

        db.execSQL(
            """
            INSERT INTO dictionaries (
                id, dictionary_uid, name, description, color, word_count, created_at, updated_at
            ) VALUES (
                1, 'dict-uid-1', 'CET4', '', 0, 1, 100, 200
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO dictionaries (
                id, dictionary_uid, name, description, color, word_count, created_at, updated_at
            ) VALUES (
                2, 'dict-uid-2', 'CET6', '', 0, 1, 100, 200
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO words (
                id, dictionary_id, spelling, phonetic, meanings_json, root_explanation,
                normalized_spelling, word_uid, decomposition_json, inflections_json,
                created_at, updated_at, entry_type
            ) VALUES (
                10, 1, 'apple', '/apple/', '[]', '',
                'apple', 'word-uid-1', '[]', '[]',
                300, 400, 'word'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO words (
                id, dictionary_id, spelling, phonetic, meanings_json, root_explanation,
                normalized_spelling, word_uid, decomposition_json, inflections_json,
                created_at, updated_at, entry_type
            ) VALUES (
                20, 2, 'apple', '/apple/', '[]', '',
                'apple', 'word-uid-2', '[]', '[]',
                300, 400, 'word'
            )
            """.trimIndent()
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            32,
            true,
            AppDatabase.MIGRATION_31_32
        )

        migratedDb.query("SELECT spelling, word_uid FROM words WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("apple", cursor.getString(cursor.getColumnIndexOrThrow("spelling")))
            assertEquals("word-uid-1", cursor.getString(cursor.getColumnIndexOrThrow("word_uid")))
        }

        migratedDb.execSQL(
            """
            INSERT INTO word_phrase_tags (
                id, tag_uid, dictionary_id, name, normalized_name, description,
                source, created_at, updated_at
            ) VALUES (
                100, 'tag-uid-1', 1, '写作表达', '写作表达', '作文可用表达',
                'AI', 500, 600
            )
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO word_phrase_tags (
                id, tag_uid, dictionary_id, name, normalized_name, description,
                source, created_at, updated_at
            ) VALUES (
                101, 'tag-uid-1', 2, '写作表达', '写作表达', '作文可用表达',
                'AI', 500, 600
            )
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO word_phrases (
                id, phrase_uid, word_id, dictionary_id, phrase, normalized_phrase,
                meaning, example, usage_note, register, difficulty, confidence,
                source, model, created_at, updated_at, organized_at
            ) VALUES (
                200, 'phrase-uid-1', 10, 1, 'the apple of one''s eye', 'the apple of one''s eye',
                '掌上明珠', 'His daughter is the apple of his eye.', '人物关系描写', NULL, 'B2', 0.95,
                'AI', 'test-model', 700, 800, 900
            )
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO word_phrases (
                id, phrase_uid, word_id, dictionary_id, phrase, normalized_phrase,
                meaning, example, usage_note, register, difficulty, confidence,
                source, model, created_at, updated_at, organized_at
            ) VALUES (
                201, 'phrase-uid-1', 20, 2, 'the apple of one''s eye', 'the apple of one''s eye',
                '掌上明珠', 'His daughter is the apple of his eye.', '人物关系描写', NULL, 'B2', 0.95,
                'AI', 'test-model', 700, 800, 900
            )
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO word_phrase_tag_cross_refs (phrase_id, tag_id, created_at)
            VALUES (200, 100, 1000)
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO word_phrase_organize_marks (
                word_id, dictionary_id, status, phrase_count, error_message, model, organized_at
            ) VALUES (
                10, 1, 'SUCCESS', 1, NULL, 'test-model', 1100
            )
            """.trimIndent()
        )

        migratedDb.query("SELECT COUNT(*) AS c FROM word_phrases WHERE word_id = 10").useCount { count ->
            assertEquals(1, count)
        }
        migratedDb.query("SELECT COUNT(*) AS c FROM word_phrase_tags WHERE tag_uid = 'tag-uid-1'").useCount { count ->
            assertEquals(2, count)
        }
        migratedDb.query("SELECT COUNT(*) AS c FROM word_phrases WHERE phrase_uid = 'phrase-uid-1'").useCount { count ->
            assertEquals(2, count)
        }
        migratedDb.query(
            """
            SELECT COUNT(*) AS c
            FROM word_phrase_tag_cross_refs r
            INNER JOIN word_phrases p ON p.id = r.phrase_id
            INNER JOIN word_phrase_tags t ON t.id = r.tag_id
            WHERE p.word_id = 10 AND t.tag_uid = 'tag-uid-1'
            """.trimIndent()
        ).useCount { count ->
            assertEquals(1, count)
        }

        try {
            migratedDb.execSQL(
                """
                INSERT INTO word_phrase_tags (
                    tag_uid, dictionary_id, name, normalized_name, description,
                    source, created_at, updated_at
                ) VALUES (
                    'tag-uid-1', 1, '同 uid 标签', '同 uid 标签', '',
                    'AI', 1200, 1200
                )
                """.trimIndent()
            )
            throw AssertionError("Expected duplicate tagUid in the same dictionary to be rejected")
        } catch (_: SQLiteConstraintException) {
            // Expected unique(dictionary_id, tag_uid).
        }

        try {
            migratedDb.execSQL(
                """
                INSERT INTO word_phrase_tags (
                    tag_uid, dictionary_id, name, normalized_name, description,
                    source, created_at, updated_at
                ) VALUES (
                    'tag-uid-2', 1, '写作表达', '写作表达', '',
                    'AI', 1200, 1200
                )
                """.trimIndent()
            )
            throw AssertionError("Expected duplicate normalized phrase tag to be rejected")
        } catch (_: SQLiteConstraintException) {
            // Expected unique(dictionary_id, normalized_name).
        }

        try {
            migratedDb.execSQL(
                """
                INSERT INTO word_phrases (
                    phrase_uid, word_id, dictionary_id, phrase, normalized_phrase,
                    meaning, example, usage_note, register, difficulty, confidence,
                    source, model, created_at, updated_at, organized_at
                ) VALUES (
                    'phrase-uid-1', 10, 1, 'apple idiom duplicate', 'apple idiom duplicate',
                    '', '', '', NULL, NULL, 0.8,
                    'AI', 'test-model', 1200, 1200, 1200
                )
                """.trimIndent()
            )
            throw AssertionError("Expected duplicate phraseUid in the same dictionary to be rejected")
        } catch (_: SQLiteConstraintException) {
            // Expected unique(dictionary_id, phrase_uid).
        }

        migratedDb.close()
    }

    private inline fun Cursor.useCount(block: (Int) -> Unit) {
        use {
            assertTrue(it.moveToFirst())
            block(it.getInt(it.getColumnIndexOrThrow("c")))
        }
    }
}
