package com.xty.englishhelper.data.local

import android.database.Cursor
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.StudyDao
import com.xty.englishhelper.data.local.dao.UnitDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.ArticleEntity
import com.xty.englishhelper.data.local.entity.ArticleImageEntity
import com.xty.englishhelper.data.local.entity.ArticleSentenceEntity
import com.xty.englishhelper.data.local.entity.ArticleWordLinkEntity
import com.xty.englishhelper.data.local.entity.ArticleWordStatEntity
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.DictionaryEntity
import com.xty.englishhelper.data.local.entity.SentenceAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.UnitEntity
import com.xty.englishhelper.data.local.entity.UnitWordCrossRef
import com.xty.englishhelper.data.local.entity.WordAssociationEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.entity.WordExampleEntity
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
        WordAssociationEntity::class,
        ArticleEntity::class,
        ArticleImageEntity::class,
        ArticleSentenceEntity::class,
        ArticleWordStatEntity::class,
        ArticleWordLinkEntity::class,
        SentenceAnalysisCacheEntity::class,
        WordExampleEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun wordDao(): WordDao
    abstract fun unitDao(): UnitDao
    abstract fun studyDao(): StudyDao
    abstract fun articleDao(): ArticleDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new FSRS-based study state table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `word_study_state_new` (
                        `word_id` INTEGER NOT NULL PRIMARY KEY,
                        `state` INTEGER NOT NULL DEFAULT 2,
                        `step` INTEGER,
                        `stability` REAL NOT NULL DEFAULT 0.0,
                        `difficulty` REAL NOT NULL DEFAULT 0.0,
                        `due` INTEGER NOT NULL DEFAULT 0,
                        `last_review_at` INTEGER NOT NULL DEFAULT 0,
                        `reps` INTEGER NOT NULL DEFAULT 0,
                        `lapses` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Migrate old data: map easeLevel to approximate stability
                db.execSQL("""
                    INSERT INTO word_study_state_new (word_id, state, stability, difficulty, due, last_review_at, reps)
                    SELECT word_id, 2,
                        CASE ease_level
                            WHEN 0 THEN 0.4 WHEN 1 THEN 0.4 WHEN 2 THEN 0.5
                            WHEN 3 THEN 1.2 WHEN 4 THEN 3.2 WHEN 5 THEN 4.0
                            WHEN 6 THEN 7.0 WHEN 7 THEN 15.0 WHEN 8 THEN 30.0
                            ELSE 60.0
                        END,
                        5.0,
                        next_review_at,
                        last_reviewed_at,
                        ease_level
                    FROM word_study_state
                """.trimIndent())

                db.execSQL("DROP TABLE word_study_state")
                db.execSQL("ALTER TABLE word_study_state_new RENAME TO word_study_state")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add inflections_json to words table
                db.execSQL("ALTER TABLE words ADD COLUMN inflections_json TEXT NOT NULL DEFAULT '[]'")

                // Create articles table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `articles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `domain` TEXT NOT NULL DEFAULT '',
                        `difficulty_ai` REAL NOT NULL DEFAULT 0,
                        `difficulty_local` REAL NOT NULL DEFAULT 0,
                        `difficulty_final` REAL NOT NULL DEFAULT 0,
                        `source_type` INTEGER NOT NULL DEFAULT 1,
                        `parse_status` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_updated_at` ON `articles` (`updated_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_title` ON `articles` (`title`)")

                // Create article_images table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `article_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `local_uri` TEXT NOT NULL,
                        `order_index` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`article_id`) REFERENCES `articles`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_images_article_id_order_index` ON `article_images` (`article_id`, `order_index`)")

                // Create article_sentences table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `article_sentences` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `sentence_index` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `char_start` INTEGER NOT NULL,
                        `char_end` INTEGER NOT NULL,
                        FOREIGN KEY(`article_id`) REFERENCES `articles`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_sentences_article_id` ON `article_sentences` (`article_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_sentences_article_id_sentence_index` ON `article_sentences` (`article_id`, `sentence_index`)")

                // Create article_word_stats table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `article_word_stats` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `normalized_token` TEXT NOT NULL,
                        `display_token` TEXT NOT NULL,
                        `frequency` INTEGER NOT NULL,
                        FOREIGN KEY(`article_id`) REFERENCES `articles`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_word_stats_article_id_normalized_token` ON `article_word_stats` (`article_id`, `normalized_token`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_word_stats_article_id_frequency` ON `article_word_stats` (`article_id`, `frequency`)")

                // Create article_word_links table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `article_word_links` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `sentence_id` INTEGER NOT NULL,
                        `word_id` INTEGER NOT NULL,
                        `dictionary_id` INTEGER NOT NULL,
                        `matched_token` TEXT NOT NULL,
                        FOREIGN KEY(`article_id`) REFERENCES `articles`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`sentence_id`) REFERENCES `article_sentences`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_word_links_article_id` ON `article_word_links` (`article_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_word_links_word_id` ON `article_word_links` (`word_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_word_links_dictionary_id` ON `article_word_links` (`dictionary_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_word_links_sentence_id` ON `article_word_links` (`sentence_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_word_links_article_id_sentence_id_word_id` ON `article_word_links` (`article_id`, `sentence_id`, `word_id`)")

                // Create sentence_analysis_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sentence_analysis_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `sentence_id` INTEGER NOT NULL,
                        `sentence_hash` TEXT NOT NULL,
                        `meaning_zh` TEXT NOT NULL,
                        `grammar_json` TEXT NOT NULL,
                        `keywords_json` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sentence_analysis_cache_article_id_sentence_id_sentence_hash` ON `sentence_analysis_cache` (`article_id`, `sentence_id`, `sentence_hash`)")

                // Create word_examples table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `word_examples` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `word_id` INTEGER NOT NULL,
                        `sentence` TEXT NOT NULL,
                        `source_type` INTEGER NOT NULL DEFAULT 0,
                        `source_article_id` INTEGER,
                        `source_sentence_id` INTEGER,
                        `source_label` TEXT,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_examples_word_id` ON `word_examples` (`word_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_examples_word_id_source_type_source_article_id_source_sentence_id` ON `word_examples` (`word_id`, `source_type`, `source_article_id`, `source_sentence_id`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_word_stats_normalized_token` ON `article_word_stats`(`normalized_token`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sentence_analysis_cache ADD COLUMN model_key TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS index_sentence_analysis_cache_article_id_sentence_id_sentence_hash")
                db.execSQL("CREATE UNIQUE INDEX `index_sentence_analysis_cache_article_id_sentence_id_sentence_hash_model_key` ON `sentence_analysis_cache`(`article_id`, `sentence_id`, `sentence_hash`, `model_key`)")
                db.execSQL("DELETE FROM sentence_analysis_cache")
            }
        }
    }
}
