package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import com.xty.englishhelper.data.json.WordPoolStrategyJsonModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryWordPoolMergePlanner @Inject constructor() {

    data class StrategySnapshot(
        val strategy: String,
        val updatedAt: Long,
        val pools: List<WordPoolJsonModel>
    ) {
        val canonicalKeys: Set<String>
            get() = pools.map(::canonicalKey).toSet()

        private fun canonicalKey(pool: WordPoolJsonModel): String {
            val focus = pool.focusWordUid.orEmpty()
            val members = pool.memberWordUids
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .joinToString(",")
            return listOf(
                strategy,
                pool.algorithmVersion,
                focus,
                members,
                pool.qualityScore
            ).joinToString("|")
        }
    }

    data class Plan(
        val cloudSnapshotsToApply: List<StrategySnapshot>
    )

    fun plan(
        localPools: List<WordPoolJsonModel>,
        cloudPools: List<WordPoolJsonModel>
    ): Plan {
        return planSnapshots(
            localByStrategy = snapshotsByStrategy(localPools),
            cloudByStrategy = snapshotsByStrategy(cloudPools)
        )
    }

    fun plan(local: DictionaryJsonModel, cloud: DictionaryJsonModel): Plan {
        return planSnapshots(
            localByStrategy = snapshotsByStrategy(local.wordPoolStrategies, local.wordPools),
            cloudByStrategy = snapshotsByStrategy(cloud.wordPoolStrategies, cloud.wordPools)
        )
    }

    private fun planSnapshots(
        localByStrategy: Map<String, StrategySnapshot>,
        cloudByStrategy: Map<String, StrategySnapshot>
    ): Plan {
        val cloudSnapshotsToApply = mutableListOf<StrategySnapshot>()

        val allStrategies = (localByStrategy.keys + cloudByStrategy.keys).sorted()
        allStrategies.forEach { strategy ->
            val local = localByStrategy[strategy]
            val cloud = cloudByStrategy[strategy]

            when {
                local == null && cloud != null -> cloudSnapshotsToApply += cloud
                local != null && cloud == null -> Unit
                local != null && cloud != null -> {
                    if (local.canonicalKeys == cloud.canonicalKeys) {
                        if (cloud.updatedAt > local.updatedAt) cloudSnapshotsToApply += cloud
                        return@forEach
                    }

                    when {
                        cloud.updatedAt > local.updatedAt -> cloudSnapshotsToApply += cloud
                        cloud.updatedAt < local.updatedAt -> Unit
                        cloud.updatedAt > 0L && local.updatedAt > 0L -> {
                            throw IllegalStateException(
                                "词池策略 $strategy 在本地和云端同时存在不同内容，且更新时间相同，无法安全同步。请在一端重建词池后重试。"
                            )
                        }
                        cloud.canonicalKeys.containsAll(local.canonicalKeys) -> cloudSnapshotsToApply += cloud
                        local.canonicalKeys.containsAll(cloud.canonicalKeys) -> Unit
                        else -> {
                            throw IllegalStateException(
                                "词池策略 $strategy 在本地和云端同时存在不同内容，且缺少可判定的新旧顺序，无法安全同步。请在一端重建词池后重试。"
                            )
                        }
                    }
                }
            }
        }

        return Plan(cloudSnapshotsToApply = cloudSnapshotsToApply)
    }

    private fun snapshotsByStrategy(pools: List<WordPoolJsonModel>): Map<String, StrategySnapshot> {
        return pools
            .map(::normalizePool)
            .groupBy { it.strategy }
            .mapValues { (strategy, groupedPools) ->
                StrategySnapshot(
                    strategy = strategy,
                    updatedAt = groupedPools.maxOfOrNull { it.updatedAt } ?: 0L,
                    pools = groupedPools
                )
            }
    }

    private fun snapshotsByStrategy(
        snapshots: List<WordPoolStrategyJsonModel>,
        legacyPools: List<WordPoolJsonModel>
    ): Map<String, StrategySnapshot> {
        if (snapshots.isEmpty()) return snapshotsByStrategy(legacyPools)
        val byStrategy = linkedMapOf<String, StrategySnapshot>()
        snapshots.forEach { snapshot ->
            val strategy = snapshot.strategy.ifBlank { "BALANCED" }
            require(byStrategy[strategy] == null) { "词池策略快照重复：$strategy" }
            val pools = snapshot.pools.map { pool ->
                normalizePool(pool.copy(strategy = strategy))
            }
            byStrategy[strategy] = StrategySnapshot(
                strategy = strategy,
                updatedAt = snapshot.updatedAt.takeIf { it > 0 }
                    ?: pools.maxOfOrNull { it.updatedAt }
                    ?: 0L,
                pools = pools
            )
        }
        return byStrategy
    }

    private fun normalizePool(pool: WordPoolJsonModel): WordPoolJsonModel {
        val strategy = pool.strategy.ifBlank { "BALANCED" }
        return pool.copy(
            strategy = strategy,
            memberWordUids = pool.memberWordUids.filter { it.isNotBlank() }.distinct()
        )
    }
}
