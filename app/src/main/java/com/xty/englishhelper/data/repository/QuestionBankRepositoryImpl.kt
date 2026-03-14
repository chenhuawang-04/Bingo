package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.entity.ExamPaperEntity
import com.xty.englishhelper.data.local.entity.PracticeRecordEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupParagraphEntity
import com.xty.englishhelper.data.local.entity.QuestionItemEntity
import com.xty.englishhelper.data.local.entity.QuestionSourceArticleEntity
import com.xty.englishhelper.domain.model.AnswerSource
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.DifficultyLevel
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.SourceVerifyStatus
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionBankRepositoryImpl @Inject constructor(
    private val dao: QuestionBankDao,
    private val articleRepository: ArticleRepository
) : QuestionBankRepository {

    // ── ExamPaper ──

    override fun getAllExamPapers(): Flow<List<ExamPaper>> =
        dao.getAllExamPapers().map { list -> list.map { it.toDomain() } }

    override suspend fun getExamPaperById(id: Long): ExamPaper? =
        dao.getExamPaperById(id)?.toDomain()

    override suspend fun insertExamPaper(paper: ExamPaper): Long {
        val now = System.currentTimeMillis()
        return dao.insertExamPaper(paper.toEntity(now))
    }

    override suspend fun deleteExamPaper(id: Long) = dao.deleteExamPaper(id)

    // ── QuestionGroup ──

    override fun getAllGroupsWithPaperTitle(): Flow<List<QuestionGroup>> =
        dao.getAllGroupsWithPaperTitle().map { list -> list.map { it.toDomain() } }

    override fun getGroupsByPaper(paperId: Long): Flow<List<QuestionGroup>> =
        dao.getGroupsByPaper(paperId).map { list -> list.map { it.toDomain() } }

    override suspend fun getGroupById(groupId: Long): QuestionGroup? {
        val entity = dao.getGroupById(groupId) ?: return null
        val paragraphs = getParagraphs(groupId)
        val items = getItemsByGroup(groupId)
        val linkedArticleId = dao.getLinkedArticleId(groupId)
        val paper = dao.getExamPaperById(entity.examPaperId)
        return entity.toDomain().copy(
            paragraphs = paragraphs,
            items = items,
            linkedArticleId = linkedArticleId,
            examPaperTitle = paper?.title
        )
    }

    override suspend fun insertQuestionGroup(group: QuestionGroup): Long {
        val now = System.currentTimeMillis()
        return dao.insertQuestionGroup(group.toEntity(now))
    }

    override suspend fun deleteQuestionGroup(groupId: Long) = dao.deleteQuestionGroup(groupId)

    override suspend fun updateSourceVerification(groupId: Long, status: Int, error: String?) {
        dao.updateSourceVerification(groupId, status, error, System.currentTimeMillis())
    }

    override suspend fun updateSourceUrl(groupId: Long, url: String) {
        dao.updateSourceUrl(groupId, url, System.currentTimeMillis())
    }

    override suspend fun markHasAiAnswer(groupId: Long) {
        dao.markHasAiAnswer(groupId, System.currentTimeMillis())
    }

    override suspend fun markHasScannedAnswer(groupId: Long) {
        dao.markHasScannedAnswer(groupId, System.currentTimeMillis())
    }

    // ── Paragraphs ──

    override suspend fun getParagraphs(groupId: Long): List<ArticleParagraph> =
        dao.getParagraphs(groupId).map { it.toDomain(groupId) }

    override suspend fun insertParagraphs(groupId: Long, paragraphs: List<ArticleParagraph>) {
        dao.deleteParagraphs(groupId)
        dao.insertParagraphs(paragraphs.mapIndexed { index, p ->
            QuestionGroupParagraphEntity(
                questionGroupId = groupId,
                paragraphIndex = index,
                text = p.text,
                paragraphType = p.paragraphType.name,
                imageUri = p.imageUri,
                imageUrl = p.imageUrl
            )
        })
    }

    // ── QuestionItem ──

    override suspend fun getItemsByGroup(groupId: Long): List<QuestionItem> =
        dao.getItemsByGroup(groupId).map { it.toDomain() }

    override suspend fun insertQuestionItems(items: List<QuestionItem>) {
        dao.insertQuestionItems(items.mapIndexed { index, item ->
            item.toEntity(index)
        })
    }

    override suspend fun updateAnswer(
        itemId: Long, answer: String, source: String,
        explanation: String?, difficultyLevel: String?, difficultyScore: Float?
    ) {
        dao.updateAnswer(itemId, answer, source, explanation, difficultyLevel, difficultyScore)
    }

    override suspend fun incrementWrongCount(itemId: Long) = dao.incrementWrongCount(itemId)

    override suspend fun getWrongItemIds(groupId: Long): List<Long> = dao.getWrongItemIds(groupId)

    // ── PracticeRecord ──

    override suspend fun insertPracticeRecords(records: List<PracticeRecord>) {
        dao.insertPracticeRecords(records.map { it.toEntity() })
    }

    override suspend fun getCorrectCountByGroup(groupId: Long): Int =
        dao.getCorrectCountByGroup(groupId)

    override suspend fun getTotalPracticeCountByGroup(groupId: Long): Int =
        dao.getTotalPracticeCountByGroup(groupId)

    // ── SourceArticle ──

    override suspend fun linkSourceArticle(groupId: Long, articleId: Long) {
        dao.deleteSourceArticle(groupId)
        dao.insertSourceArticle(
            QuestionSourceArticleEntity(
                questionGroupId = groupId,
                linkedArticleId = articleId,
                verifiedAt = System.currentTimeMillis()
            )
        )
        articleRepository.updateArticleCategory(articleId, ArticleCategoryDefaults.SOURCE_ID)
    }

    override suspend fun getLinkedArticleId(groupId: Long): Long? =
        dao.getLinkedArticleId(groupId)

    // ── Composite save ──

    override suspend fun saveScannedPaper(
        paper: ExamPaper,
        groups: List<QuestionGroup>
    ): Long {
        val now = System.currentTimeMillis()
        val paperId = dao.insertExamPaper(paper.toEntity(now))

        var totalQuestions = 0
        groups.forEachIndexed { groupIndex, group ->
            val groupEntity = QuestionGroupEntity(
                uid = group.uid.ifBlank { UUID.randomUUID().toString() },
                examPaperId = paperId,
                questionType = group.questionType.name,
                sectionLabel = group.sectionLabel,
                orderInPaper = groupIndex,
                directions = group.directions,
                passageText = group.passageText,
                sourceInfo = group.sourceInfo,
                sourceUrl = group.sourceUrl,
                sourceAuthor = group.sourceAuthor,
                sourceVerified = 0,
                wordCount = group.wordCount,
                difficultyLevel = group.difficultyLevel?.name,
                difficultyScore = group.difficultyScore,
                createdAt = now,
                updatedAt = now
            )
            val groupId = dao.insertQuestionGroup(groupEntity)

            // Insert paragraphs
            if (group.paragraphs.isNotEmpty()) {
                dao.insertParagraphs(group.paragraphs.mapIndexed { i, p ->
                    QuestionGroupParagraphEntity(
                        questionGroupId = groupId,
                        paragraphIndex = i,
                        text = p.text,
                        paragraphType = p.paragraphType.name
                    )
                })
            }

            // Insert question items
            if (group.items.isNotEmpty()) {
                dao.insertQuestionItems(group.items.mapIndexed { i, item ->
                    item.copy(questionGroupId = groupId).toEntity(i)
                })
                totalQuestions += group.items.size
            }
        }

        dao.updateTotalQuestions(paperId, totalQuestions, now)
        return paperId
    }

    // ── Mappers ──

    private fun ExamPaperEntity.toDomain() = ExamPaper(
        id = id, uid = uid, title = title, description = description,
        totalQuestions = totalQuestions, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun ExamPaper.toEntity(now: Long) = ExamPaperEntity(
        id = id, uid = uid.ifBlank { UUID.randomUUID().toString() },
        title = title, description = description, totalQuestions = totalQuestions,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now
    )

    private fun QuestionGroupEntity.toDomain() = QuestionGroup(
        id = id, uid = uid, examPaperId = examPaperId,
        questionType = QuestionType.entries.find { it.name == questionType } ?: QuestionType.READING_COMPREHENSION,
        sectionLabel = sectionLabel, orderInPaper = orderInPaper,
        directions = directions, passageText = passageText,
        sourceInfo = sourceInfo, sourceUrl = sourceUrl, sourceAuthor = sourceAuthor,
        sourceVerified = when (sourceVerified) { 1 -> SourceVerifyStatus.VERIFIED; -1 -> SourceVerifyStatus.FAILED; else -> SourceVerifyStatus.UNVERIFIED },
        sourceVerifyError = sourceVerifyError,
        wordCount = wordCount,
        difficultyLevel = DifficultyLevel.entries.find { it.name == difficultyLevel },
        difficultyScore = difficultyScore,
        hasAiAnswer = hasAiAnswer != 0, hasScannedAnswer = hasScannedAnswer != 0,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun com.xty.englishhelper.data.local.dao.QuestionGroupWithPaperTitle.toDomain() = QuestionGroup(
        id = id, uid = uid, examPaperId = exam_paper_id,
        questionType = QuestionType.entries.find { it.name == question_type } ?: QuestionType.READING_COMPREHENSION,
        sectionLabel = section_label, orderInPaper = order_in_paper,
        directions = directions, passageText = passage_text,
        sourceInfo = source_info, sourceUrl = source_url, sourceAuthor = source_author,
        sourceVerified = when (source_verified) { 1 -> SourceVerifyStatus.VERIFIED; -1 -> SourceVerifyStatus.FAILED; else -> SourceVerifyStatus.UNVERIFIED },
        sourceVerifyError = source_verify_error,
        wordCount = word_count,
        difficultyLevel = DifficultyLevel.entries.find { it.name == difficulty_level },
        difficultyScore = difficulty_score,
        hasAiAnswer = has_ai_answer != 0, hasScannedAnswer = has_scanned_answer != 0,
        createdAt = created_at, updatedAt = updated_at,
        examPaperTitle = exam_paper_title
    )

    private fun QuestionGroup.toEntity(now: Long) = QuestionGroupEntity(
        id = id, uid = uid.ifBlank { UUID.randomUUID().toString() },
        examPaperId = examPaperId, questionType = questionType.name,
        sectionLabel = sectionLabel, orderInPaper = orderInPaper,
        directions = directions, passageText = passageText,
        sourceInfo = sourceInfo, sourceUrl = sourceUrl, sourceAuthor = sourceAuthor,
        sourceVerified = when (sourceVerified) { SourceVerifyStatus.VERIFIED -> 1; SourceVerifyStatus.FAILED -> -1; else -> 0 },
        sourceVerifyError = sourceVerifyError,
        wordCount = wordCount,
        difficultyLevel = difficultyLevel?.name, difficultyScore = difficultyScore,
        hasAiAnswer = if (hasAiAnswer) 1 else 0, hasScannedAnswer = if (hasScannedAnswer) 1 else 0,
        createdAt = if (createdAt == 0L) now else createdAt, updatedAt = now
    )

    private fun QuestionGroupParagraphEntity.toDomain(groupId: Long) = ArticleParagraph(
        id = id, articleId = groupId, paragraphIndex = paragraphIndex,
        text = text,
        paragraphType = ParagraphType.entries.find { it.name == paragraphType } ?: ParagraphType.TEXT,
        imageUri = imageUri, imageUrl = imageUrl
    )

    private fun QuestionItemEntity.toDomain() = QuestionItem(
        id = id, questionGroupId = questionGroupId,
        questionNumber = questionNumber, questionText = questionText,
        optionA = optionA, optionB = optionB, optionC = optionC, optionD = optionD,
        correctAnswer = correctAnswer,
        answerSource = AnswerSource.entries.find { it.name == answerSource } ?: AnswerSource.NONE,
        explanation = explanation, orderInGroup = orderInGroup,
        wordCount = wordCount,
        difficultyLevel = DifficultyLevel.entries.find { it.name == difficultyLevel },
        difficultyScore = difficultyScore,
        wrongCount = wrongCount, extraData = extraData
    )

    private fun QuestionItem.toEntity(index: Int) = QuestionItemEntity(
        id = id, questionGroupId = questionGroupId,
        questionNumber = questionNumber, questionText = questionText,
        optionA = optionA, optionB = optionB, optionC = optionC, optionD = optionD,
        correctAnswer = correctAnswer, answerSource = answerSource.name,
        explanation = explanation, orderInGroup = index,
        wordCount = wordCount,
        difficultyLevel = difficultyLevel?.name, difficultyScore = difficultyScore,
        wrongCount = wrongCount, extraData = extraData
    )

    private fun PracticeRecord.toEntity() = PracticeRecordEntity(
        id = id, questionItemId = questionItemId,
        userAnswer = userAnswer, isCorrect = if (isCorrect) 1 else 0,
        practicedAt = practicedAt
    )
}
