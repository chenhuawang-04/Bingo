package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_source_articles",
    foreignKeys = [
        ForeignKey(
            entity = QuestionGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["question_group_id"], unique = true)
    ]
)
data class QuestionSourceArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "question_group_id")
    val questionGroupId: Long,
    @ColumnInfo(name = "linked_article_id")
    val linkedArticleId: Long,
    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long
)
