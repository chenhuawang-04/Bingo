package com.xty.englishhelper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedLruCacheTest {
    @Test
    fun `evicts least recently used entry and stays bounded`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        cache["a"]
        cache["c"] = 3

        assertEquals(2, cache.size())
        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }
}
