package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.WordEdgeJsonModel
import com.xty.englishhelper.domain.model.EdgeType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryWordEdgeMergePlanner @Inject constructor() {

    data class Plan(val cloudEdgesToApply: List<WordEdgeJsonModel>)

    fun plan(
        localEdges: List<WordEdgeJsonModel>,
        cloudEdges: List<WordEdgeJsonModel>
    ): Plan {
        val localByKey = validateSnapshot(localEdges)
        val cloudByKey = validateSnapshot(cloudEdges)
        val toApply = mutableListOf<WordEdgeJsonModel>()

        cloudByKey.forEach { (key, cloud) ->
            val local = localByKey[key]
            when {
                local == null -> toApply += cloud
                local.evidenceSource == "user_note" && cloud.evidenceSource != "user_note" -> Unit
                cloud.evidenceSource == "user_note" && local.evidenceSource != "user_note" -> toApply += cloud
                canonical(local) == canonical(cloud) && cloud.updatedAt > local.updatedAt -> toApply += cloud
                canonical(local) == canonical(cloud) -> Unit
                cloud.updatedAt > local.updatedAt -> toApply += cloud
                cloud.updatedAt < local.updatedAt -> Unit
                else -> throw IllegalStateException(
                    "词池边 ${cloud.wordUidA}/${cloud.wordUidB}/${cloud.edgeType} 在本地和云端内容冲突，且更新时间相同。"
                )
            }
        }
        return Plan(toApply.sortedBy(::key))
    }

    fun validateSnapshot(edges: List<WordEdgeJsonModel>): Map<String, WordEdgeJsonModel> {
        val byKey = linkedMapOf<String, WordEdgeJsonModel>()
        edges.forEach { edge ->
            require(edge.wordUidA.isNotBlank() && edge.wordUidB.isNotBlank()) {
                "词池边端点 wordUid 不能为空"
            }
            require(edge.wordUidA != edge.wordUidB) { "词池边不能连接同一个单词：${edge.wordUidA}" }
            require(EdgeType.fromDbValue(edge.edgeType) != null) { "未知词池边类型：${edge.edgeType}" }
            val edgeKey = key(edge)
            require(byKey.put(edgeKey, edge) == null) { "词池边快照包含重复关系：$edgeKey" }
        }
        return byKey
    }

    private fun key(edge: WordEdgeJsonModel): String {
        val a = minOf(edge.wordUidA, edge.wordUidB)
        val b = maxOf(edge.wordUidA, edge.wordUidB)
        return "$a|$b|${edge.edgeType}"
    }

    private fun canonical(edge: WordEdgeJsonModel): List<Any?> = listOf(
        minOf(edge.wordUidA, edge.wordUidB),
        maxOf(edge.wordUidA, edge.wordUidB),
        edge.edgeType,
        edge.status,
        edge.learningValue,
        edge.relationStrength,
        edge.confidence,
        edge.reason,
        edge.warningNote,
        edge.evidenceSource,
        edge.register,
        edge.exampleSentence,
        edge.difficultyCefr
    )
}
