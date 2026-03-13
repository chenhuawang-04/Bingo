package com.xty.englishhelper.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AiProviderProfile(
    val name: String,
    val provider: AiProvider,
    val baseUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)
