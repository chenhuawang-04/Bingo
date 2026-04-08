package com.xty.englishhelper.data.remote.oed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface OedWafSolver {
    suspend fun ensureCookie(seedUrl: String): String?
}

@Singleton
class OedAwsWafWebViewSolver @Inject constructor(
    @ApplicationContext private val context: Context
) : OedWafSolver {

    private val solveMutex = Mutex()

    override suspend fun ensureCookie(seedUrl: String): String? = solveMutex.withLock {
        withContext(Dispatchers.Main) {
            val initialCookie = CookieManager.getInstance().getCookie(OED_BASE)
            if (containsAwsWafCookie(initialCookie)) return@withContext initialCookie

            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                val timeoutHandler = Handler(Looper.getMainLooper())
                var finished = false

                fun complete(cookie: String?) {
                    if (finished) return
                    finished = true
                    timeoutHandler.removeCallbacksAndMessages(null)
                    webView.stopLoading()
                    webView.destroy()
                    if (continuation.isActive) continuation.resume(cookie)
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = BROWSER_UA

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val cookie = cookieManager.getCookie(OED_BASE)
                        if (containsAwsWafCookie(cookie)) {
                            complete(cookie)
                        }
                    }
                }

                timeoutHandler.postDelayed({
                    val cookie = cookieManager.getCookie(OED_BASE)
                    complete(cookie)
                }, TIMEOUT_MS)

                webView.loadUrl(seedUrl)
                continuation.invokeOnCancellation {
                    timeoutHandler.post { complete(null) }
                }
            }
        }
    }

    private fun containsAwsWafCookie(cookieHeader: String?): Boolean {
        if (cookieHeader.isNullOrBlank()) return false
        val lower = cookieHeader.lowercase()
        return "aws-waf-token" in lower || "awswaf" in lower
    }

    private companion object {
        private const val OED_BASE = "https://www.oed.com/"
        private const val TIMEOUT_MS = 12_000L
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
    }
}
