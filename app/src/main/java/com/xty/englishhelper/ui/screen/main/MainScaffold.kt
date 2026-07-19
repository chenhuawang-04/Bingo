package com.xty.englishhelper.ui.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowWidthSizeClass
import com.xty.englishhelper.R
import com.xty.englishhelper.ui.components.dictionary.QuickDictionarySheet
import com.xty.englishhelper.ui.components.topbar.LocalAppTopBarController
import com.xty.englishhelper.ui.components.topbar.ManagedAppTopBar
import com.xty.englishhelper.ui.components.topbar.rememberAppTopBarController
import com.xty.englishhelper.ui.debug.AiDebugDialogHost
import com.xty.englishhelper.ui.navigation.AddWordRoute
import com.xty.englishhelper.ui.navigation.ArticleEditorRoute
import com.xty.englishhelper.ui.navigation.ArticleListRoute
import com.xty.englishhelper.ui.navigation.ArticleReaderRoute
import com.xty.englishhelper.ui.navigation.DictionaryRoute
import com.xty.englishhelper.ui.navigation.HomeRoute
import com.xty.englishhelper.ui.navigation.NotificationRoute
import com.xty.englishhelper.ui.navigation.PlanRoute
import com.xty.englishhelper.ui.navigation.QuestionBankListRoute
import com.xty.englishhelper.ui.navigation.QuestionBankReaderRoute
import com.xty.englishhelper.ui.navigation.QuestionBankScanRoute
import com.xty.englishhelper.ui.navigation.StudyRoute
import com.xty.englishhelper.ui.navigation.StudySetupRoute
import com.xty.englishhelper.ui.navigation.UnitDetailRoute
import com.xty.englishhelper.ui.navigation.WordDetailRoute
import com.xty.englishhelper.ui.screen.dictionary.QuickDictionaryViewModel
import com.xty.englishhelper.ui.screen.notification.NotificationViewModel

private val DICTIONARY_TAB_ROUTES: Set<String> = setOf(
    HomeRoute::class,
    DictionaryRoute::class,
    WordDetailRoute::class,
    AddWordRoute::class,
    UnitDetailRoute::class,
    StudySetupRoute::class,
    StudyRoute::class
).mapNotNull { it.qualifiedName }.toSet()

private val ARTICLE_TAB_ROUTES: Set<String> = setOf(
    ArticleListRoute::class,
    ArticleEditorRoute::class,
    ArticleReaderRoute::class
).mapNotNull { it.qualifiedName }.toSet()

private val QUESTION_BANK_TAB_ROUTES: Set<String> = setOf(
    QuestionBankListRoute::class,
    QuestionBankScanRoute::class,
    QuestionBankReaderRoute::class
).mapNotNull { it.qualifiedName }.toSet()

private val PLAN_TAB_ROUTES: Set<String> = setOf(
    PlanRoute::class
).mapNotNull { it.qualifiedName }.toSet()

private fun matchesTab(currentRoute: String?, prefixes: Set<String>): Boolean =
    currentRoute != null && prefixes.any { currentRoute.startsWith(it) }

@Composable
fun MainScaffold(
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit
) {
    var showQuickDictionary by rememberSaveable { mutableStateOf(false) }
    val quickDictionaryViewModel: QuickDictionaryViewModel = hiltViewModel()
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val notificationState by notificationViewModel.uiState.collectAsState()
    val topBarController = rememberAppTopBarController()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isInDictionaryTab = matchesTab(currentRoute, DICTIONARY_TAB_ROUTES)
    val isInArticleTab = matchesTab(currentRoute, ARTICLE_TAB_ROUTES)
    val isInQuestionBankTab = matchesTab(currentRoute, QUESTION_BANK_TAB_ROUTES)
    val isInPlanTab = matchesTab(currentRoute, PLAN_TAB_ROUTES)
    val showBottomBar = isInDictionaryTab || isInArticleTab || isInQuestionBankTab || isInPlanTab

    fun navigateToDictionaryTab() {
        navController.navigate(HomeRoute) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToArticleTab() {
        navController.navigate(ArticleListRoute) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToQuestionBankTab() {
        navController.navigate(QuestionBankListRoute) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToPlanTab() {
        navController.navigate(PlanRoute) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isCompact = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    CompositionLocalProvider(LocalAppTopBarController provides topBarController) {
        QuickDictionarySheet(
            visible = showQuickDictionary,
            onDismiss = { showQuickDictionary = false },
            viewModel = quickDictionaryViewModel
        )

        if (isCompact) {
            Scaffold(
                topBar = {
                    ManagedAppTopBar(
                        controller = topBarController,
                        onQuickSearchClick = { showQuickDictionary = true },
                        onNotificationsClick = { navController.navigate(NotificationRoute) },
                        unreadNotificationCount = notificationState.unreadCount
                    )
                },
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = isInDictionaryTab,
                                onClick = ::navigateToDictionaryTab,
                                icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                                label = { Text(stringResource(R.string.nav_dictionary)) }
                            )
                            NavigationBarItem(
                                selected = isInArticleTab,
                                onClick = ::navigateToArticleTab,
                                icon = { Icon(Icons.AutoMirrored.Outlined.Article, null) },
                                label = { Text(stringResource(R.string.nav_article)) }
                            )
                            NavigationBarItem(
                                selected = isInQuestionBankTab,
                                onClick = ::navigateToQuestionBankTab,
                                icon = { Icon(Icons.Outlined.Quiz, null) },
                                label = { Text(stringResource(R.string.question_bank)) }
                            )
                            NavigationBarItem(
                                selected = isInPlanTab,
                                onClick = ::navigateToPlanTab,
                                icon = { Icon(Icons.Outlined.DateRange, null) },
                                label = { Text(stringResource(R.string.nav_plan)) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    content(PaddingValues())
                    AiDebugDialogHost()
                }
            }
        } else {
            Scaffold(
                topBar = {
                    ManagedAppTopBar(
                        controller = topBarController,
                        onQuickSearchClick = { showQuickDictionary = true },
                        onNotificationsClick = { navController.navigate(NotificationRoute) },
                        unreadNotificationCount = notificationState.unreadCount
                    )
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showBottomBar) {
                        NavigationRail {
                            NavigationRailItem(
                                selected = isInDictionaryTab,
                                onClick = ::navigateToDictionaryTab,
                                icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                                label = { Text(stringResource(R.string.nav_dictionary)) }
                            )
                            NavigationRailItem(
                                selected = isInArticleTab,
                                onClick = ::navigateToArticleTab,
                                icon = { Icon(Icons.AutoMirrored.Outlined.Article, null) },
                                label = { Text(stringResource(R.string.nav_article)) }
                            )
                            NavigationRailItem(
                                selected = isInQuestionBankTab,
                                onClick = ::navigateToQuestionBankTab,
                                icon = { Icon(Icons.Outlined.Quiz, null) },
                                label = { Text(stringResource(R.string.question_bank)) }
                            )
                            NavigationRailItem(
                                selected = isInPlanTab,
                                onClick = ::navigateToPlanTab,
                                icon = { Icon(Icons.Outlined.DateRange, null) },
                                label = { Text(stringResource(R.string.nav_plan)) }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        content(PaddingValues())
                    }
                }
            }

            AiDebugDialogHost()
        }
    }
}
