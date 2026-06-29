package com.xty.englishhelper.domain.model

enum class AiSettingsScope(val prefix: String) {
    MAIN(""),
    FAST("fast_"),
    POOL("pool_"),
    OCR("ocr_"),
    ARTICLE("article_"),
    SEARCH("search_"),
    REVIEWER("reviewer_"),
    QUESTION_GENERATE("qgen_"),
    WRITING_PRACTICE("writing_practice_")
}
