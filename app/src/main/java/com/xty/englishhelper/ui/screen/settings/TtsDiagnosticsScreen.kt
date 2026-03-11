package com.xty.englishhelper.ui.screen.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TtsDiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: TtsDiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音诊断") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status
            Text("初始化状态", style = MaterialTheme.typography.titleMedium)
            Text(
                state.initMessage,
                color = if (state.initSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            HorizontalDivider()

            // App settings
            Text("应用设置", style = MaterialTheme.typography.titleMedium)
            Text("语速：${"%.2f".format(state.ttsRate)}x")
            Text("音调：${"%.2f".format(state.ttsPitch)}x")
            Text("口音：${state.ttsLocale}")

            HorizontalDivider()

            // Engine info
            Text("语音引擎", style = MaterialTheme.typography.titleMedium)
            Text("默认引擎：${state.defaultEngine.ifBlank { "未知" }}")
            if (state.engines.isEmpty()) {
                Text("未发现可用引擎", color = MaterialTheme.colorScheme.error)
            } else {
                state.engines.forEach { engine ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(engine.label, style = MaterialTheme.typography.bodyMedium)
                            Text(engine.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (engine.isDefault) {
                            Text("默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            HorizontalDivider()

            // Language support
            Text("语言支持", style = MaterialTheme.typography.titleMedium)
            if (state.languageChecks.isEmpty()) {
                Text("暂无法获取语言支持信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.languageChecks.forEach { lang ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lang.label, style = MaterialTheme.typography.bodyMedium)
                            Text(lang.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            lang.status,
                            color = if (lang.status == "支持") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.englishLanguages.isNotEmpty()) {
                Text("英语语音包（已安装）", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.englishLanguages.take(12).forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.englishLanguages.size > 12) {
                        Text("…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        val actions = listOf(
                            "android.settings.TEXT_TO_SPEECH_SETTINGS",
                            "android.settings.TTS_SETTINGS",
                            "com.android.settings.TTS_SETTINGS"
                        )
                        var opened = false
                        for (action in actions) {
                            val intent = Intent(action)
                            val result = runCatching {
                                context.startActivity(intent)
                                opened = true
                            }
                            if (result.isSuccess) break
                        }
                        if (!opened) {
                            scope.launch { snackbarHostState.showSnackbar("无法打开系统语音设置") }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("打开系统语音设置")
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = viewModel::refresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重新诊断")
                }
            }
        }
    }
}
