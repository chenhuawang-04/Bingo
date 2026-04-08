package com.xty.englishhelper.ui.screen.dictionary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.QuickDictionaryEntry
import com.xty.englishhelper.domain.model.QuickDictionarySource
import com.xty.englishhelper.domain.repository.CambridgeDictionaryRepository
import com.xty.englishhelper.domain.repository.OedDictionaryRepository
import com.xty.englishhelper.util.AiJsonRepairer
import com.xty.englishhelper.util.AiResponseUnwrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class QuickDictionaryViewModel @Inject constructor(
    private val cambridgeRepository: CambridgeDictionaryRepository,
    private val oedRepository: OedDictionaryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val aiApiClientProvider: AiApiClientProvider
) : ViewModel() {

    companion object {
        private const val TAG = "QuickDictionaryVM"
    }

    private val _uiState = MutableStateFlow(QuickDictionaryUiState())
    val uiState: StateFlow<QuickDictionaryUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null
    private var lookupJob: Job? = null
    private val lookupSerial = AtomicLong(0L)
    private val lookupLimiter = Semaphore(permits = 3)
    private val entryCache = ConcurrentHashMap<String, CachedEntries>()
    private val cacheTtlMs = 5 * 60 * 1000L
    private val maxCacheEntries = 300

    fun updateQuery(query: String) {
        val normalizedOld = _uiState.value.query.trim()
        val normalizedNew = query.trim()
        _uiState.update {
            it.copy(
                query = query,
                error = null,
                groups = if (normalizedOld != normalizedNew) emptyList() else it.groups
            )
        }
        scheduleSuggestionFetch(query)
    }

    fun setMode(mode: QuickLookupMode) {
        _uiState.update {
            it.copy(mode = mode, suggestions = emptyList(), error = null)
        }
        scheduleSuggestionFetch(_uiState.value.query)
    }

    fun setSource(source: QuickLookupSource) {
        _uiState.update {
            it.copy(source = source, suggestions = emptyList(), error = null)
        }
        scheduleSuggestionFetch(_uiState.value.query)
    }

    fun submitQuery() {
        val query = uiState.value.query.trim()
        if (query.isBlank()) return
        performLookup(query)
    }

    fun selectSuggestion(suggestion: String) {
        _uiState.update {
            it.copy(query = suggestion, suggestions = emptyList(), error = null)
        }
        performLookup(suggestion)
    }

    fun toggleGroupExpanded(word: String) {
        _uiState.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.word.equals(word, ignoreCase = true)) group.copy(expanded = !group.expanded) else group
                }
            )
        }
    }

    private fun scheduleSuggestionFetch(query: String) {
        suggestionJob?.cancel()
        val state = uiState.value
        val requested = query.trim()
        if (state.mode != QuickLookupMode.EN_TO_EN || requested.length < 2 || !state.source.includesCambridge) {
            _uiState.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        suggestionJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(250)
            try {
                val suggestions = cambridgeRepository.searchSuggestions(requested)
                if (!isActive || _uiState.value.query.trim() != requested) return@launch
                _uiState.update { it.copy(suggestions = suggestions.take(12), isSearching = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                Log.w(TAG, "Suggestion fetch failed for query='$requested': ${err.message}")
                if (_uiState.value.query.trim() != requested) return@launch
                _uiState.update { it.copy(isSearching = false, suggestions = emptyList()) }
            }
        }
    }

    private fun performLookup(rawQuery: String) {
        lookupJob?.cancel()
        val requested = rawQuery.trim()
        val serial = lookupSerial.incrementAndGet()
        lookupJob = viewModelScope.launch {
            val modeAtStart = _uiState.value.mode
            val sourceAtStart = _uiState.value.source
            _uiState.update {
                it.copy(isLoading = true, error = null, groups = emptyList(), suggestions = emptyList())
            }
            try {
                val groups = when (modeAtStart) {
                    QuickLookupMode.EN_TO_EN -> {
                        val entries = lookupEntriesForWord(requested, sourceAtStart)
                        listOf(
                            QuickDictionaryWordGroup(
                                word = requested,
                                hint = null,
                                entries = entries,
                                expanded = true
                            )
                        )
                    }

                    QuickLookupMode.ZH_TO_EN -> {
                        val candidates = findEnglishCandidates(requested)
                        supervisorScope {
                            candidates.map { candidate ->
                                async {
                                    lookupLimiter.withPermit {
                                        val entries = lookupEntriesForWord(candidate.word, sourceAtStart)
                                        QuickDictionaryWordGroup(
                                            word = candidate.word,
                                            hint = candidate.note,
                                            entries = entries,
                                            expanded = false
                                        )
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }.filter { it.entries.isNotEmpty() }

                val current = _uiState.value
                if (!isActive ||
                    serial != lookupSerial.get() ||
                    current.query.trim() != requested ||
                    current.mode != modeAtStart ||
                    current.source != sourceAtStart
                ) {
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        groups = groups,
                        error = if (groups.isEmpty()) "未找到可用词条，请尝试更换词典来源或关键词" else null
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                Log.e(TAG, "Lookup failed for query='$requested': ${err.message}", err)
                val current = _uiState.value
                if (!isActive ||
                    serial != lookupSerial.get() ||
                    current.query.trim() != requested ||
                    current.mode != modeAtStart ||
                    current.source != sourceAtStart
                ) {
                    return@launch
                }
                _uiState.update {
                    it.copy(isLoading = false, groups = emptyList(), error = err.message ?: "查询失败")
                }
            }
        }
    }

    private suspend fun lookupEntriesForWord(
        word: String,
        source: QuickLookupSource
    ): List<QuickDictionaryEntry> = supervisorScope {
        val tasks = mutableListOf<Deferred<List<QuickDictionaryEntry>>>()

        if (source.includesCambridge) {
            tasks += async {
                lookupBySourceWithCache(QuickDictionarySource.CAMBRIDGE, word) {
                    val entry = cambridgeRepository.fetchEntry(word)
                    listOf(
                        QuickDictionaryEntry(
                            source = QuickDictionarySource.CAMBRIDGE,
                            sourceLabel = "Cambridge",
                            headword = entry.headword,
                            variant = entry.partOfSpeech,
                            pronunciation = entry.pronunciation,
                            summary = entry.senses.firstOrNull()?.definition.orEmpty(),
                            senses = entry.senses.mapNotNull { sense ->
                                val def = sense.definition.trim()
                                if (def.isBlank()) return@mapNotNull null
                                val trans = sense.translation?.trim().orEmpty()
                                if (trans.isBlank()) def else "$def  |  $trans"
                            },
                            sourceUrl = entry.sourceUrl
                        )
                    )
                }
            }
        }

        if (source.includesOed) {
            tasks += async {
                lookupBySourceWithCache(QuickDictionarySource.OED, word) {
                    oedRepository.lookupWord(word)
                }
            }
        }

        tasks.awaitAll()
            .flatten()
            .filter { it.headword.isNotBlank() || it.summary.isNotBlank() }
            .distinctBy { "${it.source.name}|${it.sourceUrl}" }
    }

    private suspend fun findEnglishCandidates(queryZh: String): List<CandidateWord> {
        val cfg = settingsDataStore.getFastAiConfig()
        if (cfg.apiKey.isBlank()) {
            throw IllegalStateException("未配置 FAST 模型的 API Key，请先在设置中完成配置")
        }

        val prompt = """
            你是英汉词汇助手。请根据用户提供的中文含义，给出最相关的英语候选词。
            只输出 JSON，不要输出任何额外文本。
            JSON 格式：
            {
              "candidates": [
                { "word": "single english word", "note": "简短说明，不超过 20 字" }
              ]
            }
            约束：
            - 返回 3-8 个候选
            - 每个 word 必须是英文单词或常见短语（不超过 3 个词）
            - 按最常用、最贴切优先排序
            用户输入：$queryZh
        """.trimIndent()

        val client = aiApiClientProvider.getClient(cfg.provider)
        val raw = client.sendMessage(
            url = cfg.baseUrl,
            apiKey = cfg.apiKey,
            model = cfg.model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 700
        )

        val cleaned = normalizeAiJson(raw)
        val jsonText = extractFirstJsonObject(cleaned) ?: cleaned
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("candidates") ?: JSONArray()
        val out = mutableListOf<CandidateWord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val word = obj.optString("word").trim()
            val note = obj.optString("note").trim()
            if (word.isBlank()) continue
            out += CandidateWord(
                word = word.lowercase().replace(Regex("\\s+"), " ").trim(),
                note = note
            )
        }
        return out.distinctBy { it.word.lowercase() }.take(6)
    }

    private suspend fun lookupBySourceWithCache(
        source: QuickDictionarySource,
        word: String,
        loader: suspend () -> List<QuickDictionaryEntry>
    ): List<QuickDictionaryEntry> {
        val key = "${source.name}|${word.trim().lowercase()}"
        val now = System.currentTimeMillis()
        val cached = entryCache[key]
        if (cached != null && (now - cached.savedAt) <= cacheTtlMs) {
            return cached.entries
        }
        val loaded = runCatching { loader() }
            .onFailure { Log.w(TAG, "Lookup source '$source' failed for '$word': ${it.message}") }
            .getOrDefault(emptyList())
        entryCache[key] = CachedEntries(savedAt = now, entries = loaded)
        trimCacheIfNeeded()
        return loaded
    }

    private fun trimCacheIfNeeded() {
        if (entryCache.size <= maxCacheEntries) return
        val removeKeys = entryCache.entries
            .sortedBy { it.value.savedAt }
            .take(entryCache.size - maxCacheEntries)
            .map { it.key }
        removeKeys.forEach { entryCache.remove(it) }
    }

    private fun normalizeAiJson(raw: String): String {
        val noFence = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val unwrapped = AiResponseUnwrapper.unwrapJsonEnvelope(noFence) ?: noFence
        return AiJsonRepairer.repair(unwrapped)
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

data class CachedEntries(
    val savedAt: Long,
    val entries: List<QuickDictionaryEntry>
)

enum class QuickLookupMode(val label: String) {
    ZH_TO_EN("中 -> 英"),
    EN_TO_EN("英语查询")
}

enum class QuickLookupSource(val label: String, val includesCambridge: Boolean, val includesOed: Boolean) {
    CAMBRIDGE("Cambridge", includesCambridge = true, includesOed = false),
    OED("OED", includesCambridge = false, includesOed = true),
    BOTH("全部", includesCambridge = true, includesOed = true)
}

data class CandidateWord(
    val word: String,
    val note: String
)

data class QuickDictionaryWordGroup(
    val word: String,
    val hint: String?,
    val entries: List<QuickDictionaryEntry>,
    val expanded: Boolean
)

data class QuickDictionaryUiState(
    val query: String = "",
    val mode: QuickLookupMode = QuickLookupMode.EN_TO_EN,
    val source: QuickLookupSource = QuickLookupSource.BOTH,
    val suggestions: List<String> = emptyList(),
    val groups: List<QuickDictionaryWordGroup> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
