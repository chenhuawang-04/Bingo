package com.xty.englishhelper.ui.screen.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.AppNotification
import com.xty.englishhelper.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class NotificationUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeAll(), repository.observeUnreadCount()) { notifications, unread ->
                NotificationUiState(notifications, unread)
            }.collect { state -> _uiState.value = state }
        }
    }

    fun open(notification: AppNotification, navigate: (AppNotification) -> Unit) {
        viewModelScope.launch {
            if (!notification.isRead) repository.markRead(notification.id)
            navigate(notification)
        }
    }

    fun markAllRead() = viewModelScope.launch { repository.markAllRead() }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun clearRead() = viewModelScope.launch { repository.clearRead() }
}
