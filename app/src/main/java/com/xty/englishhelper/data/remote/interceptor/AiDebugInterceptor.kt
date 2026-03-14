package com.xty.englishhelper.data.remote.interceptor

import com.xty.englishhelper.data.debug.AiDebugEvent
import com.xty.englishhelper.data.debug.AiDebugManager
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class AiDebugInterceptor @Inject constructor(
    private val debugManager: AiDebugManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!debugManager.isEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val requestJson = readRequestBody(request.body)
        return try {
            val response = chain.proceed(request)
            val responseJson = readResponseBody(response)
            debugManager.emit(
                AiDebugEvent(
                    url = request.url.toString(),
                    method = request.method,
                    statusCode = response.code,
                    requestJson = requestJson,
                    responseJson = responseJson
                )
            )
            response
        } catch (e: Exception) {
            debugManager.emit(
                AiDebugEvent(
                    url = request.url.toString(),
                    method = request.method,
                    statusCode = -1,
                    requestJson = requestJson,
                    responseJson = """{"error":"${e.message ?: "unknown"}"}"""
                )
            )
            throw e
        }
    }

    private fun readRequestBody(body: okhttp3.RequestBody?): String {
        if (body == null) return ""
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readString(StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun readResponseBody(response: Response): String {
        return try {
            val peeked = response.peekBody(Long.MAX_VALUE)
            peeked.string()
        } catch (_: Exception) {
            ""
        }
    }
}
