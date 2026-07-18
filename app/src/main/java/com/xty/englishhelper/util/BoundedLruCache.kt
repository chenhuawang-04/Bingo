package com.xty.englishhelper.util

class BoundedLruCache<K, V>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0)
    }

    private val values = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    operator fun get(key: K): V? = values[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        values[key] = value
    }

    @Synchronized
    fun clear() = values.clear()

    @Synchronized
    fun size(): Int = values.size
}
