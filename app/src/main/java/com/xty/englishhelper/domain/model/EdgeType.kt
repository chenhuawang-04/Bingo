package com.xty.englishhelper.domain.model

enum class EdgeType(
    val dbValue: String,
    val cluster: EdgeCluster,
    val label: String,
    val shortCode: String
) {
    // Semantic cluster
    SEMANTIC_SYNONYM("SEMANTIC_SYNONYM", EdgeCluster.SEMANTIC, "意近/同义", "SS"),
    SEMANTIC_ANTONYM("SEMANTIC_ANTONYM", EdgeCluster.SEMANTIC, "反义", "SA"),
    SEMANTIC_OVERLAP("SEMANTIC_OVERLAP", EdgeCluster.SEMANTIC, "语义重叠", "SO"),
    SEMANTIC_HYPERNYM("SEMANTIC_HYPERNYM", EdgeCluster.SEMANTIC, "上位词", "SHup"),
    SEMANTIC_HYPONYM("SEMANTIC_HYPONYM", EdgeCluster.SEMANTIC, "下位词", "SHy"),

    // Form cluster
    FORM_SPELLING("FORM_SPELLING", EdgeCluster.FORM, "形近", "FS"),
    FORM_HOMOPHONE("FORM_HOMOPHONE", EdgeCluster.FORM, "同音", "FH"),
    FORM_PRONUNCIATION("FORM_PRONUNCIATION", EdgeCluster.FORM, "发音相似", "FP"),
    FORM_MINIMAL_PAIR("FORM_MINIMAL_PAIR", EdgeCluster.FORM, "最小对立体", "FM"),

    // Family cluster
    FAMILY_INFLECTION("FAMILY_INFLECTION", EdgeCluster.FAMILY, "屈折变化", "FI"),
    FAMILY_DERIVATION("FAMILY_DERIVATION", EdgeCluster.FAMILY, "派生词", "FD"),
    FAMILY_SAME_ROOT("FAMILY_SAME_ROOT", EdgeCluster.FAMILY, "同词根", "FR"),

    // Usage cluster
    USAGE_COLLOCATION("USAGE_COLLOCATION", EdgeCluster.USAGE, "搭配", "UC"),
    USAGE_PHRASE("USAGE_PHRASE", EdgeCluster.USAGE, "短语", "UP"),
    USAGE_PATTERN("USAGE_PATTERN", EdgeCluster.USAGE, "句型", "UPat"),

    // Learning cluster
    LEARNING_CONFUSABLE("LEARNING_CONFUSABLE", EdgeCluster.LEARNING, "易混淆", "LC"),
    LEARNING_MISUSE_PAIR("LEARNING_MISUSE_PAIR", EdgeCluster.LEARNING, "误用配对", "LM");

    companion object {
        fun fromDbValue(value: String): EdgeType? =
            entries.firstOrNull { it.dbValue == value }

        fun fromShortCode(code: String): EdgeType =
            entries.firstOrNull { it.shortCode.equals(code, ignoreCase = true) } ?: FORM_SPELLING
    }
}

enum class EdgeCluster(val label: String) {
    SEMANTIC("语义"),
    FORM("形式"),
    FAMILY("词族"),
    USAGE("用法"),
    LEARNING("学习")
}
