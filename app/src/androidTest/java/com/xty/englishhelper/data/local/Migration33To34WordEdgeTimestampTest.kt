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
class Migration33To34WordEdgeTimestampTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationCopiesCreatedAtIntoUpdatedAt() {
        val name = "migration-33-34-word-edge-timestamp"
        val db = helper.createDatabase(name, 33)
        db.execSQL(
            """
            INSERT INTO word_edges (
                id, word_id_a, word_id_b, edge_type, dictionary_id, created_at,
                status, learning_value, relation_strength, confidence
            ) VALUES (1, 10, 20, 'SEMANTIC_SYNONYM', 1, 123456, 'core', 3, 4, 0.8)
            """.trimIndent()
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            name,
            34,
            true,
            AppDatabase.MIGRATION_33_34
        )
        migrated.query("SELECT created_at, updated_at FROM word_edges WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(123456L, cursor.getLong(0))
            assertEquals(123456L, cursor.getLong(1))
        }
        migrated.close()
    }
}
