package com.xty.englishhelper.domain.background

import com.xty.englishhelper.domain.model.AppUpdateCheckPayload
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.BackgroundTaskVisibility
import com.xty.englishhelper.domain.model.isHiddenByDefault
import com.xty.englishhelper.domain.model.visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTaskMetadataTest {

    @Test
    fun `update checks are hidden network-only maintenance tasks`() {
        val task = BackgroundTask(
            id = 1,
            type = BackgroundTaskType.APP_UPDATE_CHECK,
            status = BackgroundTaskStatus.PENDING,
            payload = AppUpdateCheckPayload(currentVersion = "8.1.4"),
            progressCurrent = 0,
            progressTotal = 0,
            progressMessage = null,
            attempt = 0,
            errorMessage = null,
            createdAt = 1,
            updatedAt = 1,
            dedupeKey = "system:app-update-check"
        )

        assertEquals(BackgroundTaskVisibility.HIDDEN, task.type.visibility)
        assertTrue(task.isHiddenByDefault)
        assertEquals(TaskResourceDemand(network = 1), task.resourceDemand())
    }
}
