package com.xty.englishhelper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.xty.englishhelper.ui.navigation.NavGraph
import com.xty.englishhelper.ui.theme.EnglishHelperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnglishHelperTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
