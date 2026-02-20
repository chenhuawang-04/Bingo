package com.xty.englishhelper.data.local

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
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.entity.WordStudyStateEntity

@Database(
    entities = [
        DictionaryEntity::class,
        WordEntity::class,
        SynonymEntity::class,
        SimilarWordEntity::class,
        CognateEntity::class,
        UnitEntity::class,
        UnitWordCrossRef::class,
        WordStudyStateEntity::class
    ],
    version = 2,
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
    }
}
