package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.WordPhraseEntity
import com.xty.englishhelper.data.local.entity.WordPhraseOrganizeMarkEntity
import com.xty.englishhelper.data.local.entity.WordPhraseTagCrossRef
import com.xty.englishhelper.data.local.entity.WordPhraseTagEntity

@Dao
interface WordPhraseDao {

    @Query("SELECT * FROM word_phrase_tags WHERE dictionary_id = :dictionaryId ORDER BY name ASC")
    suspend fun getTagsByDictionary(dictionaryId: Long): List<WordPhraseTagEntity>

    @Query(
        """
        SELECT * FROM word_phrase_tags
        WHERE dictionary_id = :dictionaryId AND normalized_name = :normalizedName
        LIMIT 1
        """
    )
    suspend fun getTagByNormalizedName(dictionaryId: Long, normalizedName: String): WordPhraseTagEntity?

    @Query(
        """
        SELECT * FROM word_phrase_tags
        WHERE dictionary_id = :dictionaryId AND tag_uid = :tagUid
        LIMIT 1
        """
    )
    suspend fun getTagByUid(dictionaryId: Long, tagUid: String): WordPhraseTagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: WordPhraseTagEntity): Long

    @Update
    suspend fun updateTag(tag: WordPhraseTagEntity)

    @Query("SELECT * FROM word_phrases WHERE word_id = :wordId ORDER BY phrase ASC")
    suspend fun getPhrasesByWord(wordId: Long): List<WordPhraseEntity>

    @Query("SELECT * FROM word_phrases WHERE dictionary_id = :dictionaryId ORDER BY word_id ASC, phrase ASC")
    suspend fun getPhrasesByDictionary(dictionaryId: Long): List<WordPhraseEntity>

    @Query(
        """
        SELECT * FROM word_phrases
        WHERE word_id = :wordId AND normalized_phrase = :normalizedPhrase
        LIMIT 1
        """
    )
    suspend fun getPhraseByWordAndNormalized(wordId: Long, normalizedPhrase: String): WordPhraseEntity?

    @Query(
        """
        SELECT * FROM word_phrases
        WHERE dictionary_id = :dictionaryId AND phrase_uid = :phraseUid
        LIMIT 1
        """
    )
    suspend fun getPhraseByUid(dictionaryId: Long, phraseUid: String): WordPhraseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhrase(phrase: WordPhraseEntity): Long

    @Update
    suspend fun updatePhrase(phrase: WordPhraseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhraseTagCrossRef(ref: WordPhraseTagCrossRef)

    @Query(
        """
        SELECT
            r.phrase_id AS phraseId,
            t.id AS id,
            t.tag_uid AS tagUid,
            t.dictionary_id AS dictionaryId,
            t.name AS name,
            t.normalized_name AS normalizedName,
            t.description AS description,
            t.source AS source,
            t.created_at AS createdAt,
            t.updated_at AS updatedAt
        FROM word_phrase_tag_cross_refs r
        INNER JOIN word_phrase_tags t ON t.id = r.tag_id
        WHERE r.phrase_id IN (:phraseIds)
        ORDER BY t.name ASC
        """
    )
    suspend fun getTagsForPhraseIds(phraseIds: List<Long>): List<PhraseTagLinkProjection>

    @Query(
        """
        SELECT r.phrase_id AS phraseId, r.tag_id AS tagId
        FROM word_phrase_tag_cross_refs r
        INNER JOIN word_phrases p ON p.id = r.phrase_id
        WHERE p.dictionary_id = :dictionaryId
        """
    )
    suspend fun getCrossRefsByDictionary(dictionaryId: Long): List<PhraseTagRefProjection>

    @Query("SELECT COUNT(*) FROM word_phrases WHERE word_id = :wordId")
    suspend fun countPhrasesForWord(wordId: Long): Int

    @Query("SELECT COUNT(*) FROM word_phrases WHERE dictionary_id = :dictionaryId")
    suspend fun countPhrasesByDictionary(dictionaryId: Long): Int

    @Query("SELECT COUNT(*) FROM word_phrase_tags WHERE dictionary_id = :dictionaryId")
    suspend fun countTagsByDictionary(dictionaryId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT word_id FROM word_phrase_organize_marks
            WHERE dictionary_id = :dictionaryId AND status IN ('SUCCESS', 'EMPTY')
            UNION
            SELECT word_id FROM word_phrases
            WHERE dictionary_id = :dictionaryId
        )
        """
    )
    suspend fun countOrganizedWords(dictionaryId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM word_phrase_organize_marks
        WHERE word_id = :wordId AND status IN ('SUCCESS', 'EMPTY')
        """
    )
    suspend fun hasFinishedMark(wordId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrganizeMark(mark: WordPhraseOrganizeMarkEntity)

    @Query("DELETE FROM word_phrase_tag_cross_refs WHERE phrase_id IN (SELECT id FROM word_phrases WHERE dictionary_id = :dictionaryId)")
    suspend fun deleteCrossRefsByDictionary(dictionaryId: Long)

    @Query("DELETE FROM word_phrases WHERE dictionary_id = :dictionaryId")
    suspend fun deletePhrasesByDictionary(dictionaryId: Long)

    @Query("DELETE FROM word_phrase_tags WHERE dictionary_id = :dictionaryId")
    suspend fun deleteTagsByDictionary(dictionaryId: Long)

    @Query("DELETE FROM word_phrase_organize_marks WHERE dictionary_id = :dictionaryId")
    suspend fun deleteMarksByDictionary(dictionaryId: Long)
}

data class PhraseTagLinkProjection(
    val phraseId: Long,
    val id: Long,
    val tagUid: String,
    val dictionaryId: Long,
    val name: String,
    val normalizedName: String,
    val description: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class PhraseTagRefProjection(
    val phraseId: Long,
    val tagId: Long
)
