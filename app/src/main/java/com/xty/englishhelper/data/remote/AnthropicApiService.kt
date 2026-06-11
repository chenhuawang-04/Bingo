package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.AnthropicModelsResponse
import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface AnthropicApiService {
    // 返回原始响应体：普通 JSON 与 SSE 流式（text/event-stream）由 AnthropicApiClient 自动判断解析。
    @Streaming
    @POST
    suspend fun createMessage(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Body request: AnthropicRequest
    ): Response<ResponseBody>

    @Streaming
    @POST
    suspend fun createMultimodalMessage(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("x-api-key") apiKey: String
    ): AnthropicModelsResponse
}
