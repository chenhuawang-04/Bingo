package com.xty.englishhelper.data.remote.dto

data class OpenAiModelsResponse(
    val data: List<OpenAiModelItem> = emptyList()
)

data class OpenAiModelItem(
    val id: String = ""
)
