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
class Migration30To31SyncIdentityTest {

    private val testDbName = "migration-30-31-sync-identity-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate30To31_backfillsDictionaryAndUnitIdentity() {
        val db = helper.createDatabase(testDbName, 30)

        db.execSQL(
            """
            INSERT INTO dictionaries (id, name, description, color, word_count, created_at, updated_at)
            VALUES (1, 'CET4', '', 0, 0, 100, 200)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO units (id, dictionary_id, name, default_repeat_count, created_at)
            VALUES (10, 1, 'Unit 1', 5, 12345)
            """.trimIndent()
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            31,
            true,
            AppDatabase.MIGRATION_30_31
        )

        val dictionaryCursor: Cursor = migratedDb.query(
            "SELECT dictionary_uid FROM dictionaries WHERE id = 1"
        )
        dictionaryCursor.use {
            assertTrue(it.moveToFirst())
            val dictionaryUid = it.getString(it.getColumnIndexOrThrow("dictionary_uid"))
            assertTrue(dictionaryUid.isNotBlank())
        }

        val unitCursor: Cursor = migratedDb.query(
            "SELECT unit_uid, updated_at FROM units WHERE id = 10"
        )
        unitCursor.use {
            assertTrue(it.moveToFirst())
            val unitUid = it.getString(it.getColumnIndexOrThrow("unit_uid"))
            val updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
            assertTrue(unitUid.isNotBlank())
            assertEquals(12345L, updatedAt)
        }

        migratedDb.close()
    }
}
