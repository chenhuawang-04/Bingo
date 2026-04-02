package com.xty.englishhelper.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class TopLevelRoute {
    @Serializable
    data object Dictionary : TopLevelRoute()

    @Serializable
    data object Article : TopLevelRoute()

    @Serializable
    data object QuestionBank : TopLevelRoute()

    @Serializable
    data object Plan : TopLevelRoute()
}

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
data object TtsDiagnosticsRoute

@Serializable
data object BackgroundTaskRoute

@Serializable
data class UnitDetailRoute(val unitId: Long, val dictionaryId: Long)

@Serializable
data class StudySetupRoute(val dictionaryId: Long)

@Serializable
data class StudyRoute(val unitIds: String, val mode: String = "NORMAL")

@Serializable
data object ArticleListRoute

@Serializable
data class ArticleEditorRoute(val articleId: Long = 0L)

@Serializable
data class ArticleReaderRoute(val articleId: Long, val scrollToSentenceId: Long = 0L)

@Serializable
data class BatchImportRoute(val dictionaryId: Long)

@Serializable
data object GuardianBrowseRoute

@Serializable
data class GuardianArticleRoute(val articleUrl: String)

@Serializable
data object QuestionBankListRoute

@Serializable
data class QuestionBankScanRoute(val editMode: Boolean = false)

@Serializable
data class QuestionBankReaderRoute(val groupId: Long)

@Serializable
data object PlanRoute
