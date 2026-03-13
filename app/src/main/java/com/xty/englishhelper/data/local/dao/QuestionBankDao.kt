package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.ExamPaperEntity
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

    @Query("DELETE FROM exam_papers WHERE id = :id")
    suspend fun deleteExamPaper(id: Long)

    @Query("UPDATE exam_papers SET total_questions = :count, updated_at = :updatedAt WHERE id = :paperId")
    suspend fun updateTotalQuestions(paperId: Long, count: Int, updatedAt: Long)

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

    @Query("UPDATE question_groups SET has_ai_answer = 1, updated_at = :updatedAt WHERE id = :groupId")
    suspend fun markHasAiAnswer(groupId: Long, updatedAt: Long)

    @Query("UPDATE question_groups SET has_scanned_answer = 1, updated_at = :updatedAt WHERE id = :groupId")
    suspend fun markHasScannedAnswer(groupId: Long, updatedAt: Long)

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

    @Query("UPDATE question_items SET wrong_count = wrong_count + 1 WHERE id = :itemId")
    suspend fun incrementWrongCount(itemId: Long)

    @Query("SELECT id FROM question_items WHERE question_group_id = :groupId AND wrong_count > 0")
    suspend fun getWrongItemIds(groupId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM question_items WHERE question_group_id = :groupId")
    suspend fun getItemCount(groupId: Long): Int

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
