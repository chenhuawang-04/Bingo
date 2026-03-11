package com.xty.englishhelper.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    private var mediaPlayer: MediaPlayer? = null
    private var currentMediaFile: File? = null

    private val prewarmJobs = ConcurrentHashMap<String, Job>()
    private val prewarmCallbacks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val cacheBypass = ConcurrentHashMap<String, Boolean>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(listener)
            }
            _state.update {
                it.copy(
                    isReady = ready,
                    error = if (ready) null else "TTS init failed"
                )
            }
        }
    }

    fun articleSessionId(articleId: Long): String = "article:$articleId"

    fun wordSessionId(wordId: Long): String = "word:$wordId"

    fun prewarmArticle(articleId: Long, paragraphs: List<String>) {
        if (paragraphs.isEmpty()) return
        val sessionId = articleSessionId(articleId)
        prewarmJobs.remove(sessionId)?.cancel()
        val job = ioScope.launch {
            try {
                state.first { it.isReady }
                val config = settingsDataStore.getTtsConfig()
                val cacheDir = articleCacheDir(articleId, config)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val concurrency = config.prewarmConcurrency.coerceIn(1, 6)
                val retryCount = config.prewarmRetry.coerceIn(0, 5)
                val semaphore = Semaphore(concurrency)

                coroutineScope {
                    paragraphs.forEachIndexed { index, text ->
                        launch {
                            semaphore.withPermit {
                                prewarmParagraph(articleId, index, text, config, retryCount)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Best-effort prewarm, ignore failures.
            }
        }
        job.invokeOnCompletion { prewarmJobs.remove(sessionId) }
        prewarmJobs[sessionId] = job
    }

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
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        } else {
            tts?.stop()
        }
        abandonAudioFocus()
        _state.update { it.copy(isSpeaking = false) }
    }

    fun resume() {
        if (!paused || queue.isEmpty()) return
        paused = false
        if (mediaPlayer != null) {
            if (!requestAudioFocus()) {
                _state.update { it.copy(isSpeaking = false, error = "Unable to get audio focus") }
                return
            }
            mediaPlayer?.start()
            _state.update { it.copy(isSpeaking = true) }
            return
        }
        scope.launch { speakCurrent() }
    }

    fun stop() {
        paused = false
        queue = emptyList()
        queueIndex = 0
        currentMode = null
        currentSessionId = null
        currentLocaleOverride = null
        stopMediaPlayer()
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
        stopMediaPlayer()
        scope.launch { speakCurrent() }
    }

    fun previous() {
        if (queueIndex <= 0) return
        queueIndex -= 1
        stopMediaPlayer()
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
            _state.update { it.copy(error = "TTS engine not ready") }
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
            _state.update { it.copy(isSpeaking = false, error = "Unable to get audio focus") }
            return
        }

        val text = queue[queueIndex]
        val articleId = currentArticleId()
        val cacheKey = if (currentMode == TtsMode.ARTICLE && articleId != null) {
            "${articleId}_${queueIndex}"
        } else null
        val cachedFile = if (currentMode == TtsMode.ARTICLE && articleId != null && cacheKey != null && !cacheBypass.containsKey(cacheKey)) {
            cachedAudioFile(articleId, queueIndex, text, config)
        } else null

        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            playCachedAudio(cachedFile)
            return
        }

        val utteranceId = "tts_${currentSessionId}_${queueIndex}"
        mainHandler.post {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        }
    }

    private suspend fun prewarmParagraph(
        articleId: Long,
        index: Int,
        text: String,
        config: SettingsDataStore.TtsConfig,
        retryCount: Int
    ) {
        ensureActive()
        if (text.isBlank()) return
        val file = cachedAudioFile(articleId, index, text, config)
        if (file.exists() && file.length() > 0) return
        if (file.exists()) {
            file.delete()
        }

        var attempt = 0
        val maxAttempts = retryCount.coerceAtLeast(0) + 1
        val cacheKey = "${articleId}_${index}"
        while (attempt < maxAttempts) {
            ensureActive()
            waitUntilNotSpeaking()
            val utteranceId = "prewarm_${articleId}_${index}_${System.currentTimeMillis()}_$attempt"
            val success = synthesizeToFile(text, file, utteranceId, config)
            if (success && file.exists() && file.length() > 0) {
                cacheBypass.remove(cacheKey)
                return
            }
            attempt += 1
            if (attempt < maxAttempts) {
                delay(200L * attempt)
            }
        }

        if (file.exists() && file.length() == 0L) {
            file.delete()
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
            _state.update { it.copy(error = "Missing language data; falling back to English") }
        }
    }

    private fun isPrewarmUtterance(utteranceId: String?): Boolean =
        utteranceId?.startsWith("prewarm_") == true

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (isPrewarmUtterance(utteranceId)) return
            _state.update { it.copy(isSpeaking = true) }
        }

        override fun onDone(utteranceId: String?) {
            if (isPrewarmUtterance(utteranceId)) {
                prewarmCallbacks.remove(utteranceId)?.complete(true)
                return
            }
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
            if (isPrewarmUtterance(utteranceId)) {
                prewarmCallbacks.remove(utteranceId)?.complete(false)
                return
            }
            if (paused) return
            _state.update { it.copy(isSpeaking = false, error = "TTS playback failed") }
            abandonAudioFocus()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (isPrewarmUtterance(utteranceId)) {
                prewarmCallbacks.remove(utteranceId)?.complete(false)
                return
            }
            if (paused) return
            _state.update { it.copy(isSpeaking = false, error = "TTS playback failed") }
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

    private fun currentArticleId(): Long? {
        val session = currentSessionId ?: return null
        if (!session.startsWith("article:")) return null
        return session.removePrefix("article:").toLongOrNull()
    }

    private fun articleCacheDir(articleId: Long, config: SettingsDataStore.TtsConfig): File {
        val key = ttsCacheKey(config)
        return File(appContext.cacheDir, "tts/article_$articleId/$key")
    }

    private fun cachedAudioFile(
        articleId: Long,
        index: Int,
        text: String,
        config: SettingsDataStore.TtsConfig
    ): File {
        val dir = articleCacheDir(articleId, config)
        val hash = hashText(text)
        val name = "p_${index}_$hash.wav"
        return File(dir, name)
    }

    private fun ttsCacheKey(config: SettingsDataStore.TtsConfig): String {
        val rate = String.format(Locale.US, "%.2f", config.rate)
        val pitch = String.format(Locale.US, "%.2f", config.pitch)
        val locale = config.localeTag.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "l_${locale}_r_${rate}_p_${pitch}"
    }

    private fun hashText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun playCachedAudio(file: File) {
        mainHandler.post {
            stopMediaPlayer()
            currentMediaFile = file
            val player = MediaPlayer()
            mediaPlayer = player
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                onMediaCompleted()
            }
            player.setOnErrorListener { _, _, _ ->
                onMediaError()
                true
            }
            player.prepare()
            player.start()
        }
    }

    private fun onMediaCompleted() {
        mainHandler.post {
            stopMediaPlayer()
            currentMediaFile = null
            if (paused) {
                _state.update { it.copy(isSpeaking = false) }
                abandonAudioFocus()
                return@post
            }
            if (currentMode != TtsMode.ARTICLE) {
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

    private fun onMediaError() {
        mainHandler.post {
            val articleId = currentArticleId()
            if (articleId != null) {
                val cacheKey = "${articleId}_${queueIndex}"
                cacheBypass[cacheKey] = true
            }
            currentMediaFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
            stopMediaPlayer()
            currentMediaFile = null
            _state.update { it.copy(isSpeaking = false, error = "TTS playback failed") }
            abandonAudioFocus()
            scope.launch { speakCurrent() }
        }
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.run {
            setOnCompletionListener(null)
            setOnErrorListener(null)
            try {
                stop()
            } catch (_: Exception) {
            }
            reset()
            release()
        }
        mediaPlayer = null
        currentMediaFile = null
    }

    private suspend fun waitUntilNotSpeaking() {
        if (!_state.value.isSpeaking) return
        state.first { !it.isSpeaking }
    }

    private suspend fun synthesizeToFile(
        text: String,
        file: File,
        utteranceId: String,
        config: SettingsDataStore.TtsConfig
    ): Boolean {
        val engine = tts ?: return false
        withContext(Dispatchers.Main.immediate) {
            engine.setSpeechRate(config.rate)
            engine.setPitch(config.pitch)
            applyLocale(config.localeTag)
        }
        val deferred = CompletableDeferred<Boolean>()
        prewarmCallbacks[utteranceId] = deferred
        val status = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            engine.synthesizeToFile(text, Bundle(), file, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            engine.synthesizeToFile(
                text,
                hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId),
                file.absolutePath
            )
        }
        if (status != TextToSpeech.SUCCESS) {
            prewarmCallbacks.remove(utteranceId)
            return false
        }
        val success = withTimeoutOrNull(20000) { deferred.await() } ?: false
        if (!success) {
            prewarmCallbacks.remove(utteranceId)
        }
        return success
    }
}

