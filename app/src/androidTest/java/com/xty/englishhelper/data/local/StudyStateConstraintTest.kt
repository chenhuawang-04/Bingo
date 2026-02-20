package com.xty.englishhelper.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyStateConstraintTest {

    private val testDbName = "study-state-constraint-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun createV3Database(): android.database.sqlite.SQLiteDatabase {
        // Create v2 first, then migrate to v3
        val db = helper.createDatabase(testDbName, 2)
        db.close()
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.MIGRATION_2_3)
        return migratedDb
    }

    @Test
    fun deletingWordCascadesDeletesStudyState() {
        val db = createV3Database()

        val now = System.currentTimeMillis()

        // Insert dictionary
        db.execSQL(
            "INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at) VALUES (1, 'Test', '', 0, 1, $now, $now)"
        )

        // Insert word
        db.execSQL(
            "INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, normalized_spelling, word_uid, created_at, updated_at) VALUES (1, 1, 'apple', '', '[]', '', 'apple', 'uid1', $now, $now)"
        )

        // Insert study state
        db.execSQL(
            "INSERT INTO word_study_state (word_id, remaining_reviews, ease_level, next_review_at, last_reviewed_at) VALUES (1, 5, 0, 0, 0)"
        )

        // Verify study state exists
        val beforeCursor = db.query("SELECT COUNT(*) FROM word_study_state WHERE word_id = 1")
        beforeCursor.use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }

        // Delete word
        db.execSQL("DELETE FROM words WHERE id = 1")

        // Verify study state is cascade deleted
        val afterCursor = db.query("SELECT COUNT(*) FROM word_study_state WHERE word_id = 1")
        afterCursor.use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }

        db.close()
    }

    @Test
    fun upsertStudyStateOverwritesOldValue() {
        val db = createV3Database()

        val now = System.currentTimeMillis()

        db.execSQL(
            "INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at) VALUES (1, 'Test', '', 0, 1, $now, $now)"
        )
        db.execSQL(
            "INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, normalized_spelling, word_uid, created_at, updated_at) VALUES (1, 1, 'apple', '', '[]', '', 'apple', 'uid1', $now, $now)"
        )

        // Insert initial study state
        db.execSQL(
            "INSERT INTO word_study_state (word_id, remaining_reviews, ease_level, next_review_at, last_reviewed_at) VALUES (1, 5, 0, 0, 0)"
        )

        // Upsert with new values (REPLACE)
        db.execSQL(
            "INSERT OR REPLACE INTO word_study_state (word_id, remaining_reviews, ease_level, next_review_at, last_reviewed_at) VALUES (1, 3, 2, 1000, 500)"
        )

        // Verify only one record exists with updated values
        val cursor = db.query("SELECT remaining_reviews, ease_level, next_review_at, last_reviewed_at FROM word_study_state WHERE word_id = 1")
        cursor.use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertEquals(2, it.getInt(1))
            assertEquals(1000L, it.getLong(2))
            assertEquals(500L, it.getLong(3))
        }

        db.close()
    }
}
