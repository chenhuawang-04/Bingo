package com.xty.englishhelper.domain.model

data class PoolHealthReport(
    val dictionaryId: Long,
    val strategy: PoolStrategy,
    val existingPoolCount: Int,
    val plannedPoolCount: Int,
    val existingCoveredWordCount: Int,
    val expectedCoveredWordCount: Int,
    val validEdgeCount: Int,
    val connectedComponentCount: Int,
    val oversizedComponentCount: Int,
    val invalidSizePoolCount: Int,
    val disconnectedPoolCount: Int,
    val uncoveredWordCount: Int,
    val extraneousMemberCount: Int,
    val orphanEdgeCount: Int,
    val selfLoopEdgeCount: Int,
    val unknownTypeEdgeCount: Int,
    val layoutMismatch: Boolean,
    val invalidFocusPoolCount: Int = 0,
    val missingSupportingEdgePoolCount: Int = 0,
    val storedEdgeCount: Int = validEdgeCount,
    val duplicatePoolCount: Int = 0
) {
    val arePersistedPoolsHealthy: Boolean
        get() = invalidSizePoolCount == 0 &&
            disconnectedPoolCount == 0 &&
            uncoveredWordCount == 0 &&
            extraneousMemberCount == 0 &&
            invalidFocusPoolCount == 0 &&
            missingSupportingEdgePoolCount == 0 &&
            duplicatePoolCount == 0

    val isHealthy: Boolean
        get() = arePersistedPoolsHealthy &&
            orphanEdgeCount == 0 &&
            selfLoopEdgeCount == 0 &&
            unknownTypeEdgeCount == 0

    val canRepairFromExistingEdges: Boolean
        get() = storedEdgeCount > 0 && missingSupportingEdgePoolCount == 0
}

data class PoolRepairResult(
    val before: PoolHealthReport,
    val after: PoolHealthReport,
    val replacedPoolCount: Int
)
