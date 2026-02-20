package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import com.xty.englishhelper.data.remote.dto.AnthropicResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface AnthropicApiService {
    @POST
    suspend fun createMessage(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Body request: AnthropicRequest
    ): AnthropicResponse
}
