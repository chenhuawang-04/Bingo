package com.xty.englishhelper.domain.usecase.questionbank

import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.ScanResult
import com.xty.englishhelper.domain.repository.ScannedQuestionGroup
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.DifficultyLevel
import java.util.UUID
import javax.inject.Inject

/**
 * Offset applied to question group IDs when used as articleId in shared components
 * (ScanWordLinksUseCase, AnalyzeParagraphUseCase, TranslateParagraphUseCase, TTS, etc.)
 */
private const val QB_ARTICLE_ID_OFFSET = 10_000_000L

fun questionBankContentId(groupId: Long): Long = groupId + QB_ARTICLE_ID_OFFSET

/**
 * Convert a ScanResult into domain models ready for saving.
 */
class ConvertScanResultUseCase @Inject constructor() {

    operator fun invoke(scanResult: ScanResult): Pair<ExamPaper, List<QuestionGroup>> {
        val now = System.currentTimeMillis()
        val paper = ExamPaper(
            uid = UUID.randomUUID().toString(),
            title = scanResult.examPaperTitle.ifBlank { "未命名试卷" },
            createdAt = now,
            updatedAt = now
        )

        val groups = scanResult.questionGroups.mapIndexed { index, scanned ->
            scanned.toDomainGroup(index)
        }

        return paper to groups
    }

    private fun ScannedQuestionGroup.toDomainGroup(orderInPaper: Int): QuestionGroup {
        val paragraphs = passageParagraphs.mapIndexed { i, text ->
            ArticleParagraph(paragraphIndex = i, text = text)
        }

        val items = questions.mapIndexed { i, q ->
            QuestionItem(
                questionGroupId = 0,
                questionNumber = q.questionNumber,
                questionText = q.questionText,
                optionA = q.optionA.ifBlank { null },
                optionB = q.optionB.ifBlank { null },
                optionC = q.optionC.ifBlank { null },
                optionD = q.optionD.ifBlank { null },
                orderInGroup = i,
                wordCount = q.wordCount,
                difficultyLevel = DifficultyLevel.entries.find { it.name == q.difficultyLevel },
                difficultyScore = q.difficultyScore
            )
        }

        return QuestionGroup(
            uid = UUID.randomUUID().toString(),
            examPaperId = 0,
            questionType = QuestionType.entries.find { it.name == questionType } ?: QuestionType.READING_COMPREHENSION,
            sectionLabel = sectionLabel,
            orderInPaper = orderInPaper,
            directions = directions,
            passageText = passageParagraphs.joinToString("\n"),
            sourceInfo = sourceInfo,
            sourceUrl = sourceUrl,
            wordCount = wordCount,
            difficultyLevel = DifficultyLevel.entries.find { it.name == difficultyLevel },
            difficultyScore = difficultyScore,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            paragraphs = paragraphs,
            items = items
        )
    }
}

/**
 * Save a scanned paper and its groups to the database.
 */
class SaveScannedPaperUseCase @Inject constructor(
    private val repository: QuestionBankRepository
) {
    suspend operator fun invoke(paper: ExamPaper, groups: List<QuestionGroup>): Long {
        return repository.saveScannedPaper(paper, groups)
    }
}
