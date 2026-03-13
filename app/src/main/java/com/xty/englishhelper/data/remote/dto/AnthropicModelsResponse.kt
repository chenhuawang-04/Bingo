package com.xty.englishhelper.data.remote.dto

data class AnthropicModelsResponse(
    val data: List<AnthropicModelItem> = emptyList()
)

data class AnthropicModelItem(
    val id: String = ""
)
