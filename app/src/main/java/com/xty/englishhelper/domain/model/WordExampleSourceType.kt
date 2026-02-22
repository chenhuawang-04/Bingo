package com.xty.englishhelper.domain.model

enum class WordExampleSourceType(val value: Int) {
    MANUAL(0),
    ARTICLE(1);

    companion object {
        fun fromValue(value: Int): WordExampleSourceType =
            entries.find { it.value == value } ?: MANUAL
    }
}
