package com.xty.englishhelper.domain.background

import com.xty.englishhelper.domain.model.AppUpdateCheckPayload
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.BackgroundTaskVisibility
import com.xty.englishhelper.domain.model.isHiddenByDefault
import com.xty.englishhelper.domain.model.visibility
import com.xty.englishhelper.domain.model.ArticleAdvancedScorePayload
import com.xty.englishhelper.domain.model.AutoPaperSelectPayload
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

    @Test
    fun `v10 ai tasks declare bounded network cpu and memory demand`() {
        val advanced = BackgroundTask(
            id = 2,
            type = BackgroundTaskType.ARTICLE_ADVANCED_SCORE,
            status = BackgroundTaskStatus.PENDING,
            payload = ArticleAdvancedScorePayload(1, 75, 300, 600),
            progressCurrent = 0,
            progressTotal = 0,
            progressMessage = null,
            attempt = 0,
            errorMessage = null,
            createdAt = 1,
            updatedAt = 1,
            dedupeKey = "advanced"
        )
        val selection = advanced.copy(
            id = 3,
            type = BackgroundTaskType.AUTO_PAPER_SELECT,
            payload = AutoPaperSelectPayload(1, "paper", "2026-01-01", "ENGLISH_ONE", "PARAGRAPH_ORDER", 75, 300, 600)
        )

        assertEquals(TaskResourceDemand(network = 1, memoryHeavy = 1), advanced.resourceDemand())
        assertEquals(TaskResourceDemand(network = 1, databaseWriter = 1), selection.resourceDemand())
    }
}
