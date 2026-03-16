package com.xty.englishhelper.domain.model

enum class QuestionType(val displayName: String) {
    READING_COMPREHENSION("阅读理解"),
    CLOZE("完形填空"),
    TRANSLATION("翻译"),
    PARAGRAPH_ORDER("段落排序"),
    SENTENCE_INSERTION("句子插入"),
    COMMENT_OPINION_MATCH("评论观点匹配"),
    SUBHEADING_MATCH("小标题匹配"),
    INFORMATION_MATCH("信息匹配"),
    NEW_TYPE("新题型"),
    WRITING("写作");
}

enum class AnswerSource { NONE, AI, SCANNED, WEB }

enum class DifficultyLevel(val displayName: String) {
    EASY("简单"), MEDIUM("中等"), HARD("困难")
}

enum class SourceVerifyStatus { UNVERIFIED, VERIFIED, FAILED }
