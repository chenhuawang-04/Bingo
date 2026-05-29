package com.xty.englishhelper.domain.model

enum class EdgeType(val dbValue: String) {
    SPELLING("SPELLING"),
    MEANING("MEANING"),
    ROOT("ROOT"),
    PRONUNCIATION("PRONUNCIATION"),
    AI_GENERAL("AI");

    companion object {
        fun fromDbValue(value: String): EdgeType {
            return entries.firstOrNull { it.dbValue == value } ?: AI_GENERAL
        }
    }
}
