package com.xty.englishhelper.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.WordEntity

data class WordWithDetails(
    @Embedded
    val word: WordEntity,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val synonyms: List<SynonymEntity>,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val similarWords: List<SimilarWordEntity>,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val cognates: List<CognateEntity>
)
