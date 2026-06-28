package com.xty.englishhelper.ui.components.topbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.xty.englishhelper.R

typealias AppTopBarActions = @Composable RowScope.() -> Unit

data class AppTopBarState(
    val visible: () -> Boolean = { true },
    val title: @Composable () -> Unit = {},
    val navigationIcon: @Composable () -> Unit = {},
    val actions: AppTopBarActions = {}
)

class AppTopBarController {
    private var owner: Any? = null

    var state by mutableStateOf(AppTopBarState())
        private set

    fun set(owner: Any, nextState: AppTopBarState) {
        this.owner = owner
        state = nextState
    }

    fun clear(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            state = AppTopBarState()
        }
    }
}

val LocalAppTopBarController = compositionLocalOf<AppTopBarController> {
    error("AppTopBarController is not provided")
}

@Composable
fun rememberAppTopBarController(): AppTopBarController = remember { AppTopBarController() }

@Composable
fun AppTopBarEffect(
    visible: Boolean = true,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: AppTopBarActions = {}
) {
    val controller = LocalAppTopBarController.current
    val owner = remember { Any() }
    val latestVisible: State<Boolean> = rememberUpdatedState(visible)
    val latestTitle: State<@Composable () -> Unit> = rememberUpdatedState(title)
    val latestNavigationIcon: State<@Composable () -> Unit> = rememberUpdatedState(navigationIcon)
    val latestActions: State<AppTopBarActions> = rememberUpdatedState(actions)

    DisposableEffect(controller, owner) {
        controller.set(
            owner,
            AppTopBarState(
                visible = { latestVisible.value },
                title = { latestTitle.value() },
                navigationIcon = { latestNavigationIcon.value() },
                actions = { latestActions.value.invoke(this) }
            )
        )
        onDispose {
            controller.clear(owner)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedAppTopBar(
    controller: AppTopBarController,
    onQuickSearchClick: () -> Unit
) {
    val state = controller.state
    if (!state.visible()) return

    TopAppBar(
        title = state.title,
        navigationIcon = state.navigationIcon,
        actions = {
            state.actions.invoke(this)
            IconButton(onClick = onQuickSearchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.quick_search)
                )
            }
        }
    )
}

@Composable
fun AppTopBarBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_back)
        )
    }
}

@Composable
fun AppTopBarCloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.common_close)
        )
    }
}
