package com.xty.englishhelper.domain.model

data class QuestionGroup(
    val id: Long = 0,
    val uid: String,
    val examPaperId: Long,
    val questionType: QuestionType,
    val sectionLabel: String? = null,
    val orderInPaper: Int = 0,
    val directions: String? = null,
    val passageText: String = "",
    val sourceInfo: String? = null,
    val sourceUrl: String? = null,
    val sourceAuthor: String? = null,
    val sourceVerified: SourceVerifyStatus = SourceVerifyStatus.UNVERIFIED,
    val sourceVerifyError: String? = null,
    val wordCount: Int = 0,
    val difficultyLevel: DifficultyLevel? = null,
    val difficultyScore: Float? = null,
    val hasAiAnswer: Boolean = false,
    val hasScannedAnswer: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    // Associated data
    val paragraphs: List<ArticleParagraph> = emptyList(),
    val items: List<QuestionItem> = emptyList(),
    val examPaperTitle: String? = null,
    val linkedArticleId: Long? = null
)
