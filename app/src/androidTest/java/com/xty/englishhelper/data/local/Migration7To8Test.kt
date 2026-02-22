package com.xty.englishhelper.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val testDbName = "migration-7-8-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate7To8_addsModelKeyColumn() {
        // Create v7 database and insert a cache entry
        val db = helper.createDatabase(testDbName, 7)

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("article_id", 1L)
            put("sentence_id", 10L)
            put("sentence_hash", "abc123")
            put("meaning_zh", "测试含义")
            put("grammar_json", "[]")
            put("keywords_json", "[]")
            put("created_at", now)
        }
        db.insert("sentence_analysis_cache", SQLiteDatabase.CONFLICT_REPLACE, values)

        // Verify row exists before migration
        val preCursor = db.query("SELECT COUNT(*) FROM sentence_analysis_cache")
        preCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        db.close()

        // Run migration
        val migratedDb = helper.runMigrationsAndValidate(
            testDbName, 8, true, AppDatabase.MIGRATION_7_8
        )

        // Migration clears the cache, so expect 0 rows
        val cursor = migratedDb.query("SELECT COUNT(*) FROM sentence_analysis_cache")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }

        migratedDb.close()
    }

    @Test
    fun migrate7To8_modelKeyColumnHasCorrectDefault() {
        val db = helper.createDatabase(testDbName, 7)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName, 8, true, AppDatabase.MIGRATION_7_8
        )

        // Insert a row without specifying model_key to verify the default value
        migratedDb.execSQL(
            """INSERT INTO sentence_analysis_cache
               (article_id, sentence_id, sentence_hash, meaning_zh, grammar_json, keywords_json, created_at)
               VALUES (1, 1, 'hash1', '含义', '[]', '[]', 0)"""
        )

        val cursor = migratedDb.query("SELECT model_key FROM sentence_analysis_cache")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("", it.getString(it.getColumnIndex("model_key")))
        }

        migratedDb.close()
    }

    @Test
    fun migrate7To8_newUniqueIndexIncludesModelKey() {
        val db = helper.createDatabase(testDbName, 7)
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName, 8, true, AppDatabase.MIGRATION_7_8
        )

        // Insert two entries with same (article_id, sentence_id, sentence_hash) but different model_key
        migratedDb.execSQL(
            """INSERT INTO sentence_analysis_cache
               (article_id, sentence_id, sentence_hash, model_key, meaning_zh, grammar_json, keywords_json, created_at)
               VALUES (1, 1, 'hash1', 'gpt-4|v1', '含义A', '[]', '[]', 0)"""
        )
        migratedDb.execSQL(
            """INSERT INTO sentence_analysis_cache
               (article_id, sentence_id, sentence_hash, model_key, meaning_zh, grammar_json, keywords_json, created_at)
               VALUES (1, 1, 'hash1', 'claude|v1', '含义B', '[]', '[]', 0)"""
        )

        val cursor = migratedDb.query("SELECT COUNT(*) FROM sentence_analysis_cache")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }

        // Same model_key should violate unique constraint
        try {
            migratedDb.execSQL(
                """INSERT INTO sentence_analysis_cache
                   (article_id, sentence_id, sentence_hash, model_key, meaning_zh, grammar_json, keywords_json, created_at)
                   VALUES (1, 1, 'hash1', 'gpt-4|v1', '含义C', '[]', '[]', 0)"""
            )
            fail("Expected unique constraint violation")
        } catch (_: Exception) {
            // Expected
        }

        migratedDb.close()
    }
}
