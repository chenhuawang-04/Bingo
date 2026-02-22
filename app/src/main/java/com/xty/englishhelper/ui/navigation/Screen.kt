package com.xty.englishhelper.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class DictionaryRoute(val dictionaryId: Long)

@Serializable
data class WordDetailRoute(val wordId: Long, val dictionaryId: Long)

@Serializable
data class AddWordRoute(val dictionaryId: Long, val wordId: Long = 0L)

@Serializable
data object ImportExportRoute

@Serializable
data object SettingsRoute

@Serializable
data class UnitDetailRoute(val unitId: Long, val dictionaryId: Long)

@Serializable
data class StudySetupRoute(val dictionaryId: Long)

@Serializable
data class StudyRoute(val unitIds: String)

@Serializable
data object ArticleListRoute

@Serializable
data class ArticleEditorRoute(val articleId: Long = 0L)

@Serializable
data class ArticleReaderRoute(val articleId: Long, val scrollToSentenceId: Long = 0L)
