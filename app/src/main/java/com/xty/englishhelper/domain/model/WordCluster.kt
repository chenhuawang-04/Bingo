package com.xty.englishhelper.domain.model

data class WordCluster(
    val id: Long,
    val dictionaryId: Long,
    val name: String,
    val memberCount: Int = 0
)

data class WordClusterReview(
    val cluster: WordCluster,
    val words: List<WordDetails>
)
