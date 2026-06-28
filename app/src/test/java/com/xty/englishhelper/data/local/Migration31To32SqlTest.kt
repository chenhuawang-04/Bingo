package com.xty.englishhelper.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration31To32SqlTest {

    @Test
    fun `migration creates phrase tables without schema-divergent defaults`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqls = mutableListOf<String>()
        every { db.execSQL(capture(sqls)) } just Runs

        AppDatabase.MIGRATION_31_32.migrate(db)

        val createTableSql = sqls.filter { it.contains("CREATE TABLE IF NOT EXISTS") }
        val phraseTableSql = createTableSql.filter {
            it.contains("`word_phrase_tags`") ||
                it.contains("`word_phrases`") ||
                it.contains("`word_phrase_tag_cross_refs`") ||
                it.contains("`word_phrase_organize_marks`")
        }

        assertTrue(phraseTableSql.any { it.contains("`word_phrase_tags`") })
        assertTrue(phraseTableSql.any { it.contains("`word_phrases`") })
        assertTrue(phraseTableSql.any { it.contains("`word_phrase_tag_cross_refs`") })
        assertTrue(phraseTableSql.any { it.contains("`word_phrase_organize_marks`") })
        phraseTableSql.forEach { sql ->
            assertFalse(
                "Room schema 32 has no defaultValue metadata for new phrase tables: $sql",
                sql.contains(" DEFAULT ", ignoreCase = true)
            )
        }
    }

    @Test
    fun `migration creates dictionary scoped phrase and tag uniqueness`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val sqls = mutableListOf<String>()
        every { db.execSQL(capture(sqls)) } just Runs

        AppDatabase.MIGRATION_31_32.migrate(db)

        assertTrue(sqls.any {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_phrase_tags_dictionary_id_tag_uid`") &&
                it.contains("`dictionary_id`, `tag_uid`")
        })
        assertTrue(sqls.any {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_phrase_tags_dictionary_id_normalized_name`") &&
                it.contains("`dictionary_id`, `normalized_name`")
        })
        assertTrue(sqls.any {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_phrases_dictionary_id_phrase_uid`") &&
                it.contains("`dictionary_id`, `phrase_uid`")
        })
        assertTrue(sqls.any {
            it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_phrases_word_id_normalized_phrase`") &&
                it.contains("`word_id`, `normalized_phrase`")
        })
    }
}
