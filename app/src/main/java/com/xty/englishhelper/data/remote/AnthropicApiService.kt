package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import com.xty.englishhelper.data.remote.dto.AnthropicResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface AnthropicApiService {
    @POST
    suspend fun createMessage(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: AnthropicRequest
    ): AnthropicResponse

    @POST
    suspend fun createMultimodalMessage(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: RequestBody
    ): AnthropicResponse
}
