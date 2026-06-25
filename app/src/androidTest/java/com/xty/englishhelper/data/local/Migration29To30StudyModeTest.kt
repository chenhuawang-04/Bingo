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
class Migration29To30StudyModeTest {

    private val testDbName = "migration-29-30-study-mode-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate29To30_preservesNormalStateAndAllowsBrainstormSibling() {
        val db = helper.createDatabase(testDbName, 29)

        db.execSQL(
            """
            INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at)
            VALUES (1, 'CET4', '', 0, 1, 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO words (
                id, dictionary_id, spelling, phonetic, meanings_json, root_explanation,
                normalized_spelling, word_uid, decomposition_json, inflections_json,
                created_at, updated_at, entry_type
            ) VALUES (
                10, 1, 'apple', '', '[]', '',
                'apple', 'uid-apple', '[]', '[]',
                0, 0, 'word'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO word_study_state (
                word_id, state, step, stability, difficulty, due, last_review_at, reps, lapses
            ) VALUES (
                10, 2, NULL, 3.5, 5.0, 12345, 6789, 4, 1
            )
            """.trimIndent()
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            30,
            true,
            AppDatabase.MIGRATION_29_30
        )

        val cursor: Cursor = migratedDb.query(
            "SELECT word_id, study_mode, due, reps, lapses FROM word_study_state WHERE word_id = 10"
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(10L, it.getLong(it.getColumnIndexOrThrow("word_id")))
            assertEquals("NORMAL", it.getString(it.getColumnIndexOrThrow("study_mode")))
            assertEquals(12345L, it.getLong(it.getColumnIndexOrThrow("due")))
            assertEquals(4, it.getInt(it.getColumnIndexOrThrow("reps")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("lapses")))
        }

        migratedDb.execSQL(
            """
            INSERT INTO word_study_state (
                word_id, study_mode, state, step, stability, difficulty, due, last_review_at, reps, lapses
            ) VALUES (
                10, 'BRAINSTORM', 1, 0, 0.0, 0.0, 0, 0, 0, 0
            )
            """.trimIndent()
        )

        val countCursor = migratedDb.query(
            "SELECT COUNT(*) FROM word_study_state WHERE word_id = 10"
        )
        countCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }

        migratedDb.close()
    }
}
