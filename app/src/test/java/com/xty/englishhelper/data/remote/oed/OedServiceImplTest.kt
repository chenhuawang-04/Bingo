package com.xty.englishhelper.data.remote.oed

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OedServiceImplTest {

    @Test
    fun `fetchSearchHtml retries when connection is closed`() = runTest {
        val client = mockk<OkHttpClient>()
        val call1 = mockk<Call>()
        val call2 = mockk<Call>()
        val solver = mockk<OedWafSolver>()
        val service = OedServiceImpl(client, solver)
        val urlSlot = mutableListOf<String>()
        coEvery { solver.ensureCookie(any()) } returns null

        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            urlSlot += request.url.toString()
            if (urlSlot.size == 1) call1 else call2
        }
        every { call1.execute() } throws IOException("unexpected end of stream")
        every { call2.execute() } returns successResponse(
            url = "https://www.oed.com/search/dictionary/?scope=Entries&q=test",
            body = "<html><body>ok</body></html>"
        )

        val html = service.fetchSearchHtml("test")

        assertTrue(html.contains("ok"))
        assertEquals(2, urlSlot.size)
        assertTrue(urlSlot.first().contains("q=test"))
    }

    @Test
    fun `fetchEntryHtml throws challenge error for challenge page`() = runTest {
        val client = mockk<OkHttpClient>()
        val call1 = mockk<Call>()
        val call2 = mockk<Call>()
        val call3 = mockk<Call>()
        val solver = mockk<OedWafSolver>()
        val service = OedServiceImpl(client, solver)
        coEvery { solver.ensureCookie(any()) } returns null

        every { client.newCall(any()) } returnsMany listOf(call1, call2, call3)
        every { call1.execute() } returns successResponse(
            url = "https://www.oed.com/dictionary/test",
            body = "<html><title>Just a moment...</title><body>Cloudflare</body></html>"
        )
        every { call2.execute() } returns successResponse(
            url = "https://www.oed.com/dictionary/test",
            body = "<html><title>Just a moment...</title><body>Cloudflare</body></html>"
        )
        every { call3.execute() } returns successResponse(
            url = "https://www.oed.com/dictionary/test",
            body = "<html><title>Just a moment...</title><body>Cloudflare</body></html>"
        )

        val err = runCatching { service.fetchEntryHtml("/dictionary/test") }.exceptionOrNull()

        assertTrue(err is OedFetchException)
        assertTrue(err?.message?.isNotBlank() == true)
    }

    @Test
    fun `fetchEntryHtml throws for non-200 response`() = runTest {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val solver = mockk<OedWafSolver>()
        val service = OedServiceImpl(client, solver)
        coEvery { solver.ensureCookie(any()) } returns null

        every { client.newCall(any()) } returns call
        every { call.execute() } returns errorResponse(
            url = "https://www.oed.com/dictionary/test",
            code = 403,
            message = "Forbidden"
        )

        val err = runCatching { service.fetchEntryHtml("/dictionary/test") }.exceptionOrNull()

        assertTrue(err is OedFetchException)
        assertTrue(err?.message?.contains("HTTP 403") == true)
        verify(exactly = 1) { call.execute() }
    }

    @Test
    fun `fetchSearchHtml throws challenge error when waf header is returned`() = runTest {
        val client = mockk<OkHttpClient>()
        val call1 = mockk<Call>()
        val call2 = mockk<Call>()
        val call3 = mockk<Call>()
        val solver = mockk<OedWafSolver>()
        val service = OedServiceImpl(client, solver)
        coEvery { solver.ensureCookie(any()) } returns null

        every { client.newCall(any()) } returnsMany listOf(call1, call2, call3)
        every { call1.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://www.oed.com/search/dictionary/?scope=Entries&q=test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(202)
            .message("Accepted")
            .addHeader("x-amzn-waf-action", "challenge")
            .body("".toResponseBody("text/html".toMediaType()))
            .build()
        every { call2.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://www.oed.com/search/dictionary/?scope=Entries&q=test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(202)
            .message("Accepted")
            .addHeader("x-amzn-waf-action", "challenge")
            .body("".toResponseBody("text/html".toMediaType()))
            .build()
        every { call3.execute() } returns Response.Builder()
            .request(Request.Builder().url("https://www.oed.com/search/dictionary/?scope=Entries&q=test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(202)
            .message("Accepted")
            .addHeader("x-amzn-waf-action", "challenge")
            .body("".toResponseBody("text/html".toMediaType()))
            .build()

        val err = runCatching { service.fetchSearchHtml("test") }.exceptionOrNull()

        assertTrue(err is OedFetchException)
        assertTrue(err?.message?.isNotBlank() == true)
    }

    private fun successResponse(url: String, body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("text/html".toMediaType()))
            .build()
    }

    private fun errorResponse(url: String, code: Int, message: String): Response {
        return Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body("error".toResponseBody("text/plain".toMediaType()))
            .build()
    }
}
