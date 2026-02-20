package com.xty.englishhelper.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class AnthropicHeaderInterceptor(
    private val apiVersion: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("anthropic-version", apiVersion)
            .addHeader("content-type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
