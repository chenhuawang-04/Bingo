package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.ExamPaperEntity
import com.xty.englishhelper.data.local.entity.ExamPaperSourceEntity
import com.xty.englishhelper.data.local.entity.ExamPaperSlotSelectionEntity
import com.xty.englishhelper.data.local.entity.ExamPaperAnswerDraftEntity
import com.xty.englishhelper.data.local.entity.ExamPaperPracticeProgressEntity
import com.xty.englishhelper.data.local.entity.PracticeRecordEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupParagraphEntity
import com.xty.englishhelper.data.local.entity.QuestionItemEntity
import com.xty.englishhelper.data.local.entity.QuestionSourceArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionBankDao {

    // ── ExamPaper ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamPaper(paper: ExamPaperEntity): Long

    @Update
    suspend fun updateExamPaper(paper: ExamPaperEntity)

    @Query("SELECT * FROM exam_papers ORDER BY updated_at DESC")
    fun getAllExamPapers(): Flow<List<ExamPaperEntity>>

    @Query("SELECT * FROM exam_papers WHERE id = :id")
    suspend fun getExamPaperById(id: Long): ExamPaperEntity?

    @Query("SELECT * FROM exam_papers WHERE day_key = :dayKey AND profile = :profile AND status = 'COLLECTING' ORDER BY daily_sequence DESC LIMIT 1")
    suspend fun getCollectingPaperByDay(dayKey: String, profile: String): ExamPaperEntity?

    @Query("SELECT COALESCE(MAX(daily_sequence), 0) FROM exam_papers WHERE day_key = :dayKey AND paper_type = 'COMPOSED'")
    suspend fun getMaxComposedPaperSequenceByDay(dayKey: String): Int

    @Query("SELECT * FROM exam_papers WHERE day_key = :dayKey AND daily_sequence = :sequence LIMIT 1")
    suspend fun getExamPaperByDayAndSequence(dayKey: String, sequence: Int): ExamPaperEntity?

    @Query("SELECT * FROM exam_papers WHERE day_key = :dayKey AND composition_mode = 'AUTOMATIC' ORDER BY daily_sequence DESC LIMIT 1")
    suspend fun getLatestAutoPaperByDay(dayKey: String): ExamPaperEntity?

    @Query("""
        SELECT ep.*,
            (SELECT COUNT(*) FROM exam_paper_sources eps WHERE eps.exam_paper_id = ep.id) AS collected_source_count,
            (SELECT COUNT(*) FROM question_groups qg WHERE qg.exam_paper_id = ep.id) AS generated_group_count
        FROM exam_papers ep
        ORDER BY ep.updated_at DESC
    """)
    fun getAllExamPaperSummaries(): Flow<List<ExamPaperWithProgress>>

    @Query("DELETE FROM exam_papers WHERE id = :id")
    suspend fun deleteExamPaper(id: Long)

    @Query("UPDATE exam_papers SET total_questions = :count, updated_at = :updatedAt WHERE id = :paperId")
    suspend fun updateTotalQuestions(paperId: Long, count: Int, updatedAt: Long)

    @Query("""
        UPDATE exam_papers
        SET status = :status,
            generation_error = :error,
            generation_started_at = COALESCE(:startedAt, generation_started_at),
            generation_completed_at = COALESCE(:completedAt, generation_completed_at),
            updated_at = :updatedAt
        WHERE id = :paperId
    """)
    suspend fun updateExamPaperStatus(
        paperId: Long,
        status: String,
        error: String?,
        startedAt: Long?,
        completedAt: Long?,
        updatedAt: Long
    )

    @Query("UPDATE exam_papers SET special_question_type = :questionType, updated_at = :updatedAt WHERE id = :paperId AND status = 'COLLECTING'")
    suspend fun updateExamPaperSpecialQuestionType(paperId: Long, questionType: String, updatedAt: Long)

    @Query(
        """
        UPDATE exam_papers
        SET selection_status = :status,
            selection_error = :error,
            selection_started_at = COALESCE(:startedAt, selection_started_at),
            selection_completed_at = COALESCE(:completedAt, selection_completed_at),
            updated_at = :updatedAt
        WHERE id = :paperId
        """
    )
    suspend fun updateAutoPaperSelectionStatus(
        paperId: Long,
        status: String,
        error: String?,
        startedAt: Long?,
        completedAt: Long?,
        updatedAt: Long
    )

    @Query("""
        SELECT MAX(ts) FROM (
            SELECT updated_at AS ts FROM exam_papers WHERE id = :paperId
            UNION ALL
            SELECT MAX(updated_at) FROM question_groups WHERE exam_paper_id = :paperId
            UNION ALL
            SELECT MAX(pr.practiced_at) FROM practice_records pr
            INNER JOIN question_items qi ON pr.question_item_id = qi.id
            INNER JOIN question_groups qg ON qi.question_group_id = qg.id
            WHERE qg.exam_paper_id = :paperId
            UNION ALL
            SELECT MAX(updated_at) FROM exam_paper_sources WHERE exam_paper_id = :paperId
            UNION ALL
            SELECT MAX(updated_at) FROM exam_paper_answer_drafts WHERE exam_paper_id = :paperId
            UNION ALL
            SELECT MAX(updated_at) FROM exam_paper_practice_progress WHERE exam_paper_id = :paperId
        )
    """)
    suspend fun getEffectiveUpdatedAt(paperId: Long): Long?

    // ── QuestionGroup ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionGroup(group: QuestionGroupEntity): Long

    @Update
    suspend fun updateQuestionGroup(group: QuestionGroupEntity)

    @Query("SELECT * FROM question_groups WHERE exam_paper_id = :paperId ORDER BY order_in_paper")
    fun getGroupsByPaper(paperId: Long): Flow<List<QuestionGroupEntity>>

    @Query("SELECT * FROM question_groups WHERE exam_paper_id = :paperId ORDER BY order_in_paper")
    suspend fun getGroupsByPaperOnce(paperId: Long): List<QuestionGroupEntity>

    @Query("SELECT * FROM question_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): QuestionGroupEntity?

    @Query("DELETE FROM question_groups WHERE id = :groupId")
    suspend fun deleteQuestionGroup(groupId: Long)

    @Query("""
        SELECT qg.*, ep.title AS exam_paper_title
        FROM question_groups qg
        INNER JOIN exam_papers ep ON qg.exam_paper_id = ep.id
        ORDER BY ep.updated_at DESC, qg.order_in_paper
    """)
    fun getAllGroupsWithPaperTitle(): Flow<List<QuestionGroupWithPaperTitle>>

    @Query("""
        UPDATE question_groups
        SET source_verified = :status, source_verify_error = :error, updated_at = :updatedAt
        WHERE id = :groupId
    """)
    suspend fun updateSourceVerification(groupId: Long, status: Int, error: String?, updatedAt: Long)

    @Query("""
        UPDATE question_groups
        SET source_url = :url, source_verified = 0, source_verify_error = NULL, updated_at = :updatedAt
        WHERE id = :groupId
    """)
    suspend fun updateSourceUrl(groupId: Long, url: String, updatedAt: Long)

    @Query("""
        UPDATE question_groups
        SET source_url = :url,
            source_info = :info,
            source_verified = 0,
            source_verify_error = NULL,
            updated_at = :updatedAt
        WHERE id = :groupId
    """)
    suspend fun updateSourceMeta(groupId: Long, url: String?, info: String?, updatedAt: Long)

    @Query("UPDATE question_groups SET has_ai_answer = 1, updated_at = :updatedAt WHERE id = :groupId")
    suspend fun markHasAiAnswer(groupId: Long, updatedAt: Long)

    @Query("UPDATE question_groups SET has_scanned_answer = 1, updated_at = :updatedAt WHERE id = :groupId")
    suspend fun markHasScannedAnswer(groupId: Long, updatedAt: Long)

    // ── ExamPaperSource ──

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExamPaperSource(source: ExamPaperSourceEntity): Long

    @Query("SELECT * FROM exam_paper_sources WHERE exam_paper_id = :paperId ORDER BY order_in_paper, slot_key")
    fun getExamPaperSources(paperId: Long): Flow<List<ExamPaperSourceEntity>>

    @Query("SELECT * FROM exam_paper_sources WHERE exam_paper_id = :paperId ORDER BY order_in_paper, slot_key")
    suspend fun getExamPaperSourcesOnce(paperId: Long): List<ExamPaperSourceEntity>

    @Query("SELECT * FROM exam_paper_sources WHERE id = :sourceId")
    suspend fun getExamPaperSourceById(sourceId: Long): ExamPaperSourceEntity?

    @Query("""
        SELECT * FROM exam_paper_sources
        WHERE exam_paper_id = :paperId AND article_id = :articleId
          AND question_type = :questionType AND COALESCE(variant, '') = COALESCE(:variant, '')
        LIMIT 1
    """)
    suspend fun findExamPaperSource(
        paperId: Long,
        articleId: Long,
        questionType: String,
        variant: String?
    ): ExamPaperSourceEntity?

    @Query("""
        UPDATE exam_paper_sources
        SET status = :status,
            question_group_id = COALESCE(:groupId, question_group_id),
            error_message = :error,
            updated_at = :updatedAt
        WHERE id = :sourceId
    """)
    suspend fun updateExamPaperSourceStatus(
        sourceId: Long,
        status: String,
        groupId: Long?,
        error: String?,
        updatedAt: Long
    )

    // ── Automatic paper slot selection ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamPaperSlotSelection(selection: ExamPaperSlotSelectionEntity): Long

    @Query("SELECT * FROM exam_paper_slot_selections WHERE exam_paper_id = :paperId ORDER BY id")
    fun getExamPaperSlotSelections(paperId: Long): Flow<List<ExamPaperSlotSelectionEntity>>

    @Query("SELECT * FROM exam_paper_slot_selections WHERE exam_paper_id = :paperId ORDER BY id")
    suspend fun getExamPaperSlotSelectionsOnce(paperId: Long): List<ExamPaperSlotSelectionEntity>

    @Query("SELECT * FROM exam_paper_slot_selections WHERE exam_paper_id = :paperId AND slot_key = :slotKey LIMIT 1")
    suspend fun getExamPaperSlotSelection(paperId: Long, slotKey: String): ExamPaperSlotSelectionEntity?

    // ── QuestionGroupParagraph ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraphs(paragraphs: List<QuestionGroupParagraphEntity>)

    @Query("SELECT * FROM question_group_paragraphs WHERE question_group_id = :groupId ORDER BY paragraph_index")
    suspend fun getParagraphs(groupId: Long): List<QuestionGroupParagraphEntity>

    @Query("DELETE FROM question_group_paragraphs WHERE question_group_id = :groupId")
    suspend fun deleteParagraphs(groupId: Long)

    // ── QuestionItem ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionItems(items: List<QuestionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionItem(item: QuestionItemEntity): Long

    @Update
    suspend fun updateQuestionItem(item: QuestionItemEntity)

    @Query("SELECT * FROM question_items WHERE question_group_id = :groupId ORDER BY order_in_group")
    suspend fun getItemsByGroup(groupId: Long): List<QuestionItemEntity>

    @Query("SELECT * FROM question_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): QuestionItemEntity?

    @Query("""
        UPDATE question_items
        SET correct_answer = :answer, answer_source = :source, explanation = :explanation,
            difficulty_level = :difficultyLevel, difficulty_score = :difficultyScore
        WHERE id = :itemId
    """)
    suspend fun updateAnswer(
        itemId: Long, answer: String, source: String,
        explanation: String?, difficultyLevel: String?, difficultyScore: Float?
    )

    @Query("""
        UPDATE question_items
        SET correct_answer = :sampleText,
            answer_source = :source,
            sample_source_title = :sampleTitle,
            sample_source_url = :sampleUrl,
            sample_source_info = :sampleInfo
        WHERE id = :itemId
    """)
    suspend fun updateWritingSample(
        itemId: Long,
        sampleText: String,
        source: String,
        sampleTitle: String?,
        sampleUrl: String?,
        sampleInfo: String?
    )

    @Query("UPDATE question_items SET wrong_count = wrong_count + 1 WHERE id = :itemId")
    suspend fun incrementWrongCount(itemId: Long)

    @Query("SELECT id FROM question_items WHERE question_group_id = :groupId AND wrong_count > 0")
    suspend fun getWrongItemIds(groupId: Long): List<Long>

    @Query("UPDATE question_items SET extra_data = :extraData WHERE question_group_id = :groupId")
    suspend fun updateItemsExtraDataByGroup(groupId: Long, extraData: String?)

    @Query("SELECT COUNT(*) FROM question_items WHERE question_group_id = :groupId")
    suspend fun getItemCount(groupId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM question_items qi
        INNER JOIN question_groups qg ON qi.question_group_id = qg.id
        WHERE qg.exam_paper_id = :paperId
    """)
    suspend fun getItemCountByPaper(paperId: Long): Int

    // ── PracticeRecord ──

    @Insert
    suspend fun insertPracticeRecord(record: PracticeRecordEntity): Long

    @Insert
    suspend fun insertPracticeRecords(records: List<PracticeRecordEntity>)

    @Query("SELECT * FROM practice_records WHERE question_item_id = :itemId ORDER BY practiced_at DESC")
    suspend fun getRecordsByItem(itemId: Long): List<PracticeRecordEntity>

    @Query("""
        SELECT COUNT(*) FROM practice_records
        WHERE question_item_id IN (SELECT id FROM question_items WHERE question_group_id = :groupId)
        AND is_correct = 1
    """)
    suspend fun getCorrectCountByGroup(groupId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM practice_records
        WHERE question_item_id IN (SELECT id FROM question_items WHERE question_group_id = :groupId)
    """)
    suspend fun getTotalPracticeCountByGroup(groupId: Long): Int

    // ── Full-paper practice recovery ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamPaperAnswerDraft(draft: ExamPaperAnswerDraftEntity)

    @Query("""
        SELECT d.* FROM exam_paper_answer_drafts d
        INNER JOIN question_items qi ON d.question_item_id = qi.id
        WHERE d.exam_paper_id = :paperId AND qi.question_group_id = :groupId
    """)
    suspend fun getExamPaperAnswerDrafts(paperId: Long, groupId: Long): List<ExamPaperAnswerDraftEntity>

    @Query("DELETE FROM exam_paper_answer_drafts WHERE exam_paper_id = :paperId AND question_item_id IN (SELECT id FROM question_items WHERE question_group_id = :groupId)")
    suspend fun clearExamPaperAnswerDrafts(paperId: Long, groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamPaperPracticeProgress(progress: ExamPaperPracticeProgressEntity)

    @Query("DELETE FROM exam_paper_practice_progress WHERE exam_paper_id = :paperId AND question_group_id = :groupId")
    suspend fun clearExamPaperPracticeProgress(paperId: Long, groupId: Long)

    @Query("SELECT question_group_id FROM exam_paper_practice_progress WHERE exam_paper_id = :paperId ORDER BY submitted_at")
    fun observeCompletedExamPaperGroupIds(paperId: Long): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM exam_paper_practice_progress WHERE exam_paper_id = :paperId AND question_group_id = :groupId)")
    suspend fun isExamPaperGroupCompleted(paperId: Long, groupId: Long): Boolean

    @Query("""
        SELECT qg.uid AS group_uid, qi.question_number, d.user_answer, d.updated_at
        FROM exam_paper_answer_drafts d
        INNER JOIN question_items qi ON d.question_item_id = qi.id
        INNER JOIN question_groups qg ON qi.question_group_id = qg.id
        WHERE d.exam_paper_id = :paperId
    """)
    suspend fun getExamPaperAnswerDraftExportRows(paperId: Long): List<ExamPaperAnswerDraftExportRow>

    @Query("""
        SELECT qg.uid FROM exam_paper_practice_progress p
        INNER JOIN question_groups qg ON p.question_group_id = qg.id
        WHERE p.exam_paper_id = :paperId
        ORDER BY p.submitted_at
    """)
    suspend fun getCompletedExamPaperGroupUids(paperId: Long): List<String>

    // ── QuestionSourceArticle ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourceArticle(entity: QuestionSourceArticleEntity): Long

    @Query("SELECT * FROM question_source_articles WHERE question_group_id = :groupId")
    suspend fun getSourceArticle(groupId: Long): QuestionSourceArticleEntity?

    @Query("SELECT linked_article_id FROM question_source_articles WHERE question_group_id = :groupId")
    suspend fun getLinkedArticleId(groupId: Long): Long?

    @Query("DELETE FROM question_source_articles WHERE question_group_id = :groupId")
    suspend fun deleteSourceArticle(groupId: Long)
}

data class QuestionGroupWithPaperTitle(
    val id: Long,
    val uid: String,
    val exam_paper_id: Long,
    val question_type: String,
    val section_label: String?,
    val order_in_paper: Int,
    val directions: String?,
    val passage_text: String,
    val source_info: String?,
    val source_url: String?,
    val source_author: String?,
    val source_verified: Int,
    val source_verify_error: String?,
    val word_count: Int,
    val difficulty_level: String?,
    val difficulty_score: Float?,
    val has_ai_answer: Int,
    val has_scanned_answer: Int,
    val created_at: Long,
    val updated_at: Long,
    val exam_paper_title: String?
)

data class ExamPaperWithProgress(
    val id: Long,
    val uid: String,
    val title: String,
    val description: String?,
    val total_questions: Int,
    val created_at: Long,
    val updated_at: Long,
    val paper_type: String,
    val status: String,
    val day_key: String?,
    val daily_sequence: Int,
    val profile: String,
    val blueprint_version: Int,
    val special_question_type: String?,
    val composition_mode: String,
    val selection_status: String,
    val selection_error: String?,
    val selection_started_at: Long?,
    val selection_completed_at: Long?,
    val generation_error: String?,
    val generation_started_at: Long?,
    val generation_completed_at: Long?,
    val collected_source_count: Int,
    val generated_group_count: Int
)

data class ExamPaperAnswerDraftExportRow(
    val group_uid: String,
    val question_number: Int,
    val user_answer: String,
    val updated_at: Long
)
