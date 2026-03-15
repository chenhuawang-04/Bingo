package com.xty.englishhelper.domain.model

enum class QuestionType(val displayName: String) {
    READING_COMPREHENSION("阅读理解"),
    CLOZE("完形填空"),
    TRANSLATION("翻译"),
    NEW_TYPE("新题型"),
    WRITING("写作");
}

enum class AnswerSource { NONE, AI, SCANNED, WEB }

enum class DifficultyLevel(val displayName: String) {
    EASY("简单"), MEDIUM("中等"), HARD("困难")
}

enum class SourceVerifyStatus { UNVERIFIED, VERIFIED, FAILED }
