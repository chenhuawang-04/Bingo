package com.xty.englishhelper.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AiScopeConfig(
    val providerName: String,
    val model: String
)
