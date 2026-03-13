package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_group_paragraphs",
    foreignKeys = [
        ForeignKey(
            entity = QuestionGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("question_group_id"),
        Index(value = ["question_group_id", "paragraph_index"], unique = true)
    ]
)
data class QuestionGroupParagraphEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "question_group_id")
    val questionGroupId: Long,
    @ColumnInfo(name = "paragraph_index")
    val paragraphIndex: Int,
    @ColumnInfo(defaultValue = "")
    val text: String = "",
    @ColumnInfo(name = "paragraph_type", defaultValue = "TEXT")
    val paragraphType: String = "TEXT",
    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,
    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null
)
