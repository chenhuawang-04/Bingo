package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.BrainstormDailyGoalEntity
import com.xty.englishhelper.data.local.entity.BrainstormSettingsEntity

@Dao
interface BrainstormDao {
    // Daily goal
    @Query("SELECT * FROM brainstorm_daily_goal WHERE date = :date")
    suspend fun getDailyGoal(date: String): BrainstormDailyGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyGoal(goal: BrainstormDailyGoalEntity)

    @Query("UPDATE brainstorm_daily_goal SET total_learned = :total, due_words_learned = :due, new_words_learned = :newW, updated_at = :now WHERE date = :date")
    suspend fun updateProgress(date: String, total: Int, due: Int, newW: Int, now: Long)

    @Query("UPDATE brainstorm_daily_goal SET is_completed = 1, completed_at = :now, updated_at = :now WHERE date = :date")
    suspend fun markCompleted(date: String, now: Long)

    @Query("UPDATE brainstorm_daily_goal SET is_completed = 1, continued_after_goal = 1, completed_at = :now, updated_at = :now WHERE date = :date")
    suspend fun markCompletedAndContinued(date: String, now: Long)

    // Settings
    @Query("SELECT * FROM brainstorm_settings WHERE id = 1")
    suspend fun getSettings(): BrainstormSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: BrainstormSettingsEntity)
}
