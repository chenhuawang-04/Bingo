package com.xty.englishhelper.data.local

import android.database.Cursor
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.ArticleCategoryDao
import com.xty.englishhelper.data.local.dao.BackgroundTaskDao
import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.dao.StudyDao
import com.xty.englishhelper.data.local.dao.UnitDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.ArticleEntity
import com.xty.englishhelper.data.local.entity.ArticleCategoryEntity
import com.xty.englishhelper.data.local.entity.ArticleImageEntity
import com.xty.englishhelper.data.local.entity.ArticleParagraphEntity
import com.xty.englishhelper.data.local.entity.ArticleSentenceEntity
import com.xty.englishhelper.data.local.entity.ArticleWordLinkEntity
import com.xty.englishhelper.data.local.entity.ArticleWordStatEntity
import com.xty.englishhelper.data.local.entity.BackgroundTaskEntity
import com.xty.englishhelper.data.local.entity.ParagraphAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.DictionaryEntity
import com.xty.englishhelper.data.local.entity.ExamPaperEntity
import com.xty.englishhelper.data.local.entity.PracticeRecordEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupParagraphEntity
import com.xty.englishhelper.data.local.entity.QuestionItemEntity
import com.xty.englishhelper.data.local.entity.QuestionSourceArticleEntity
import com.xty.englishhelper.data.local.entity.SentenceAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.UnitEntity
import com.xty.englishhelper.data.local.entity.UnitWordCrossRef
import com.xty.englishhelper.data.local.entity.WordAssociationEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.entity.WordExampleEntity
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
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
        WordExampleEntity::class,
        WordPoolEntity::class,
        WordPoolMemberEntity::class,
        ArticleParagraphEntity::class,
        ParagraphAnalysisCacheEntity::class,
        ExamPaperEntity::class,
        QuestionGroupEntity::class,
        QuestionGroupParagraphEntity::class,
        QuestionItemEntity::class,
        PracticeRecordEntity::class,
        QuestionSourceArticleEntity::class,
        BackgroundTaskEntity::class,
        ArticleCategoryEntity::class
    ],
    version = 16,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun wordDao(): WordDao
    abstract fun unitDao(): UnitDao
    abstract fun studyDao(): StudyDao
    abstract fun articleDao(): ArticleDao
    abstract fun wordPoolDao(): WordPoolDao
    abstract fun questionBankDao(): QuestionBankDao
    abstract fun backgroundTaskDao(): BackgroundTaskDao
    abstract fun articleCategoryDao(): ArticleCategoryDao

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `word_pools` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dictionary_id` INTEGER NOT NULL,
                    `focus_word_id` INTEGER,
                    `strategy` TEXT NOT NULL,
                    `algorithm_version` TEXT NOT NULL,
                    FOREIGN KEY(`dictionary_id`) REFERENCES `dictionaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`focus_word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_pools_dictionary_id` ON `word_pools`(`dictionary_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_pools_focus_word_id` ON `word_pools`(`focus_word_id`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `word_pool_members` (
                    `word_id` INTEGER NOT NULL,
                    `pool_id` INTEGER NOT NULL,
                    PRIMARY KEY(`word_id`, `pool_id`),
                    FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`pool_id`) REFERENCES `word_pools`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_pool_members_pool_id` ON `word_pool_members`(`pool_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_pool_members_word_id` ON `word_pool_members`(`word_id`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasColumn = db.query("PRAGMA table_info(`articles`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "article_uid") {
                            found = true
                            break
                        }
                    }
                    found
                }
                if (!hasColumn) {
                    db.execSQL("ALTER TABLE articles ADD COLUMN article_uid TEXT NOT NULL DEFAULT ''")
                }

                val seen = mutableSetOf<String>()
                db.query("SELECT id, article_uid FROM articles").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val uidIndex = cursor.getColumnIndex("article_uid")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val uid = cursor.getString(uidIndex) ?: ""
                        val normalized = uid.trim()
                        if (normalized.isBlank() || !seen.add(normalized)) {
                            val newUid = UUID.randomUUID().toString()
                            db.execSQL(
                                "UPDATE articles SET article_uid = ? WHERE id = ?",
                                arrayOf<Any>(newUid, id)
                            )
                            seen.add(newUid)
                        }
                    }
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_article_uid` ON `articles`(`article_uid`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. ALTER articles table — add new columns
                db.execSQL("ALTER TABLE articles ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN author TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN source TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN cover_image_uri TEXT")
                db.execSQL("ALTER TABLE articles ADD COLUMN cover_image_url TEXT")
                db.execSQL("ALTER TABLE articles ADD COLUMN word_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN is_saved INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE articles ADD COLUMN source_type_v2 TEXT NOT NULL DEFAULT 'LOCAL'")

                // 2. ALTER article_sentences — add paragraph_id
                db.execSQL("ALTER TABLE article_sentences ADD COLUMN paragraph_id INTEGER NOT NULL DEFAULT 0")

                // 3. CREATE article_paragraphs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `article_paragraphs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `paragraph_index` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `image_uri` TEXT,
                        `image_url` TEXT,
                        `paragraph_type` TEXT NOT NULL DEFAULT 'TEXT',
                        FOREIGN KEY(`article_id`) REFERENCES `articles`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_paragraphs_article_id` ON `article_paragraphs`(`article_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_paragraphs_article_id_paragraph_index` ON `article_paragraphs`(`article_id`, `paragraph_index`)")

                // 4. CREATE paragraph_analysis_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `paragraph_analysis_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `article_id` INTEGER NOT NULL,
                        `paragraph_id` INTEGER NOT NULL,
                        `paragraph_hash` TEXT NOT NULL,
                        `model_key` TEXT NOT NULL,
                        `meaning_zh` TEXT NOT NULL,
                        `grammar_json` TEXT NOT NULL,
                        `keywords_json` TEXT NOT NULL,
                        `breakdowns_json` TEXT NOT NULL DEFAULT '[]',
                        `created_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_paragraph_analysis_cache_article_id_paragraph_id_paragraph_hash_model_key` ON `paragraph_analysis_cache`(`article_id`, `paragraph_id`, `paragraph_hash`, `model_key`)")

                // 5. Migrate existing articles: split content into paragraphs
                val articleCursor = db.query("SELECT id, content FROM articles")
                articleCursor.use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val contentIdx = cursor.getColumnIndex("content")
                    while (cursor.moveToNext()) {
                        val articleId = cursor.getLong(idIdx)
                        val content = cursor.getString(contentIdx) ?: ""
                        if (content.isBlank()) continue

                        // Smart paragraph splitting
                        val paragraphs = smartSplitParagraphs(content)
                        var charOffset = 0

                        paragraphs.forEachIndexed { paraIndex, paraText ->
                            db.execSQL(
                                "INSERT INTO article_paragraphs (article_id, paragraph_index, text, paragraph_type) VALUES (?, ?, ?, 'TEXT')",
                                arrayOf<Any>(articleId, paraIndex, paraText)
                            )

                            // Get the inserted paragraph id
                            val paraCursor = db.query(
                                "SELECT id FROM article_paragraphs WHERE article_id = ? AND paragraph_index = ?",
                                arrayOf<Any>(articleId.toString(), paraIndex.toString())
                            )
                            val paragraphId = paraCursor.use { pc ->
                                if (pc.moveToFirst()) pc.getLong(0) else 0L
                            }

                            // Match existing sentences to this paragraph by char range
                            val paraStart = charOffset
                            val paraEnd = charOffset + paraText.length
                            db.execSQL(
                                "UPDATE article_sentences SET paragraph_id = ? WHERE article_id = ? AND char_start >= ? AND char_end <= ?",
                                arrayOf<Any>(paragraphId, articleId, paraStart, paraEnd)
                            )
                            // Account for paragraph separator
                            charOffset = paraEnd + 1
                        }

                        // Backfill word_count
                        val wordCount = content.split(Regex("\\s+")).count { it.isNotBlank() }
                        db.execSQL(
                            "UPDATE articles SET word_count = ? WHERE id = ?",
                            arrayOf<Any>(wordCount, articleId)
                        )
                    }
                }
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // exam_papers
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exam_papers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `total_questions` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exam_papers_uid` ON `exam_papers`(`uid`)")

                // question_groups
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `question_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `exam_paper_id` INTEGER NOT NULL,
                        `question_type` TEXT NOT NULL,
                        `section_label` TEXT,
                        `order_in_paper` INTEGER NOT NULL DEFAULT 0,
                        `directions` TEXT,
                        `passage_text` TEXT NOT NULL DEFAULT '',
                        `source_info` TEXT,
                        `source_url` TEXT,
                        `source_author` TEXT,
                        `source_verified` INTEGER NOT NULL DEFAULT 0,
                        `source_verify_error` TEXT,
                        `word_count` INTEGER NOT NULL DEFAULT 0,
                        `difficulty_level` TEXT,
                        `difficulty_score` REAL,
                        `has_ai_answer` INTEGER NOT NULL DEFAULT 0,
                        `has_scanned_answer` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`exam_paper_id`) REFERENCES `exam_papers`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_question_groups_uid` ON `question_groups`(`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_groups_exam_paper_id` ON `question_groups`(`exam_paper_id`)")

                // question_group_paragraphs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `question_group_paragraphs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `question_group_id` INTEGER NOT NULL,
                        `paragraph_index` INTEGER NOT NULL,
                        `text` TEXT NOT NULL DEFAULT '',
                        `paragraph_type` TEXT NOT NULL DEFAULT 'TEXT',
                        `image_uri` TEXT,
                        `image_url` TEXT,
                        FOREIGN KEY(`question_group_id`) REFERENCES `question_groups`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_group_paragraphs_question_group_id` ON `question_group_paragraphs`(`question_group_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_question_group_paragraphs_question_group_id_paragraph_index` ON `question_group_paragraphs`(`question_group_id`, `paragraph_index`)")

                // question_items
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `question_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `question_group_id` INTEGER NOT NULL,
                        `question_number` INTEGER NOT NULL,
                        `question_text` TEXT NOT NULL,
                        `option_a` TEXT,
                        `option_b` TEXT,
                        `option_c` TEXT,
                        `option_d` TEXT,
                        `correct_answer` TEXT,
                        `answer_source` TEXT NOT NULL DEFAULT 'NONE',
                        `explanation` TEXT,
                        `order_in_group` INTEGER NOT NULL DEFAULT 0,
                        `word_count` INTEGER NOT NULL DEFAULT 0,
                        `difficulty_level` TEXT,
                        `difficulty_score` REAL,
                        `wrong_count` INTEGER NOT NULL DEFAULT 0,
                        `extra_data` TEXT,
                        `sample_source_title` TEXT,
                        `sample_source_url` TEXT,
                        `sample_source_info` TEXT,
                        FOREIGN KEY(`question_group_id`) REFERENCES `question_groups`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_items_question_group_id` ON `question_items`(`question_group_id`)")

                // practice_records
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `practice_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `question_item_id` INTEGER NOT NULL,
                        `user_answer` TEXT NOT NULL,
                        `is_correct` INTEGER NOT NULL,
                        `practiced_at` INTEGER NOT NULL,
                        FOREIGN KEY(`question_item_id`) REFERENCES `question_items`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_practice_records_question_item_id_practiced_at` ON `practice_records`(`question_item_id`, `practiced_at`)")

                // question_source_articles
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `question_source_articles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `question_group_id` INTEGER NOT NULL,
                        `linked_article_id` INTEGER NOT NULL,
                        `linked_article_uid` TEXT NOT NULL DEFAULT '',
                        `verified_at` INTEGER NOT NULL,
                        FOREIGN KEY(`question_group_id`) REFERENCES `question_groups`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_question_source_articles_question_group_id` ON `question_source_articles`(`question_group_id`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE question_source_articles ADD COLUMN linked_article_uid TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `background_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        `progress_current` INTEGER NOT NULL DEFAULT 0,
                        `progress_total` INTEGER NOT NULL DEFAULT 0,
                        `attempt` INTEGER NOT NULL DEFAULT 0,
                        `error_message` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `dedupe_key` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_background_tasks_status` ON `background_tasks` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_background_tasks_type` ON `background_tasks` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_background_tasks_dedupe_key` ON `background_tasks` (`dedupe_key`)")
            }
        }

        private fun smartSplitParagraphs(content: String): List<String> {
            // If content already has blank-line paragraph breaks, use them
            val blankLineSplit = content.split(Regex("\\n\\s*\\n"))
            if (blankLineSplit.size > 1) {
                return blankLineSplit.map { it.trim() }.filter { it.isNotBlank() }
            }

            // If content has single newlines, treat each as a paragraph
            val newLineSplit = content.split("\n")
            if (newLineSplit.size > 1) {
                return newLineSplit.map { it.trim() }.filter { it.isNotBlank() }
            }

            // Otherwise, split by sentence groups (~3-5 sentences per paragraph)
            val sentenceEnders = Regex("(?<=[.!?])\\s+(?=[A-Z])")
            val sentences = sentenceEnders.split(content).map { it.trim() }.filter { it.isNotBlank() }
            if (sentences.size <= 4) return listOf(content.trim())

            val paragraphs = mutableListOf<String>()
            val buffer = mutableListOf<String>()
            for (sentence in sentences) {
                buffer.add(sentence)
                if (buffer.size >= 4) {
                    paragraphs.add(buffer.joinToString(" "))
                    buffer.clear()
                }
            }
            if (buffer.isNotEmpty()) {
                paragraphs.add(buffer.joinToString(" "))
            }
            return paragraphs
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `article_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `is_system` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_categories_name` ON `article_categories` (`name`)")

                db.execSQL("ALTER TABLE articles ADD COLUMN category_id INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    "INSERT OR IGNORE INTO article_categories (id, name, is_system, created_at, updated_at) VALUES " +
                        "(1, '普通文章', 1, $now, $now)," +
                        "(2, '题目来源', 1, $now, $now)"
                )

                db.execSQL(
                    """
                    UPDATE articles
                    SET category_id = 2
                    WHERE id IN (SELECT linked_article_id FROM question_source_articles)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE question_items ADD COLUMN sample_source_title TEXT")
                db.execSQL("ALTER TABLE question_items ADD COLUMN sample_source_url TEXT")
                db.execSQL("ALTER TABLE question_items ADD COLUMN sample_source_info TEXT")
            }
        }
    }
}
