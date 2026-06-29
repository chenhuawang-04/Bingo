package com.xty.englishhelper.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration32To33SqlTest {

    @Test
    fun `migration adds phrase practice count with zero default`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqls = mutableListOf<String>()
        every { db.execSQL(capture(sqls)) } just Runs

        AppDatabase.MIGRATION_32_33.migrate(db)

        assertTrue(sqls.any { sql ->
            sql.contains("ALTER TABLE word_phrases ADD COLUMN practice_count INTEGER NOT NULL DEFAULT 0")
        })
    }
}
