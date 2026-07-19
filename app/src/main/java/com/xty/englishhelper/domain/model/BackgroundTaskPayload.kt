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
data class WordNoteOrganizePayload(
    val dictionaryId: Long,
    val sourceWordId: Long,
    val sourceSpelling: String,
    val targetWordId: Long,
    val targetSpelling: String,
    val edgeType: String = EdgeType.SEMANTIC_OVERLAP.dbValue,
    val organizeTargetWordFirst: Boolean = false,
    val targetReferenceHints: List<String> = emptyList(),
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
 * 词池提纯（AI 评估并降权劣质边）。独立于整理（WORD_POOL_REBUILD），手动触发。
 * 提纯每次整跑（无块级续传坐标系），payload 中的策略仅用于和 QUALITY_FIRST 构建互斥调度。
 */
@Serializable
data class WordPoolReviewPayload(
    val dictionaryId: Long,
    val strategy: String
) : BackgroundTaskPayload

@Serializable
data class WordPhraseOrganizePayload(
    val dictionaryId: Long,
    val dictionaryName: String = "",
    val mode: String = "FILL_MISSING",
    val allowNewTags: Boolean = true,
    val maxPhrasesPerWord: Int = 8,
    val mainModelSnapshot: AiModelSnapshot? = null
) : BackgroundTaskPayload

@Serializable
data class QuestionGeneratePayload(
    val articleId: Long,
    val paperTitle: String = "",
    val questionType: String,
    val variant: String? = null
) : BackgroundTaskPayload

@Serializable
data class ExamPaperGeneratePayload(
    val paperId: Long,
    val paperTitle: String = "",
    val allowIncomplete: Boolean = false
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
    val rescoreAfterHours: Int = 24,
    val forceRefresh: Boolean = false
) : BackgroundTaskPayload

@Serializable
data class ArticleAdvancedScorePayload(
    val startedAt: Long,
    val minimumBasicScore: Int,
    val minimumWordCount: Int,
    val maximumWordCount: Int,
    val promptVersion: Int = 1,
    val forceRefresh: Boolean = false
) : BackgroundTaskPayload

@Serializable
data class AutoPaperSelectPayload(
    val paperId: Long,
    val paperTitle: String,
    val dayKey: String,
    val profile: String,
    val specialQuestionType: String,
    val minimumBasicScore: Int,
    val minimumWordCount: Int,
    val maximumWordCount: Int
) : BackgroundTaskPayload

@Serializable
data class SyncTaskPayload(
    val startedAt: Long,
    val syncMode: String = "SMART",
    val triggeredBy: String = "manual"
) : BackgroundTaskPayload

@Serializable
data class AppUpdateCheckPayload(
    val currentVersion: String,
    val includePrereleases: Boolean = false,
    val checkedAt: Long = 0L,
    val latestVersion: String? = null,
    val releaseName: String? = null,
    val releaseUrl: String? = null,
    val updateAvailable: Boolean = false
) : BackgroundTaskPayload
