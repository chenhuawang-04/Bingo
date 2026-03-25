package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.WordPoolJsonModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryWordPoolMergePlannerTest {

    private val planner = DictionaryWordPoolMergePlanner()

    @Test
    fun `plan applies cloud strategy when cloud snapshot is newer`() {
        val local = listOf(pool(strategy = "BALANCED", members = listOf("a", "b"), updatedAt = 100))
        val cloud = listOf(pool(strategy = "BALANCED", members = listOf("a", "c"), updatedAt = 200))

        val plan = planner.plan(localPools = local, cloudPools = cloud)

        assertEquals(listOf("BALANCED"), plan.cloudSnapshotsToApply.map { it.strategy })
    }

    @Test
    fun `plan keeps local strategy when local snapshot is newer`() {
        val local = listOf(pool(strategy = "BALANCED", members = listOf("a", "b"), updatedAt = 300))
        val cloud = listOf(pool(strategy = "BALANCED", members = listOf("a", "c"), updatedAt = 200))

        val plan = planner.plan(localPools = local, cloudPools = cloud)

        assertTrue(plan.cloudSnapshotsToApply.isEmpty())
    }

    @Test
    fun `plan uses superset when legacy snapshots lack timestamps`() {
        val local = listOf(pool(strategy = "BALANCED", members = listOf("a", "b"), updatedAt = 0))
        val cloud = listOf(
            pool(strategy = "BALANCED", members = listOf("a", "b"), updatedAt = 0),
            pool(strategy = "BALANCED", members = listOf("c", "d"), updatedAt = 0)
        )

        val plan = planner.plan(localPools = local, cloudPools = cloud)

        assertEquals(1, plan.cloudSnapshotsToApply.size)
        assertEquals(2, plan.cloudSnapshotsToApply.single().pools.size)
    }

    @Test
    fun `plan rejects ambiguous legacy conflicts`() {
        val local = listOf(pool(strategy = "BALANCED", members = listOf("a", "b"), updatedAt = 0))
        val cloud = listOf(pool(strategy = "BALANCED", members = listOf("c", "d"), updatedAt = 0))

        val error = runCatching { planner.plan(localPools = local, cloudPools = cloud) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("缺少可判定的新旧顺序") == true)
    }

    private fun pool(
        strategy: String,
        members: List<String>,
        updatedAt: Long
    ): WordPoolJsonModel {
        return WordPoolJsonModel(
            focusWordUid = null,
            memberWordUids = members,
            strategy = strategy,
            algorithmVersion = "${strategy}_v1",
            updatedAt = updatedAt
        )
    }
}
