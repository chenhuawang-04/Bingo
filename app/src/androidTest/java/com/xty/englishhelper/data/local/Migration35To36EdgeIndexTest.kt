package com.xty.englishhelper.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration35To36EdgeIndexTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationPreservesEdgesAndAddsQueryIndexes() {
        val name = "migration-35-36-edge-index"
        val db = helper.createDatabase(name, 35)
        db.execSQL(
            """
            INSERT INTO dictionaries(
                id, dictionary_uid, name, description, color, word_count, created_at, updated_at
            ) VALUES (1, 'dict-1', 'Test', '', 0, 2, 1, 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO word_edges(
                id, word_id_a, word_id_b, edge_type, dictionary_id, created_at, updated_at,
                status, learning_value, relation_strength, confidence
            ) VALUES (1, 1, 2, 'form_spelling', 1, 1, 1, 'core', 3, 3, 0.8)
            """.trimIndent()
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(name, 36, true, AppDatabase.MIGRATION_35_36)
        migrated.query("SELECT COUNT(*) FROM word_edges WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("PRAGMA index_list('word_edges')").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue("index_word_edges_dictionary_id_id" in names)
            assertTrue("index_word_edges_dictionary_id_confidence" in names)
        }
        migrated.close()
    }
}
