package com.xty.englishhelper.ui.screen.notification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.AppNotification
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import java.text.DateFormat
import java.util.Date

@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    AppTopBarEffect(
        title = { Text(stringResource(R.string.notifications_title)) },
        navigationIcon = { AppTopBarBackButton(onBack) },
        actions = {
            IconButton(onClick = viewModel::markAllRead, enabled = state.unreadCount > 0) {
                Icon(Icons.Outlined.DoneAll, stringResource(R.string.notifications_mark_all_read))
            }
            IconButton(onClick = viewModel::clearRead, enabled = state.notifications.any { it.isRead }) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.notifications_clear_read))
            }
        }
    )
    if (state.notifications.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.NotificationsNone, null)
            Text(stringResource(R.string.notifications_empty), modifier = Modifier.padding(top = 12.dp))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.notifications, key = { it.id }) { notification ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .clickable { viewModel.open(notification, onOpen) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (notification.isRead) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(notification.title, style = MaterialTheme.typography.titleSmall)
                            Text(notification.message, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(notification.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.delete(notification.id) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete))
                        }
                    }
                }
            }
        }
    }
}
