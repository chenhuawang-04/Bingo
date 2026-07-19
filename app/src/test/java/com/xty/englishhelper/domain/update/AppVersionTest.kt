package com.xty.englishhelper.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `numeric versions compare by component instead of text`() {
        assertTrue(isNewerVersion("v8.10.0", "8.9.9"))
        assertFalse(isNewerVersion("8.1.4", "8.1.4"))
        assertFalse(isNewerVersion("8.1.3", "8.1.4"))
    }

    @Test
    fun `stable release is newer than prerelease with same numbers`() {
        assertTrue(isNewerVersion("8.2.0", "8.2.0-rc.1"))
        assertFalse(isNewerVersion("8.2.0-beta.2", "8.2.0"))
    }

    @Test
    fun `prerelease identifiers use semantic ordering`() {
        assertTrue(isNewerVersion("8.2.0-rc.2", "8.2.0-rc.1"))
        assertTrue(isNewerVersion("8.2.0-beta", "8.1.9"))
    }

    @Test
    fun `invalid tags never produce false update prompts`() {
        assertFalse(isNewerVersion("nightly", "8.1.4"))
        assertFalse(isNewerVersion("8.2.0", "development"))
    }
}
