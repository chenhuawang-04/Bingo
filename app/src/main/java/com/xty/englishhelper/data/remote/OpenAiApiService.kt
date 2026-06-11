package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.OpenAiModelsResponse
import com.xty.englishhelper.data.remote.dto.OpenAiRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface OpenAiApiService {
    // 返回原始响应体而非反序列化对象：服务端可能以普通 JSON 或 SSE 流式（text/event-stream）
    // 返回，由 OpenAiCompatibleApiClient 自动判断并解析。@Streaming 避免流式响应被整体缓冲。
    @Streaming
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiRequest
    ): Response<ResponseBody>

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): OpenAiModelsResponse
}
