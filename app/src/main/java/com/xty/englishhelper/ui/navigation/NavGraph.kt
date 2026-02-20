package com.xty.englishhelper.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.xty.englishhelper.ui.screen.addword.AddWordScreen
import com.xty.englishhelper.ui.screen.dictionary.DictionaryScreen
import com.xty.englishhelper.ui.screen.home.HomeScreen
import com.xty.englishhelper.ui.screen.importexport.ImportExportScreen
import com.xty.englishhelper.ui.screen.settings.SettingsScreen
import com.xty.englishhelper.ui.screen.study.StudyScreen
import com.xty.englishhelper.ui.screen.study.StudySetupScreen
import com.xty.englishhelper.ui.screen.unitdetail.UnitDetailScreen
import com.xty.englishhelper.ui.screen.word.WordDetailScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onDictionaryClick = { dictId ->
                    navController.navigate(DictionaryRoute(dictId))
                },
                onImportExport = {
                    navController.navigate(ImportExportRoute)
                },
                onSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }

        composable<DictionaryRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DictionaryRoute>()
            DictionaryScreen(
                onBack = { navController.popBackStack() },
                onWordClick = { wordId, dictId ->
                    navController.navigate(WordDetailRoute(wordId, dictId))
                },
                onAddWord = { dictId ->
                    navController.navigate(AddWordRoute(dictId))
                },
                onUnitClick = { unitId, dictId ->
                    navController.navigate(UnitDetailRoute(unitId, dictId))
                },
                onStudy = { dictId ->
                    navController.navigate(StudySetupRoute(dictId))
                }
            )
        }

        composable<WordDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WordDetailRoute>()
            WordDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { dictId, wordId ->
                    navController.navigate(AddWordRoute(dictId, wordId))
                },
                onWordClick = { wordId, dictId ->
                    navController.navigate(WordDetailRoute(wordId, dictId))
                }
            )
        }

        composable<AddWordRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AddWordRoute>()
            AddWordScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<ImportExportRoute> {
            ImportExportScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<UnitDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnitDetailRoute>()
            UnitDetailScreen(
                onBack = { navController.popBackStack() },
                onWordClick = { wordId, dictId ->
                    navController.navigate(WordDetailRoute(wordId, dictId))
                }
            )
        }

        composable<StudySetupRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<StudySetupRoute>()
            StudySetupScreen(
                onBack = { navController.popBackStack() },
                onStartStudy = { unitIds ->
                    navController.navigate(StudyRoute(unitIds))
                }
            )
        }

        composable<StudyRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<StudyRoute>()
            StudyScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
