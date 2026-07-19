package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.ExamPaperCollectionResult
import com.xty.englishhelper.domain.model.ExamPaperProfile
import com.xty.englishhelper.domain.model.ExamPaperSource
import com.xty.englishhelper.domain.model.ExamPaperSummary
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import kotlinx.coroutines.flow.Flow

interface QuestionBankRepository {

    // ── ExamPaper ──
    fun getAllExamPapers(): Flow<List<ExamPaper>>
    fun getAllExamPaperSummaries(): Flow<List<ExamPaperSummary>>
    suspend fun getExamPaperById(id: Long): ExamPaper?
    suspend fun insertExamPaper(paper: ExamPaper): Long
    suspend fun deleteExamPaper(id: Long)
    suspend fun collectArticleForPaper(
        articleId: Long,
        dayKey: String,
        profile: ExamPaperProfile,
        specialQuestionType: QuestionType?,
        questionType: QuestionType,
        variant: String?
    ): ExamPaperCollectionResult
    fun getExamPaperSources(paperId: Long): Flow<List<ExamPaperSource>>
    suspend fun getExamPaperSourcesOnce(paperId: Long): List<ExamPaperSource>
    suspend fun updateExamPaperStatus(
        paperId: Long,
        status: com.xty.englishhelper.domain.model.ExamPaperStatus,
        error: String? = null,
        startedAt: Long? = null,
        completedAt: Long? = null
    )
    suspend fun updateExamPaperSourceStatus(
        sourceId: Long,
        status: com.xty.englishhelper.domain.model.ExamPaperSourceStatus,
        groupId: Long? = null,
        error: String? = null
    )

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
    suspend fun updateItemsExtraDataByGroup(groupId: Long, extraData: String?)

    // ── PracticeRecord ──
    suspend fun insertPracticeRecords(records: List<PracticeRecord>)
    suspend fun getCorrectCountByGroup(groupId: Long): Int
    suspend fun getTotalPracticeCountByGroup(groupId: Long): Int
    suspend fun saveExamPaperAnswerDraft(paperId: Long, itemId: Long, answer: String)
    suspend fun getExamPaperAnswerDrafts(paperId: Long, groupId: Long): Map<Long, String>
    suspend fun markExamPaperGroupCompleted(paperId: Long, groupId: Long)
    suspend fun resetExamPaperGroupProgress(paperId: Long, groupId: Long)
    fun observeCompletedExamPaperGroupIds(paperId: Long): Flow<List<Long>>
    suspend fun isExamPaperGroupCompleted(paperId: Long, groupId: Long): Boolean

    // ── SourceArticle ──
    suspend fun linkSourceArticle(groupId: Long, articleId: Long)
    suspend fun getLinkedArticleId(groupId: Long): Long?

    // ── Composite save ──
    suspend fun saveScannedPaper(
        paper: ExamPaper,
        groups: List<QuestionGroup>
    ): Long
    suspend fun saveGeneratedGroup(
        paperId: Long,
        source: ExamPaperSource,
        group: QuestionGroup
    ): Long
}
