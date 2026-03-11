package com.xty.englishhelper.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.TtsMode
import com.xty.englishhelper.domain.model.TtsState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val hasAudioFocus = AtomicBoolean(false)

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var queue: List<String> = emptyList()
    private var queueIndex: Int = 0
    private var currentMode: TtsMode? = null
    private var currentSessionId: String? = null
    private var currentLocaleOverride: String? = null
    private var paused: Boolean = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(listener)
            }
            _state.update {
                it.copy(
                    isReady = ready,
                    error = if (ready) null else "语音引擎初始化失败"
                )
            }
        }
    }

    fun articleSessionId(articleId: Long): String = "article:$articleId"

    fun wordSessionId(wordId: Long): String = "word:$wordId"

    suspend fun speakWord(wordId: Long, text: String, localeOverride: String? = null) {
        startSession(
            mode = TtsMode.WORD,
            sessionId = wordSessionId(wordId),
            texts = listOf(text),
            startIndex = 0,
            localeOverride = localeOverride
        )
    }

    suspend fun speakArticle(articleId: Long, paragraphs: List<String>, startIndex: Int = 0) {
        startSession(
            mode = TtsMode.ARTICLE,
            sessionId = articleSessionId(articleId),
            texts = paragraphs,
            startIndex = startIndex,
            localeOverride = null
        )
    }

    fun pause() {
        if (!_state.value.isSpeaking) return
        paused = true
        tts?.stop()
        abandonAudioFocus()
        _state.update { it.copy(isSpeaking = false) }
    }

    fun resume() {
        if (!paused || queue.isEmpty()) return
        paused = false
        scope.launch { speakCurrent() }
    }

    fun stop() {
        paused = false
        queue = emptyList()
        queueIndex = 0
        currentMode = null
        currentSessionId = null
        currentLocaleOverride = null
        tts?.stop()
        abandonAudioFocus()
        _state.update {
            it.copy(
                isSpeaking = false,
                mode = null,
                sessionId = null,
                currentIndex = 0,
                total = 0
            )
        }
    }

    fun next() {
        if (queueIndex >= queue.lastIndex) return
        queueIndex += 1
        scope.launch { speakCurrent() }
    }

    fun previous() {
        if (queueIndex <= 0) return
        queueIndex -= 1
        scope.launch { speakCurrent() }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun startSession(
        mode: TtsMode,
        sessionId: String,
        texts: List<String>,
        startIndex: Int,
        localeOverride: String?
    ) {
        if (texts.isEmpty()) return
        if (!_state.value.isReady) {
            _state.update { it.copy(error = "语音引擎尚未就绪") }
            return
        }
        paused = false
        currentMode = mode
        currentSessionId = sessionId
        currentLocaleOverride = localeOverride
        queue = texts
        queueIndex = startIndex.coerceIn(0, texts.lastIndex)
        speakCurrent()
    }

    private suspend fun speakCurrent() {
        if (queue.isEmpty() || queueIndex !in queue.indices) return
        if (!_state.value.isReady) return

        val config = settingsDataStore.getTtsConfig()
        val localeTag = currentLocaleOverride ?: config.localeTag
        withContext(Dispatchers.Main.immediate) {
            tts?.setSpeechRate(config.rate)
            tts?.setPitch(config.pitch)
            applyLocale(localeTag)
        }

        val text = queue[queueIndex]
        val utteranceId = "tts_${currentSessionId}_${queueIndex}"
        _state.update {
            it.copy(
                isSpeaking = true,
                mode = currentMode,
                sessionId = currentSessionId,
                currentIndex = queueIndex,
                total = queue.size
            )
        }

        if (!requestAudioFocus()) {
            _state.update { it.copy(isSpeaking = false, error = "无法获取音频焦点") }
            return
        }

        mainHandler.post {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        }
    }

    private fun applyLocale(tag: String) {
        val locale = when (tag) {
            "en-US" -> Locale.US
            "en-GB" -> Locale.UK
            "system" -> Locale.getDefault()
            else -> Locale.forLanguageTag(tag)
        }
        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = Locale.US
            tts?.setLanguage(fallback)
            _state.update { it.copy(error = "当前设备缺少该语言语音包，已切换为默认英语") }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _state.update { it.copy(isSpeaking = true) }
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                if (!_state.value.isSpeaking) return@post
                if (currentMode != TtsMode.ARTICLE || paused) {
                    _state.update { it.copy(isSpeaking = false) }
                    abandonAudioFocus()
                    return@post
                }
                if (queueIndex < queue.lastIndex) {
                    queueIndex += 1
                    scope.launch { speakCurrent() }
                } else {
                    _state.update { it.copy(isSpeaking = false) }
                    abandonAudioFocus()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.update { it.copy(isSpeaking = false, error = "语音播报失败") }
            abandonAudioFocus()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            _state.update { it.copy(isSpeaking = false, error = "语音播报失败") }
            abandonAudioFocus()
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Keep playing; Android may duck other audio.
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // No-op
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus.get()) return true
        val granted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        hasAudioFocus.set(granted)
        return granted
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus.get()) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasAudioFocus.set(false)
    }
}
