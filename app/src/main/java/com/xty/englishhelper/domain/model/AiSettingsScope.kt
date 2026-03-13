package com.xty.englishhelper.domain.model

enum class AiSettingsScope(val prefix: String) {
    MAIN(""),
    POOL("pool_"),
    OCR("ocr_"),
    ARTICLE("article_"),
    SCAN("scan_"),
    SEARCH("search_")
}
