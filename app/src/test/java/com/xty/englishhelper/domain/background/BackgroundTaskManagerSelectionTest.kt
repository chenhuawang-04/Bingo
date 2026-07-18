package com.xty.englishhelper.domain.background

import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.WordNoteOrganizePayload
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordPoolReviewPayload
import com.xty.englishhelper.domain.model.SyncTaskPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTaskManagerSelectionTest {

    @Test
    fun `poolTaskMutexKey locks quality-first edge writers per dictionary`() {
        val reviewTask = poolTaskTask(
            id = 1L,
            type = BackgroundTaskType.WORD_POOL_REVIEW,
            payload = WordPoolReviewPayload(dictionaryId = 10L, strategy = "QUALITY_FIRST")
        )
        val qualityFirstTask = poolTaskTask(
            id = 2L,
            type = BackgroundTaskType.WORD_POOL_REBUILD,
            payload = WordPoolRebuildPayload(dictionaryId = 10L, strategy = "QUALITY_FIRST")
        )
        val balancedTask = poolTaskTask(
            id = 3L,
            type = BackgroundTaskType.WORD_POOL_REBUILD,
            payload = WordPoolRebuildPayload(dictionaryId = 10L, strategy = "BALANCED")
        )
        val noteTask = poolTaskTask(
            id = 4L,
            type = BackgroundTaskType.WORD_NOTE_ORGANIZE,
            payload = WordNoteOrganizePayload(
                dictionaryId = 10L,
                sourceWordId = 1L,
                sourceSpelling = "adapt",
                targetWordId = 2L,
                targetSpelling = "adopt"
            )
        )

        assertEquals(PoolTaskMutexKey(10L, "__EDGE_WRITE__"), poolTaskMutexKey(reviewTask))
        assertEquals(PoolTaskMutexKey(10L, "__EDGE_WRITE__"), poolTaskMutexKey(qualityFirstTask))
        assertEquals(PoolTaskMutexKey(10L, "__EDGE_WRITE__"), poolTaskMutexKey(noteTask))
        assertEquals(PoolTaskMutexKey(10L, "__EDGE_WRITE__"), poolTaskMutexKey(balancedTask))
    }

    @Test
    fun `selectLaunchablePendingTasks keeps one global memory-heavy pool task at a time`() {
        val running = listOf(
            poolTaskTask(
                id = 100L,
                type = BackgroundTaskType.WORD_POOL_REBUILD,
                status = BackgroundTaskStatus.RUNNING,
                payload = WordPoolRebuildPayload(dictionaryId = 1L, strategy = "QUALITY_FIRST")
            )
        )
        val pending = listOf(
            poolTaskTask(
                id = 1L,
                type = BackgroundTaskType.WORD_POOL_REVIEW,
                payload = WordPoolReviewPayload(dictionaryId = 1L, strategy = "QUALITY_FIRST")
            ),
            poolTaskTask(
                id = 2L,
                type = BackgroundTaskType.WORD_POOL_REVIEW,
                payload = WordPoolReviewPayload(dictionaryId = 2L, strategy = "QUALITY_FIRST")
            ),
            poolTaskTask(
                id = 3L,
                type = BackgroundTaskType.WORD_POOL_REBUILD,
                payload = WordPoolRebuildPayload(dictionaryId = 1L, strategy = "BALANCED")
            ),
            poolTaskTask(
                id = 4L,
                type = BackgroundTaskType.WORD_NOTE_ORGANIZE,
                payload = WordNoteOrganizePayload(
                    dictionaryId = 1L,
                    sourceWordId = 1L,
                    sourceSpelling = "adapt",
                    targetWordId = 2L,
                    targetSpelling = "adopt"
                )
            )
        )

        val selected = selectLaunchablePendingTasks(
            pendingTasks = pending,
            runningTasks = running,
            slots = 3
        )

        assertEquals(emptyList<Long>(), selected.map { it.id })
    }

    @Test
    fun `selectLaunchablePendingTasks prevents pending note review and rebuild from launching together`() {
        val pending = listOf(
            poolTaskTask(
                id = 11L,
                type = BackgroundTaskType.WORD_POOL_REVIEW,
                payload = WordPoolReviewPayload(dictionaryId = 5L, strategy = "QUALITY_FIRST")
            ),
            poolTaskTask(
                id = 12L,
                type = BackgroundTaskType.WORD_POOL_REBUILD,
                payload = WordPoolRebuildPayload(dictionaryId = 5L, strategy = "QUALITY_FIRST")
            ),
            poolTaskTask(
                id = 13L,
                type = BackgroundTaskType.WORD_POOL_REVIEW,
                payload = WordPoolReviewPayload(dictionaryId = 6L, strategy = "QUALITY_FIRST")
            ),
            poolTaskTask(
                id = 14L,
                type = BackgroundTaskType.WORD_NOTE_ORGANIZE,
                payload = WordNoteOrganizePayload(
                    dictionaryId = 5L,
                    sourceWordId = 8L,
                    sourceSpelling = "adapt",
                    targetWordId = 9L,
                    targetSpelling = "adapter"
                )
            )
        )

        val selected = selectLaunchablePendingTasks(
            pendingTasks = pending,
            runningTasks = emptyList(),
            slots = 3
        )

        assertEquals(listOf(11L), selected.map { it.id })
    }

    @Test
    fun `balanced and balanced ai rebuilds cannot write the same dictionary concurrently`() {
        val pending = listOf(
            poolTaskTask(
                id = 21L,
                type = BackgroundTaskType.WORD_POOL_REBUILD,
                payload = WordPoolRebuildPayload(dictionaryId = 9L, strategy = "BALANCED")
            ),
            poolTaskTask(
                id = 22L,
                type = BackgroundTaskType.WORD_POOL_REBUILD,
                payload = WordPoolRebuildPayload(dictionaryId = 9L, strategy = "BALANCED_WITH_AI")
            )
        )

        val selected = selectLaunchablePendingTasks(pending, emptyList(), slots = 2)

        assertEquals(listOf(21L), selected.map { it.id })
    }

    @Test
    fun `cloud sync runs exclusively against memory heavy tasks`() {
        val cloudSync = poolTaskTask(
            id = 31L,
            type = BackgroundTaskType.CLOUD_SYNC,
            payload = SyncTaskPayload(startedAt = 1L, syncMode = "SMART", triggeredBy = "test")
        )
        val rebuild = poolTaskTask(
            id = 32L,
            type = BackgroundTaskType.WORD_POOL_REBUILD,
            payload = WordPoolRebuildPayload(dictionaryId = 1L, strategy = "QUALITY_FIRST")
        )

        assertEquals(
            listOf(31L),
            selectLaunchablePendingTasks(listOf(cloudSync, rebuild), emptyList(), slots = 2).map { it.id }
        )
        assertEquals(
            emptyList<Long>(),
            selectLaunchablePendingTasks(listOf(cloudSync), listOf(rebuild), slots = 1).map { it.id }
        )
    }

    private fun poolTaskTask(
        id: Long,
        type: BackgroundTaskType,
        payload: Any,
        status: BackgroundTaskStatus = BackgroundTaskStatus.PENDING
    ): BackgroundTask {
        return BackgroundTask(
            id = id,
            type = type,
            status = status,
            payload = payload as? com.xty.englishhelper.domain.model.BackgroundTaskPayload,
            progressCurrent = 0,
            progressTotal = 0,
            progressMessage = null,
            attempt = 0,
            errorMessage = null,
            createdAt = 0L,
            updatedAt = id,
            dedupeKey = "task:$id"
        )
    }
}
