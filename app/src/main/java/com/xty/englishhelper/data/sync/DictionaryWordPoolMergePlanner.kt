package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.WordPoolJsonModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryWordPoolMergePlanner @Inject constructor() {

    data class StrategySnapshot(
        val strategy: String,
        val pools: List<WordPoolJsonModel>
    ) {
        val updatedAt: Long
            get() = pools.maxOfOrNull { it.updatedAt } ?: 0L

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
                members
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
        val localByStrategy = snapshotsByStrategy(localPools)
        val cloudByStrategy = snapshotsByStrategy(cloudPools)
        val cloudSnapshotsToApply = mutableListOf<StrategySnapshot>()

        val allStrategies = (localByStrategy.keys + cloudByStrategy.keys).sorted()
        allStrategies.forEach { strategy ->
            val local = localByStrategy[strategy]
            val cloud = cloudByStrategy[strategy]

            when {
                local == null && cloud != null -> cloudSnapshotsToApply += cloud
                local != null && cloud == null -> Unit
                local != null && cloud != null -> {
                    if (local.canonicalKeys == cloud.canonicalKeys) return@forEach

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
                StrategySnapshot(strategy = strategy, pools = groupedPools)
            }
    }

    private fun normalizePool(pool: WordPoolJsonModel): WordPoolJsonModel {
        val strategy = pool.strategy.ifBlank { "BALANCED" }
        return pool.copy(
            strategy = strategy,
            memberWordUids = pool.memberWordUids.filter { it.isNotBlank() }.distinct()
        )
    }
}
