package com.xty.englishhelper.domain.organize

import android.util.Log
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.ai.OrganizeWordWithAiUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class OrganizeTaskStatus { ORGANIZING, SUCCESS, FAILED }

data class OrganizeTask(
    val wordId: Long,
    val dictionaryId: Long,
    val spelling: String,
    val status: OrganizeTaskStatus,
    val error: String? = null
)

@Singleton
class BackgroundOrganizeManager @Inject constructor(
    private val organizeWordWithAi: OrganizeWordWithAiUseCase,
    private val wordRepository: WordRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<Map<Long, OrganizeTask>>(emptyMap())
    val tasks: StateFlow<Map<Long, OrganizeTask>> = _tasks.asStateFlow()

    val organizingWordIds: StateFlow<Set<Long>> =
        MutableStateFlow<Set<Long>>(emptySet()).also { derived ->
            scope.launch {
                _tasks.map { map ->
                    map.filterValues { it.status == OrganizeTaskStatus.ORGANIZING }.keys
                }.collect { derived.value = it }
            }
        }

    fun enqueue(wordId: Long, dictionaryId: Long, spelling: String) {
        if (_tasks.value[wordId]?.status == OrganizeTaskStatus.ORGANIZING) return

        _tasks.update { it + (wordId to OrganizeTask(wordId, dictionaryId, spelling, OrganizeTaskStatus.ORGANIZING)) }

        scope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.MAIN)

                if (config.apiKey.isBlank()) {
                    _tasks.update {
                        it + (wordId to OrganizeTask(wordId, dictionaryId, spelling, OrganizeTaskStatus.FAILED, "API Key 未配置"))
                    }
                    return@launch
                }

                val result = organizeWordWithAi(
                    spelling,
                    config.apiKey,
                    config.model,
                    config.baseUrl,
                    config.provider
                )

                val currentWord = wordRepository.getWordById(wordId)
                if (currentWord == null) {
                    _tasks.update {
                        it + (wordId to OrganizeTask(wordId, dictionaryId, spelling, OrganizeTaskStatus.FAILED, "单词不存在"))
                    }
                    return@launch
                }

                val merged = currentWord.copy(
                    phonetic = currentWord.phonetic.ifBlank { result.phonetic },
                    meanings = currentWord.meanings.ifEmpty { result.meanings },
                    rootExplanation = currentWord.rootExplanation.ifBlank { result.rootExplanation },
                    decomposition = currentWord.decomposition.ifEmpty { result.decomposition },
                    synonyms = currentWord.synonyms.ifEmpty { result.synonyms },
                    similarWords = currentWord.similarWords.ifEmpty { result.similarWords },
                    cognates = currentWord.cognates.ifEmpty { result.cognates },
                    inflections = currentWord.inflections.ifEmpty { result.inflections }
                )
                wordRepository.updateWord(merged)

                _tasks.update {
                    it + (wordId to OrganizeTask(wordId, dictionaryId, spelling, OrganizeTaskStatus.SUCCESS))
                }

                delay(3000)
                _tasks.update { it - wordId }
            } catch (e: Exception) {
                Log.w("BgOrganize", "Failed to organize word $spelling", e)
                _tasks.update {
                    it + (wordId to OrganizeTask(wordId, dictionaryId, spelling, OrganizeTaskStatus.FAILED, e.message))
                }
            }
        }
    }

    fun dismissTask(wordId: Long) {
        _tasks.update { it - wordId }
    }

    fun dismissAll() {
        _tasks.update { map ->
            map.filterValues { it.status == OrganizeTaskStatus.ORGANIZING }
        }
    }

    fun retryAllFailed() {
        val failed = _tasks.value.values.filter { it.status == OrganizeTaskStatus.FAILED }
        for (task in failed) {
            enqueue(task.wordId, task.dictionaryId, task.spelling)
        }
    }

    fun retryFailedForDictionary(dictionaryId: Long) {
        val failed = _tasks.value.values.filter {
            it.status == OrganizeTaskStatus.FAILED && it.dictionaryId == dictionaryId
        }
        for (task in failed) {
            enqueue(task.wordId, task.dictionaryId, task.spelling)
        }
    }
}
