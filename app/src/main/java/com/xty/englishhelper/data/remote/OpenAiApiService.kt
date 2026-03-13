package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.OpenAiModelsResponse
import com.xty.englishhelper.data.remote.dto.OpenAiRequest
import com.xty.englishhelper.data.remote.dto.OpenAiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAiApiService {
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiRequest
    ): OpenAiResponse

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): OpenAiModelsResponse
}
