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
class Migration34To35PoolStagingTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationCreatesStagingAndBackfillsStrategyState() {
        val name = "migration-34-35-pool-staging"
        val db = helper.createDatabase(name, 34)
        db.execSQL(
            """
            INSERT INTO dictionaries(
                id, dictionary_uid, name, description, color, word_count, created_at, updated_at
            ) VALUES (1, 'dict-1', 'Test', '', 0, 0, 1, 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO word_pools(
                id, dictionary_id, focus_word_id, strategy, algorithm_version, updated_at, quality_score
            ) VALUES (1, 1, NULL, 'QUALITY_FIRST', 'QF_v4', 123, NULL)
            """.trimIndent()
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(name, 35, true, AppDatabase.MIGRATION_34_35)
        migrated.query(
            "SELECT updated_at FROM word_pool_strategy_states WHERE dictionary_id = 1 AND strategy = 'QUALITY_FIRST'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(123L, cursor.getLong(0))
        }
        migrated.query("SELECT COUNT(*) FROM word_edge_staging").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }
}
