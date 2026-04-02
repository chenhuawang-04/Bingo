package com.xty.englishhelper.ui.screen.plan

import com.xty.englishhelper.domain.model.PlanAutoEventLog
import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.PlanDayRecord
import com.xty.englishhelper.domain.model.PlanDaySummary
import com.xty.englishhelper.domain.model.PlanItem
import com.xty.englishhelper.domain.model.PlanStatsMode
import com.xty.englishhelper.domain.model.PlanTaskProgress
import com.xty.englishhelper.domain.model.PlanTaskType
import com.xty.englishhelper.domain.model.PlanTemplate
import com.xty.englishhelper.domain.model.PlanTypeSummary
import com.xty.englishhelper.domain.repository.PlanRepository
import com.xty.englishhelper.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelStatsModeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `stats mode switch reloads summaries with selected mode`() = runTest {
        val repo = FakePlanRepository()
        val viewModel = PlanViewModel(repo)

        advanceUntilIdle()
        assertEquals(listOf(PlanStatsMode.ALL), repo.daySummaryModeCalls)
        assertEquals(listOf(PlanStatsMode.ALL), repo.typeSummaryModeCalls)

        viewModel.setStatsMode(PlanStatsMode.AUTO)
        advanceUntilIdle()

        assertEquals(listOf(PlanStatsMode.ALL, PlanStatsMode.AUTO), repo.daySummaryModeCalls)
        assertEquals(listOf(PlanStatsMode.ALL, PlanStatsMode.AUTO), repo.typeSummaryModeCalls)
        assertEquals(PlanStatsMode.AUTO, viewModel.uiState.value.statsMode)
        assertEquals(2, viewModel.uiState.value.daySummaries.first().totalCount)
        assertEquals(2, viewModel.uiState.value.typeSummaries.first().totalCount)

        viewModel.setStatsMode(PlanStatsMode.MANUAL)
        advanceUntilIdle()

        assertEquals(listOf(PlanStatsMode.ALL, PlanStatsMode.AUTO, PlanStatsMode.MANUAL), repo.daySummaryModeCalls)
        assertEquals(listOf(PlanStatsMode.ALL, PlanStatsMode.AUTO, PlanStatsMode.MANUAL), repo.typeSummaryModeCalls)
        assertEquals(PlanStatsMode.MANUAL, viewModel.uiState.value.statsMode)
        assertEquals(3, viewModel.uiState.value.daySummaries.first().totalCount)
        assertEquals(3, viewModel.uiState.value.typeSummaries.first().totalCount)
    }

    private class FakePlanRepository : PlanRepository {
        private val template = PlanTemplate(id = 1, name = "默认", isActive = true)
        private val item = PlanItem(
            id = 1,
            templateId = 1,
            taskType = PlanTaskType.REVIEW_DUE_WORDS,
            title = "复习",
            targetCount = 2
        )

        val daySummaryModeCalls = mutableListOf<PlanStatsMode>()
        val typeSummaryModeCalls = mutableListOf<PlanStatsMode>()

        override fun observeTemplates(): Flow<List<PlanTemplate>> = flowOf(listOf(template))

        override fun observeActiveTemplate(): Flow<PlanTemplate?> = flowOf(template)

        override fun observeItemsByTemplate(templateId: Long): Flow<List<PlanItem>> = flowOf(listOf(item))

        override fun observeTodayTasks(dayStart: Long): Flow<List<PlanTaskProgress>> = flowOf(
            listOf(
                PlanTaskProgress(
                    item = item,
                    record = PlanDayRecord(
                        id = 1,
                        dayStart = dayStart,
                        itemId = 1,
                        doneCount = 1,
                        isCompleted = false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            )
        )

        override fun observeTodayEventLogs(dayStart: Long, limit: Int): Flow<List<PlanAutoEventLog>> =
            flowOf(
                listOf(
                    PlanAutoEventLog(
                        id = 1,
                        dayStart = dayStart,
                        eventKey = "article_open:2026-04-02:10",
                        source = PlanAutoSource.ARTICLE_OPEN,
                        createdAt = System.currentTimeMillis()
                    )
                )
            )

        override fun observeDaySummaries(limitDays: Int, mode: PlanStatsMode): Flow<List<PlanDaySummary>> {
            daySummaryModeCalls += mode
            val total = when (mode) {
                PlanStatsMode.ALL -> 1
                PlanStatsMode.AUTO -> 2
                PlanStatsMode.MANUAL -> 3
            }
            return flowOf(listOf(PlanDaySummary(dayStart = 0, completedCount = 1, totalCount = total)))
        }

        override fun observeTypeSummaries(limitDays: Int, mode: PlanStatsMode): Flow<List<PlanTypeSummary>> {
            typeSummaryModeCalls += mode
            val total = when (mode) {
                PlanStatsMode.ALL -> 1
                PlanStatsMode.AUTO -> 2
                PlanStatsMode.MANUAL -> 3
            }
            return flowOf(listOf(PlanTypeSummary(taskType = PlanTaskType.REVIEW_DUE_WORDS, completedCount = 1, totalCount = total)))
        }

        override suspend fun ensureDefaultTemplate() = Unit

        override suspend fun setActiveTemplate(templateId: Long) = Unit

        override suspend fun createTemplate(name: String): Long = 2

        override suspend fun renameTemplate(templateId: Long, name: String) = Unit

        override suspend fun deleteTemplate(templateId: Long) = Unit

        override suspend fun addItem(item: PlanItem): Long = 1

        override suspend fun updateItem(item: PlanItem) = Unit

        override suspend fun deleteItem(itemId: Long) = Unit

        override suspend fun ensureDayRecords(dayStart: Long) = Unit

        override suspend fun updateTaskProgress(dayStart: Long, itemId: Long, doneCount: Int) = Unit

        override suspend fun setTaskCompleted(dayStart: Long, itemId: Long, completed: Boolean) = Unit

        override suspend fun consumeAutoProgress(source: PlanAutoSource, eventKey: String, delta: Int): Boolean = true
    }
}
