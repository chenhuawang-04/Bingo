package com.xty.englishhelper.domain.model

data class ArticleAdvancedScore(
    val id: Long = 0,
    val articleId: Long,
    val articleUid: String,
    val questionType: QuestionType,
    val variant: String? = null,
    val score: Int,
    val reason: String,
    val basicScore: Int,
    val wordCount: Int,
    val modelKey: String,
    val promptVersion: Int,
    val scoredAt: Long,
    val updatedAt: Long = scoredAt
) {
    val targetKey: String get() = ArticleAdvancedScoringTargets.key(questionType, variant)
}

data class ArticleAdvancedScoreCandidate(
    val article: Article,
    val advancedScore: ArticleAdvancedScore
)

data class ArticleAdvancedScoringTarget(
    val questionType: QuestionType,
    val variant: String? = null,
    val displayName: String
) {
    val key: String get() = ArticleAdvancedScoringTargets.key(questionType, variant)
}

object ArticleAdvancedScoringTargets {
    val all: List<ArticleAdvancedScoringTarget> = listOf(
        ArticleAdvancedScoringTarget(QuestionType.CLOZE, displayName = "完形填空"),
        ArticleAdvancedScoringTarget(QuestionType.READING_COMPREHENSION, displayName = "阅读理解"),
        ArticleAdvancedScoringTarget(QuestionType.PARAGRAPH_ORDER, displayName = "段落排序"),
        ArticleAdvancedScoringTarget(QuestionType.SENTENCE_INSERTION, displayName = "句子插入"),
        ArticleAdvancedScoringTarget(QuestionType.COMMENT_OPINION_MATCH, displayName = "评论观点匹配"),
        ArticleAdvancedScoringTarget(QuestionType.SUBHEADING_MATCH, displayName = "小标题匹配"),
        ArticleAdvancedScoringTarget(QuestionType.INFORMATION_MATCH, displayName = "信息匹配"),
        ArticleAdvancedScoringTarget(QuestionType.TRANSLATION, "ENG1", "翻译（英语一）"),
        ArticleAdvancedScoringTarget(QuestionType.TRANSLATION, "ENG2", "翻译（英语二）"),
        ArticleAdvancedScoringTarget(QuestionType.WRITING, "SMALL", "写作（小作文）"),
        ArticleAdvancedScoringTarget(QuestionType.WRITING, "LARGE", "写作（大作文）")
    )

    val selectableSpecialTypes: List<QuestionType> = listOf(
        QuestionType.PARAGRAPH_ORDER,
        QuestionType.SENTENCE_INSERTION,
        QuestionType.COMMENT_OPINION_MATCH,
        QuestionType.SUBHEADING_MATCH,
        QuestionType.INFORMATION_MATCH
    )

    fun key(questionType: QuestionType, variant: String?): String =
        "${questionType.name}:${variant.orEmpty()}"

    fun targetFor(questionType: QuestionType, variant: String?): ArticleAdvancedScoringTarget? =
        all.firstOrNull { it.questionType == questionType && variantsMatch(it.variant, variant) }

    fun targetFor(slot: ExamPaperSlot): ArticleAdvancedScoringTarget? =
        targetFor(slot.questionType, slot.variant)

    private fun variantsMatch(expected: String?, actual: String?): Boolean =
        expected.orEmpty() == actual.orEmpty()
}
