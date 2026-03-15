package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_items",
    foreignKeys = [
        ForeignKey(
            entity = QuestionGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("question_group_id")
    ]
)
data class QuestionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "question_group_id")
    val questionGroupId: Long,
    @ColumnInfo(name = "question_number")
    val questionNumber: Int,
    @ColumnInfo(name = "question_text")
    val questionText: String,
    @ColumnInfo(name = "option_a")
    val optionA: String? = null,
    @ColumnInfo(name = "option_b")
    val optionB: String? = null,
    @ColumnInfo(name = "option_c")
    val optionC: String? = null,
    @ColumnInfo(name = "option_d")
    val optionD: String? = null,
    @ColumnInfo(name = "correct_answer")
    val correctAnswer: String? = null,
    @ColumnInfo(name = "answer_source", defaultValue = "NONE")
    val answerSource: String = "NONE",
    val explanation: String? = null,
    @ColumnInfo(name = "order_in_group", defaultValue = "0")
    val orderInGroup: Int = 0,
    @ColumnInfo(name = "word_count", defaultValue = "0")
    val wordCount: Int = 0,
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: String? = null,
    @ColumnInfo(name = "difficulty_score")
    val difficultyScore: Float? = null,
    @ColumnInfo(name = "wrong_count", defaultValue = "0")
    val wrongCount: Int = 0,
    @ColumnInfo(name = "extra_data")
    val extraData: String? = null,
    @ColumnInfo(name = "sample_source_title")
    val sampleSourceTitle: String? = null,
    @ColumnInfo(name = "sample_source_url")
    val sampleSourceUrl: String? = null,
    @ColumnInfo(name = "sample_source_info")
    val sampleSourceInfo: String? = null
)
