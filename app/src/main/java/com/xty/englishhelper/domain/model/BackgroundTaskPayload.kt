package com.xty.englishhelper.domain.model

import kotlinx.serialization.Serializable

sealed interface BackgroundTaskPayload

@Serializable
data class WordOrganizePayload(
    val wordId: Long,
    val dictionaryId: Long,
    val spelling: String
) : BackgroundTaskPayload

@Serializable
data class WordPoolRebuildPayload(
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
