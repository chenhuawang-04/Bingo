package com.xty.englishhelper.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2To3_populatesNormalizedSpellingAndWordUid() {
        // Create v2 database
        val db = helper.createDatabase(testDbName, 2)

        // Insert a dictionary
        db.execSQL(
            "INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at) VALUES (1, 'Test', '', 0, 0, 0, 0)"
        )

        // Insert words
        val values1 = ContentValues().apply {
            put("id", 1L)
            put("dictionary_id", 1L)
            put("spelling", "  Hello  ")
            put("phonetic", "")
            put("meanings_json", "[]")
            put("root_explanation", "")
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        db.insert("words", SQLiteDatabase.CONFLICT_REPLACE, values1)

        val values2 = ContentValues().apply {
            put("id", 2L)
            put("dictionary_id", 1L)
            put("spelling", "World")
            put("phonetic", "")
            put("meanings_json", "[]")
            put("root_explanation", "")
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        db.insert("words", SQLiteDatabase.CONFLICT_REPLACE, values2)

        db.close()

        // Run migration
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.MIGRATION_2_3)

        // Verify normalized_spelling is correctly computed
        val cursor = migratedDb.query("SELECT id, spelling, normalized_spelling, word_uid FROM words ORDER BY id")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("hello", it.getString(it.getColumnIndex("normalized_spelling")))
            assertNotEquals("", it.getString(it.getColumnIndex("word_uid")))

            assertTrue(it.moveToNext())
            assertEquals("world", it.getString(it.getColumnIndex("normalized_spelling")))
            assertNotEquals("", it.getString(it.getColumnIndex("word_uid")))
        }

        migratedDb.close()
    }

    @Test
    fun migrate2To3_removesDuplicateSpellings() {
        val db = helper.createDatabase(testDbName, 2)

        db.execSQL(
            "INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at) VALUES (1, 'Test', '', 0, 0, 0, 0)"
        )

        // Insert duplicate words (same spelling, different case)
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, created_at, updated_at) VALUES (1, 1, 'Hello', '', '[]', '', $now, $now)"
        )
        db.execSQL(
            "INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, created_at, updated_at) VALUES (2, 1, 'hello', '', '[]', '', $now, $now)"
        )
        db.execSQL(
            "INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, created_at, updated_at) VALUES (3, 1, 'HELLO', '', '[]', '', $now, $now)"
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.MIGRATION_2_3)

        // Should only have 1 word (the one with smallest id)
        val cursor = migratedDb.query("SELECT id FROM words")
        cursor.use {
            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(it.getColumnIndex("id")))
        }

        migratedDb.close()
    }

    @Test
    fun migrate2To3_uniqueIndexPreventsInsertionOfDuplicate() {
        val db = helper.createDatabase(testDbName, 2)

        db.execSQL(
            "INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at) VALUES (1, 'Test', '', 0, 0, 0, 0)"
        )

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, created_at, updated_at) VALUES (1, 1, 'Apple', '', '[]', '', $now, $now)"
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.MIGRATION_2_3)

        // Try inserting a duplicate normalized_spelling - should fail
        try {
            migratedDb.execSQL(
                "INSERT INTO words (dictionary_id, spelling, phonetic, meanings_json, root_explanation, normalized_spelling, word_uid, created_at, updated_at) VALUES (1, 'apple', '', '[]', '', 'apple', 'uid2', $now, $now)"
            )
            fail("Expected unique constraint violation")
        } catch (e: Exception) {
            // Expected
        }

        migratedDb.close()
    }
}
