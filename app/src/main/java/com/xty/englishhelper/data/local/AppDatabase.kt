package com.xty.englishhelper.data.local

import android.database.Cursor
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.StudyDao
import com.xty.englishhelper.data.local.dao.UnitDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.DictionaryEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.UnitEntity
import com.xty.englishhelper.data.local.entity.UnitWordCrossRef
import com.xty.englishhelper.data.local.entity.WordAssociationEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.entity.WordStudyStateEntity
import java.util.UUID

@Database(
    entities = [
        DictionaryEntity::class,
        WordEntity::class,
        SynonymEntity::class,
        SimilarWordEntity::class,
        CognateEntity::class,
        UnitEntity::class,
        UnitWordCrossRef::class,
        WordStudyStateEntity::class,
        WordAssociationEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun wordDao(): WordDao
    abstract fun unitDao(): UnitDao
    abstract fun studyDao(): StudyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `units` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dictionary_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `default_repeat_count` INTEGER NOT NULL DEFAULT 2,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`dictionary_id`) REFERENCES `dictionaries`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_dictionary_id` ON `units` (`dictionary_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `unit_word_cross_ref` (
                        `unit_id` INTEGER NOT NULL,
                        `word_id` INTEGER NOT NULL,
                        PRIMARY KEY(`unit_id`, `word_id`),
                        FOREIGN KEY(`unit_id`) REFERENCES `units`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_word_cross_ref_unit_id` ON `unit_word_cross_ref` (`unit_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_word_cross_ref_word_id` ON `unit_word_cross_ref` (`word_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `word_study_state` (
                        `word_id` INTEGER NOT NULL,
                        `remaining_reviews` INTEGER NOT NULL,
                        `ease_level` INTEGER NOT NULL DEFAULT 0,
                        `next_review_at` INTEGER NOT NULL DEFAULT 0,
                        `last_reviewed_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`word_id`),
                        FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Add new columns
                db.execSQL("ALTER TABLE words ADD COLUMN normalized_spelling TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE words ADD COLUMN word_uid TEXT NOT NULL DEFAULT ''")

                // Step 2: Populate normalized_spelling and word_uid for existing rows
                val cursor: Cursor = db.query("SELECT id, spelling FROM words")
                cursor.use {
                    val idIndex = it.getColumnIndex("id")
                    val spellingIndex = it.getColumnIndex("spelling")
                    while (it.moveToNext()) {
                        val id = it.getLong(idIndex)
                        val spelling = it.getString(spellingIndex)
                        val normalized = spelling.trim().lowercase()
                        val uid = UUID.randomUUID().toString()
                        db.execSQL(
                            "UPDATE words SET normalized_spelling = ?, word_uid = ? WHERE id = ?",
                            arrayOf<Any>(normalized, uid, id)
                        )
                    }
                }

                // Step 3: Remove duplicates - keep the row with smallest id per (dictionary_id, normalized_spelling)
                db.execSQL(
                    """
                    DELETE FROM words WHERE id NOT IN (
                        SELECT MIN(id) FROM words GROUP BY dictionary_id, normalized_spelling
                    )
                    """.trimIndent()
                )

                // Step 4: Create unique index
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_words_dictionary_id_normalized_spelling` ON `words` (`dictionary_id`, `normalized_spelling`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN decomposition_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `word_associations` (
                        `word_id` INTEGER NOT NULL,
                        `associated_word_id` INTEGER NOT NULL,
                        `similarity` REAL NOT NULL,
                        `common_segments_json` TEXT NOT NULL DEFAULT '[]',
                        PRIMARY KEY(`word_id`, `associated_word_id`),
                        FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`associated_word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_associations_word_id` ON `word_associations` (`word_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_associations_associated_word_id` ON `word_associations` (`associated_word_id`)")
            }
        }
    }
}
