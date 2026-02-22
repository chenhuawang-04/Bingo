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
import com.xty.englishhelper.ui.navigation.AddWordRoute
import com.xty.englishhelper.ui.navigation.ArticleEditorRoute
import com.xty.englishhelper.ui.navigation.ArticleListRoute
import com.xty.englishhelper.ui.navigation.ArticleReaderRoute
import com.xty.englishhelper.ui.navigation.DictionaryRoute
import com.xty.englishhelper.ui.navigation.HomeRoute
import com.xty.englishhelper.ui.navigation.StudyRoute
import com.xty.englishhelper.ui.navigation.StudySetupRoute
import com.xty.englishhelper.ui.navigation.UnitDetailRoute
import com.xty.englishhelper.ui.navigation.WordDetailRoute

@Composable
fun MainScaffold(
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit
) {
    // Determine current tab based on current destination route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Map routes to tabs
    val dictionaryTabRoutes = listOf(
        "com.xty.englishhelper.ui.navigation.HomeRoute",
        "com.xty.englishhelper.ui.navigation.DictionaryRoute",
        "com.xty.englishhelper.ui.navigation.WordDetailRoute",
        "com.xty.englishhelper.ui.navigation.AddWordRoute",
        "com.xty.englishhelper.ui.navigation.UnitDetailRoute",
        "com.xty.englishhelper.ui.navigation.StudySetupRoute",
        "com.xty.englishhelper.ui.navigation.StudyRoute"
    )

    val articleTabRoutes = listOf(
        "com.xty.englishhelper.ui.navigation.ArticleListRoute",
        "com.xty.englishhelper.ui.navigation.ArticleEditorRoute",
        "com.xty.englishhelper.ui.navigation.ArticleReaderRoute"
    )

    val isInDictionaryTab = currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.HomeRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.DictionaryRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.WordDetailRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.AddWordRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.UnitDetailRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.StudySetupRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.StudyRoute") == true

    val isInArticleTab = currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.ArticleListRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.ArticleEditorRoute") == true ||
            currentRoute?.startsWith("com.xty.englishhelper.ui.navigation.ArticleReaderRoute") == true

    val showBottomBar = isInDictionaryTab || isInArticleTab

    // Navigation helper functions for Tab switching with state preservation
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
        // Compact layout: BottomBar
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
        // Medium/Expanded layout: NavigationRail
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
