package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.xty.englishhelper.data.local.entity.WordStudyStateEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails

@Dao
interface StudyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyState(state: WordStudyStateEntity)

    @Query("SELECT * FROM word_study_state WHERE word_id = :wordId")
    suspend fun getStudyState(wordId: Long): WordStudyStateEntity?

    @Query("DELETE FROM word_study_state WHERE word_id = :wordId")
    suspend fun deleteStudyState(wordId: Long)

    /**
     * Get due words for given unit IDs: words that have a study state with due <= now.
     */
    @Transaction
    @Query(
        """
        SELECT DISTINCT w.* FROM words w
        INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
        INNER JOIN word_study_state s ON w.id = s.word_id
        WHERE ref.unit_id IN (:unitIds)
          AND s.due <= :now
        ORDER BY s.due ASC
        """
    )
    suspend fun getDueWords(unitIds: List<Long>, now: Long): List<WordWithDetails>

    /**
     * Get new words for given unit IDs: words that have NO study state yet.
     */
    @Transaction
    @Query(
        """
        SELECT DISTINCT w.* FROM words w
        INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
        LEFT JOIN word_study_state s ON w.id = s.word_id
        WHERE ref.unit_id IN (:unitIds)
          AND s.word_id IS NULL
        ORDER BY w.spelling ASC
        """
    )
    suspend fun getNewWords(unitIds: List<Long>): List<WordWithDetails>

    /**
     * Count due words for a single unit.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT w.id) FROM words w
        INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
        INNER JOIN word_study_state s ON w.id = s.word_id
        WHERE ref.unit_id = :unitId
          AND s.due <= :now
        """
    )
    suspend fun countDueWords(unitId: Long, now: Long): Int

    /**
     * Count new words (no study state) for a single unit.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT w.id) FROM words w
        INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
        LEFT JOIN word_study_state s ON w.id = s.word_id
        WHERE ref.unit_id = :unitId
          AND s.word_id IS NULL
        """
    )
    suspend fun countNewWords(unitId: Long): Int

    /**
     * Get all study states for words in given units (for export).
     */
    @Query(
        """
        SELECT s.* FROM word_study_state s
        INNER JOIN unit_word_cross_ref ref ON s.word_id = ref.word_id
        WHERE ref.unit_id IN (:unitIds)
        """
    )
    suspend fun getStudyStatesForUnits(unitIds: List<Long>): List<WordStudyStateEntity>

    /**
     * Get study states for words in a dictionary (for export).
     */
    @Query(
        """
        SELECT s.* FROM word_study_state s
        INNER JOIN words w ON s.word_id = w.id
        WHERE w.dictionary_id = :dictionaryId
        """
    )
    suspend fun getStudyStatesForDictionary(dictionaryId: Long): List<WordStudyStateEntity>

    /**
     * Count all due words globally.
     */
    @Query("SELECT COUNT(*) FROM word_study_state WHERE due <= :now")
    suspend fun countAllDueWords(now: Long): Int

    /**
     * Count words reviewed today (between todayStart and now).
     */
    @Query("SELECT COUNT(*) FROM word_study_state WHERE last_review_at BETWEEN :todayStart AND :now")
    suspend fun countReviewedToday(todayStart: Long, now: Long): Int

    /**
     * Get all study states with stability > 0 (actively learned words).
     */
    @Query("SELECT * FROM word_study_state WHERE stability > 0")
    suspend fun getAllActiveStudyStates(): List<WordStudyStateEntity>
}
