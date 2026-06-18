package com.xty.englishhelper.domain.model

/**
 * 头脑风暴选词 / 展示用的一条「带富信息的关联邻居」。
 *
 * 由 `word_edges` 的一条边按当前词视角定向投影而来：[neighborId] 是对端词，
 * 其余字段保留该边的全部可解释信息，供「选词评分」与「为何相关」展示使用。
 *
 * 与 [WordGraph] 的边模型不同：后者面向整图可视化（端点用节点下标），
 * 这里面向单词邻接查询（端点用 wordId），两者皆源自同一张 `word_edges` 表。
 */
data class EdgeNeighbor(
    val neighborId: Long,
    val type: EdgeType,
    /** 关系强度 1–5（值越大越紧密）。 */
    val relationStrength: Int,
    /** AI 置信度 0–1。 */
    val confidence: Double,
    /** 学习价值 1–5（值越大越值得作为记忆关联）。 */
    val learningValue: Int,
    /** 边状态：core / warning / optional 等。 */
    val status: String,
    /** 关系成立的简述（为何相关）——记忆钩子。 */
    val reason: String?,
    /** 体现该关系的例句。 */
    val exampleSentence: String?,
    /** 语域标注（formal/informal…）。 */
    val register: String?,
    /** 难度 CEFR（A1–C2）。 */
    val difficultyCefr: String?,
    /** 警示（如易混淆提醒）。 */
    val warningNote: String?
)
