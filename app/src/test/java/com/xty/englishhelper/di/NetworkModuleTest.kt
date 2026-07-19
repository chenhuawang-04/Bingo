package com.xty.englishhelper.di

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `shared network dispatcher enforces process wide limits`() {
        val dispatcher = NetworkModule.provideNetworkDispatcher()

        assertEquals(8, dispatcher.maxRequests)
        assertEquals(4, dispatcher.maxRequestsPerHost)
    }
}
