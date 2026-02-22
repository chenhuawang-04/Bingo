package com.xty.englishhelper.ui.screen.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowWidthSizeClass
import com.xty.englishhelper.ui.navigation.ArticleEditorRoute
import com.xty.englishhelper.ui.navigation.ArticleListRoute
import com.xty.englishhelper.ui.navigation.ArticleReaderRoute
import com.xty.englishhelper.ui.navigation.AddWordRoute
import com.xty.englishhelper.ui.navigation.DictionaryRoute
import com.xty.englishhelper.ui.navigation.HomeRoute
import com.xty.englishhelper.ui.navigation.StudyRoute
import com.xty.englishhelper.ui.navigation.StudySetupRoute
import com.xty.englishhelper.ui.navigation.UnitDetailRoute
import com.xty.englishhelper.ui.navigation.WordDetailRoute
import kotlin.reflect.KClass

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

private fun matchesTab(currentRoute: String?, prefixes: Set<String>): Boolean =
    currentRoute != null && prefixes.any { currentRoute.startsWith(it) }

@Composable
fun MainScaffold(
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isInDictionaryTab = matchesTab(currentRoute, DICTIONARY_TAB_ROUTES)
    val isInArticleTab = matchesTab(currentRoute, ARTICLE_TAB_ROUTES)
    val showBottomBar = isInDictionaryTab || isInArticleTab

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

    val isCompact = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    if (isCompact) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = isInDictionaryTab,
                            onClick = ::navigateToDictionaryTab,
                            icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                            label = { Text("辞书") }
                        )
                        NavigationBarItem(
                            selected = isInArticleTab,
                            onClick = ::navigateToArticleTab,
                            icon = { Icon(Icons.AutoMirrored.Outlined.Article, null) },
                            label = { Text("文章") }
                        )
                    }
                }
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    } else {
        Row {
            if (showBottomBar) {
                NavigationRail {
                    NavigationRailItem(
                        selected = isInDictionaryTab,
                        onClick = ::navigateToDictionaryTab,
                        icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                        label = { Text("辞书") }
                    )
                    NavigationRailItem(
                        selected = isInArticleTab,
                        onClick = ::navigateToArticleTab,
                        icon = { Icon(Icons.AutoMirrored.Outlined.Article, null) },
                        label = { Text("文章") }
                    )
                }
            }
            content(PaddingValues())
        }
    }
}
