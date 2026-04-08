package com.xty.englishhelper.data.remote.oed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named

class OedServiceImpl @Inject constructor(
    @Named("oed") private val client: OkHttpClient,
    private val wafSolver: OedWafSolver
) : OedService {

    override suspend fun fetchSearchHtml(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.oed.com/search/dictionary/?scope=Entries&q=$encoded"
        return fetch(url, referer = "https://www.oed.com/")
    }

    override suspend fun fetchEntryHtml(pathOrUrl: String): String {
        val url = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            "https://www.oed.com${pathOrUrl.trim()}"
        }
        return fetch(url, referer = "https://www.oed.com/")
    }

    private suspend fun fetch(url: String, referer: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val cookie = wafSolver.ensureCookie(referer).takeIf { !it.isNullOrBlank() }
            val request = buildRequest(
                url = url,
                referer = referer,
                noCache = attempt > 0,
                cookie = cookie
            )
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responsePreview = response.peekBody(2048).string()
                        if (looksLikeBotChallenge(responsePreview, response.headers)) {
                            lastError = OedFetchException(
                                "Oxford 请求被站点风控拦截（HTTP ${response.code}），正在尝试获取挑战令牌",
                                retryable = true
                            )
                            if (attempt < MAX_ATTEMPTS - 1) {
                                wafSolver.ensureCookie(url)
                                delay(RETRY_BACKOFF_MS[attempt])
                                return@repeat
                            }
                            throw lastError!!
                        }
                        throw OedFetchException("HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body?.string() ?: throw OedFetchException("Empty response body")
                    if (looksLikeBotChallenge(body, response.headers)) {
                        lastError = OedFetchException(
                            "Oxford 返回了验证/挑战页面，正在尝试获取挑战令牌",
                            retryable = true
                        )
                        if (attempt < MAX_ATTEMPTS - 1) {
                            wafSolver.ensureCookie(url)
                            delay(RETRY_BACKOFF_MS[attempt])
                            return@repeat
                        }
                        throw lastError!!
                    }
                    return@withContext body
                }
            } catch (err: OedFetchException) {
                lastError = err
                if (!err.retryable || attempt == MAX_ATTEMPTS - 1) throw err
            } catch (io: IOException) {
                lastError = OedFetchException(
                    message = "Oxford 连接被关闭或中断：${io.message ?: io.javaClass.simpleName}",
                    cause = io,
                    retryable = true
                )
                if (attempt == MAX_ATTEMPTS - 1) throw lastError!!
            }
            delay(RETRY_BACKOFF_MS[attempt])
        }
        throw lastError ?: OedFetchException("Oxford 请求失败（未知错误）")
    }

    private fun buildRequest(url: String, referer: String, noCache: Boolean, cookie: String?): Request {
        val fetchSite = if (referer.contains("www.oed.com")) "same-origin" else "none"
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
            )
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Site", fetchSite)
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Dest", "document")
        if (!cookie.isNullOrBlank()) {
            builder.header("Cookie", cookie)
        }
        if (noCache) {
            builder.header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
        }
        return builder.build()
    }

    private fun looksLikeBotChallenge(html: String, headers: Headers): Boolean {
        val content = html.lowercase()
        val server = headers["server"]?.lowercase().orEmpty()
        val wafAction = headers["x-amzn-waf-action"]?.lowercase().orEmpty()
        return wafAction == "challenge" ||
            server.contains("cloudflare") ||
            content.contains("cf-browser-verification") ||
            content.contains("/cdn-cgi/challenge-platform") ||
            (content.contains("cloudflare") && content.contains("just a moment")) ||
            content.contains("captcha") ||
            content.contains("attention required") ||
            content.contains("awswafintegration")
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val RETRY_BACKOFF_MS = longArrayOf(250L, 800L, 0L)
    }
}

class OedFetchException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false
) : Exception(message, cause)
