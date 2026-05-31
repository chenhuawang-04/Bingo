package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brainstorm_daily_goal")
data class BrainstormDailyGoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "date") val date: String,           // "2026-05-31"
    @ColumnInfo(name = "target_count") val targetCount: Int = 200,
    @ColumnInfo(name = "total_learned") val totalLearned: Int = 0,
    @ColumnInfo(name = "due_words_learned") val dueWordsLearned: Int = 0,
    @ColumnInfo(name = "new_words_learned") val newWordsLearned: Int = 0,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "continued_after_goal") val continuedAfterGoal: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
