package com.xty.englishhelper.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration32To33WordPhraseTest {

    private val testDbName = "migration-32-33-word-phrase-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate32To33_addsPracticeCountDefaultToExistingAndNewPhrases() {
        val db = helper.createDatabase(testDbName, 32)

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
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            33,
            true,
            AppDatabase.MIGRATION_32_33
        )

        migratedDb.query("SELECT practice_count FROM word_phrases WHERE id = 200").useSingleInt { count ->
            assertEquals(0, count)
        }

        migratedDb.execSQL(
            """
            INSERT INTO word_phrases (
                id, phrase_uid, word_id, dictionary_id, phrase, normalized_phrase,
                meaning, example, usage_note, register, difficulty, confidence,
                source, model, created_at, updated_at, organized_at
            ) VALUES (
                201, 'phrase-uid-2', 10, 1, 'take part in', 'take part in',
                '参加', 'Students take part in the discussion.', '活动参与', NULL, 'B1', 0.9,
                'AI', 'test-model', 901, 902, 903
            )
            """.trimIndent()
        )

        migratedDb.query("SELECT practice_count FROM word_phrases WHERE id = 201").useSingleInt { count ->
            assertEquals(0, count)
        }

        migratedDb.close()
    }

    private inline fun Cursor.useSingleInt(block: (Int) -> Unit) {
        use {
            assertTrue(it.moveToFirst())
            block(it.getInt(0))
        }
    }
}
