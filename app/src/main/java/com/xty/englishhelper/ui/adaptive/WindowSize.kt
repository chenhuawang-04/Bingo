package com.xty.englishhelper.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

@Composable
fun currentWindowSizeClass(): WindowSizeClass =
    currentWindowAdaptiveInfo().windowSizeClass

@Composable
fun currentWindowWidthClass(): WindowWidthSizeClass =
    currentWindowSizeClass().windowWidthSizeClass

fun WindowWidthSizeClass.isCompact(): Boolean = this == WindowWidthSizeClass.COMPACT

fun WindowWidthSizeClass.isExpandedOrMedium(): Boolean =
    this == WindowWidthSizeClass.MEDIUM || this == WindowWidthSizeClass.EXPANDED
