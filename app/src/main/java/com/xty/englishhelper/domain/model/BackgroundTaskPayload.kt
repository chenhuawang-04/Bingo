package com.xty.englishhelper.domain.model

import kotlinx.serialization.Serializable

sealed interface BackgroundTaskPayload

@Serializable
data class AiModelSnapshot(
    val providerName: String,
    val provider: AiProvider,
    val model: String,
    val baseUrl: String
)

@Serializable
data class WordOrganizePayload(
    val wordId: Long,
    val dictionaryId: Long,
    val spelling: String,
    val referenceHints: List<String> = emptyList(),
    val highQualityEnabled: Boolean = false,
    val referenceSource: String = WordReferenceSource.FAST.name,
    val mainModelSnapshot: AiModelSnapshot? = null,
    val referenceModelSnapshot: AiModelSnapshot? = null
) : BackgroundTaskPayload

@Serializable
data class WordPoolRebuildPayload(
    val dictionaryId: Long,
    val strategy: String,
    val rebuildMode: String = "INCREMENTAL"  // "FULL" or "INCREMENTAL"
) : BackgroundTaskPayload

/**
 * 词池审核（AI 复查低置信度 / warning 边）。独立于整理（WORD_POOL_REBUILD），手动触发。
 * 审核每次整跑（无块级续传坐标系），故 payload 只需词典 + 策略（策略仅用于复查后重建该策略的词池）。
 */
@Serializable
data class WordPoolReviewPayload(
    val dictionaryId: Long,
    val strategy: String
) : BackgroundTaskPayload

@Serializable
data class QuestionGeneratePayload(
    val articleId: Long,
    val paperTitle: String = "",
    val questionType: String,
    val variant: String? = null
) : BackgroundTaskPayload

@Serializable
data class QuestionAnswerGeneratePayload(
    val groupId: Long,
    val paperTitle: String = "",
    val sectionLabel: String = ""
) : BackgroundTaskPayload

@Serializable
data class QuestionSourceVerifyPayload(
    val groupId: Long,
    val paperTitle: String = "",
    val sectionLabel: String = "",
    val sourceUrlOverride: String? = null
) : BackgroundTaskPayload

@Serializable
data class QuestionWritingSamplePayload(
    val groupId: Long,
    val paperTitle: String = "",
    val questionSnippet: String = ""
) : BackgroundTaskPayload
@Serializable
data class OnlineArticleScanScorePayload(
    val startedAt: Long,
    val maxPerSection: Int = 8,
    val rescoreAfterHours: Int = 24,
    val forceRefresh: Boolean = false
) : BackgroundTaskPayload


