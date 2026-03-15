package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import kotlinx.coroutines.flow.Flow

interface QuestionBankRepository {

    // ── ExamPaper ──
    fun getAllExamPapers(): Flow<List<ExamPaper>>
    suspend fun getExamPaperById(id: Long): ExamPaper?
    suspend fun insertExamPaper(paper: ExamPaper): Long
    suspend fun deleteExamPaper(id: Long)

    // ── QuestionGroup ──
    fun getAllGroupsWithPaperTitle(): Flow<List<QuestionGroup>>
    fun getGroupsByPaper(paperId: Long): Flow<List<QuestionGroup>>
    suspend fun getGroupById(groupId: Long): QuestionGroup?
    suspend fun insertQuestionGroup(group: QuestionGroup): Long
    suspend fun deleteQuestionGroup(groupId: Long)
    suspend fun updateSourceVerification(groupId: Long, status: Int, error: String?)
    suspend fun updateSourceUrl(groupId: Long, url: String)
    suspend fun updateSourceMeta(groupId: Long, url: String?, info: String?)
    suspend fun markHasAiAnswer(groupId: Long)
    suspend fun markHasScannedAnswer(groupId: Long)

    // ── Paragraphs ──
    suspend fun getParagraphs(groupId: Long): List<ArticleParagraph>
    suspend fun insertParagraphs(groupId: Long, paragraphs: List<ArticleParagraph>)

    // ── QuestionItem ──
    suspend fun getItemsByGroup(groupId: Long): List<QuestionItem>
    suspend fun insertQuestionItems(items: List<QuestionItem>)
    suspend fun updateAnswer(itemId: Long, answer: String, source: String, explanation: String?, difficultyLevel: String?, difficultyScore: Float?)
    suspend fun updateWritingSample(
        itemId: Long,
        sampleText: String,
        source: String,
        sampleTitle: String?,
        sampleUrl: String?,
        sampleInfo: String?
    )
    suspend fun incrementWrongCount(itemId: Long)
    suspend fun getWrongItemIds(groupId: Long): List<Long>

    // ── PracticeRecord ──
    suspend fun insertPracticeRecords(records: List<PracticeRecord>)
    suspend fun getCorrectCountByGroup(groupId: Long): Int
    suspend fun getTotalPracticeCountByGroup(groupId: Long): Int

    // ── SourceArticle ──
    suspend fun linkSourceArticle(groupId: Long, articleId: Long)
    suspend fun getLinkedArticleId(groupId: Long): Long?

    // ── Composite save ──
    suspend fun saveScannedPaper(
        paper: ExamPaper,
        groups: List<QuestionGroup>
    ): Long
}
