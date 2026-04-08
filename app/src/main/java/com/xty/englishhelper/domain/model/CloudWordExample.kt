package com.xty.englishhelper.domain.model

enum class CloudExampleSource(val label: String) {
    CAMBRIDGE("Cambridge"),
    OED("Oxford (OED)")
}

data class CloudWordExample(
    val sentence: String,
    val source: CloudExampleSource,
    val sourceLabel: String,
    val sourceUrl: String
)
