package com.xty.englishhelper.ui.screen.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class TtsEngineInfo(
    val name: String,
    val label: String,
    val isDefault: Boolean
)

data class TtsLanguageInfo(
    val tag: String,
    val label: String,
    val status: String
)

data class TtsDiagnosticsUiState(
    val isLoading: Boolean = true,
    val initSuccess: Boolean = false,
    val initMessage: String = "初始化中…",
    val defaultEngine: String = "",
    val engines: List<TtsEngineInfo> = emptyList(),
    val languageChecks: List<TtsLanguageInfo> = emptyList(),
    val englishLanguages: List<String> = emptyList(),
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLocale: String = "system",
    val error: String? = null
)

@HiltViewModel
class TtsDiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TtsDiagnosticsUiState())
    val uiState: StateFlow<TtsDiagnosticsUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        loadSettings()
        createTts()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val cfg = settingsDataStore.getTtsConfig()
            _uiState.update {
                it.copy(
                    ttsRate = cfg.rate,
                    ttsPitch = cfg.pitch,
                    ttsLocale = cfg.localeTag
                )
            }
        }
    }

    fun refresh() {
        if (_uiState.value.initSuccess) {
            refreshDiagnostics()
        } else {
            createTts()
        }
    }

    private fun createTts() {
        tts?.shutdown()
        _uiState.update { it.copy(isLoading = true, initMessage = "初始化中…", initSuccess = false, error = null) }
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _uiState.update { it.copy(isLoading = false, initSuccess = true, initMessage = "初始化成功") }
                refreshDiagnostics()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        initSuccess = false,
                        initMessage = "初始化失败",
                        error = "TextToSpeech 初始化失败，请检查系统语音引擎"
                    )
                }
            }
        }
    }

    private fun refreshDiagnostics() {
        val engine = tts ?: return
        val defaultEngine = engine.defaultEngine ?: ""
        val engines = engine.engines.map {
            TtsEngineInfo(
                name = it.name,
                label = it.label ?: it.name,
                isDefault = it.name == defaultEngine
            )
        }

        val locales = listOf(
            Locale.getDefault(),
            Locale.US,
            Locale.UK
        ).distinctBy { it.toLanguageTag() }

        val languageChecks = locales.map { locale ->
            val code = engine.isLanguageAvailable(locale)
            val status = when (code) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "支持"
                TextToSpeech.LANG_MISSING_DATA -> "缺少数据"
                TextToSpeech.LANG_NOT_SUPPORTED -> "不支持"
                else -> "未知"
            }
            TtsLanguageInfo(
                tag = locale.toLanguageTag(),
                label = locale.displayName,
                status = status
            )
        }

        val englishLangs = engine.availableLanguages
            ?.filter { it.language == "en" }
            ?.map { it.toLanguageTag() }
            ?.sorted()
            ?: emptyList()

        _uiState.update {
            it.copy(
                defaultEngine = defaultEngine,
                engines = engines,
                languageChecks = languageChecks,
                englishLanguages = englishLangs
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }
}
