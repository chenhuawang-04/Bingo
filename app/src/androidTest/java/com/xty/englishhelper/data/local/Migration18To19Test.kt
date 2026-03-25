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
class Migration18To19Test {

    private val testDbName = "migration-18-19-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate18To19_addsUpdatedAtToWordPools() {
        val db = helper.createDatabase(testDbName, 18)

        db.execSQL(
            """
            INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at)
            VALUES (1, 'CET4', '', 0, 0, 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO words (id, dictionary_id, spelling, phonetic, meanings_json, root_explanation, normalized_spelling, word_uid, decomposition_json, inflections_json, created_at, updated_at)
            VALUES (10, 1, 'apple', '', '[]', '', 'apple', 'uid-apple', '[]', '[]', 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO word_pools (id, dictionary_id, focus_word_id, strategy, algorithm_version)
            VALUES (100, 1, 10, 'BALANCED', 'BALANCED_v1')
            """.trimIndent()
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            19,
            true,
            AppDatabase.MIGRATION_18_19
        )

        val cursor: Cursor = migratedDb.query(
            "SELECT updated_at FROM word_pools WHERE id = 100"
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(0L, it.getLong(it.getColumnIndexOrThrow("updated_at")))
        }

        migratedDb.execSQL(
            """
            INSERT INTO word_pools (id, dictionary_id, focus_word_id, strategy, algorithm_version, updated_at)
            VALUES (101, 1, 10, 'QUALITY_FIRST', 'QUALITY_FIRST_v1', 123456789)
            """.trimIndent()
        )

        val insertedCursor = migratedDb.query(
            "SELECT updated_at FROM word_pools WHERE id = 101"
        )
        insertedCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(123456789L, it.getLong(it.getColumnIndexOrThrow("updated_at")))
        }

        migratedDb.close()
    }
}
