package com.xty.englishhelper.domain.usecase.ai

import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiModelSnapshot
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.AiOrganizeResult
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.WordOrganizeProgress
import com.xty.englishhelper.domain.model.WordReferenceSource
import com.xty.englishhelper.domain.repository.AiRepository
import javax.inject.Inject

class OrganizeWordWithAiUseCase @Inject constructor(
    private val repository: AiRepository,
    private val settingsDataStore: SettingsDataStore
) {
    suspend operator fun invoke(
        word: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider,
        supplementalReferenceHints: List<String> = emptyList(),
        highQualityEnabledOverride: Boolean? = null,
        referenceSourceOverride: WordReferenceSource? = null,
        referenceModelSnapshotOverride: AiModelSnapshot? = null,
        onProgress: (suspend (WordOrganizeProgress) -> Unit)? = null
    ): AiOrganizeResult {
        val highQualityEnabled = highQualityEnabledOverride ?: settingsDataStore.getWordOrganizeHighQualityEnabled()
        if (!highQualityEnabled) {
            onProgress?.invoke(WordOrganizeProgress(current = 0, total = 1, label = "正在使用主模型整理"))
            val result = repository.organizeWord(
                word = word,
                apiKey = apiKey,
                model = model,
                baseUrl = baseUrl,
                provider = provider,
                supplementalReferenceHints = supplementalReferenceHints
            )
            onProgress?.invoke(WordOrganizeProgress(current = 1, total = 1, label = "整理完成"))
            return result
        }

        val total = 2
        val referenceConfig = referenceModelSnapshotOverride ?: run {
            val referenceScope = when (referenceSourceOverride ?: settingsDataStore.getWordOrganizeReferenceSource()) {
                WordReferenceSource.FAST -> AiSettingsScope.FAST
                WordReferenceSource.SEARCH -> AiSettingsScope.SEARCH
            }
            settingsDataStore.getConfiguredAiConfig(referenceScope).let {
                AiModelSnapshot(
                    providerName = it.providerName,
                    provider = it.provider,
                    model = it.model,
                    baseUrl = it.baseUrl
                )
            }
        }
        val referenceApiKey = settingsDataStore.getProviderApiKey(referenceConfig.providerName)

        onProgress?.invoke(WordOrganizeProgress(current = 0, total = total, label = "正在检索网络参考"))
        val reference = if (referenceApiKey.isBlank()) {
            onProgress?.invoke(
                WordOrganizeProgress(
                    current = 1,
                    total = total,
                    label = "参考模型未配置，已回退为常规整理"
                )
            )
            null
        } else {
            runCatching {
                repository.researchWord(
                    word = word,
                    apiKey = referenceApiKey,
                    model = referenceConfig.model,
                    baseUrl = referenceConfig.baseUrl,
                    provider = referenceConfig.provider
                )
            }.onFailure {
                onProgress?.invoke(
                    WordOrganizeProgress(
                        current = 1,
                        total = total,
                        label = "参考检索失败，已继续主模型整理"
                    )
                )
            }.getOrNull()
        }

        val organizeLabel = if (reference?.hasUsefulReference == true) {
            "正在结合网络参考整理"
        } else {
            "正在使用主模型整理"
        }
        onProgress?.invoke(WordOrganizeProgress(current = 1, total = total, label = organizeLabel))
        val result = repository.organizeWord(
            word = word,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl,
            provider = provider,
            reference = reference,
            supplementalReferenceHints = supplementalReferenceHints
        )
        onProgress?.invoke(WordOrganizeProgress(current = total, total = total, label = "整理完成"))
        return result
    }
}

class TestAiConnectionUseCase @Inject constructor(
    private val repository: AiRepository
) {
    suspend operator fun invoke(apiKey: String, model: String, baseUrl: String, provider: AiProvider): Boolean =
        repository.testConnection(apiKey, model, baseUrl, provider)
}
