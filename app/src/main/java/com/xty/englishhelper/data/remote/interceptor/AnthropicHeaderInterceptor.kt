package com.xty.englishhelper.data.remote.interceptor

import com.xty.englishhelper.util.Constants
import okhttp3.Interceptor
import okhttp3.Response

class AnthropicHeaderInterceptor(
    private val apiVersion: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Anthropic-Version", apiVersion)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Anthropic-Dangerous-Direct-Browser-Access", "true")
            .addHeader("User-Agent", Constants.ANTHROPIC_USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}
