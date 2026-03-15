package com.xty.englishhelper.domain.model

enum class OnlineReadingSource(val key: String, val label: String) {
    GUARDIAN("guardian", "卫报"),
    CSMONITOR("csmonitor", "CSMonitor");

    companion object {
        fun fromKey(raw: String?): OnlineReadingSource {
            return values().firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: GUARDIAN
        }
    }
}
