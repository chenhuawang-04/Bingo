package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_clusters",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("dictionary_id"),
        Index(value = ["dictionary_id", "normalized_name"], unique = true)
    ]
)
data class WordClusterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "word_cluster_members",
    primaryKeys = ["cluster_id", "word_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordClusterEntity::class,
            parentColumns = ["id"],
            childColumns = ["cluster_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("word_id")]
)
data class WordClusterMemberEntity(
    @ColumnInfo(name = "cluster_id") val clusterId: Long,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
