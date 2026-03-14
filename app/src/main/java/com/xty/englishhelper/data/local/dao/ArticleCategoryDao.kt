package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.ArticleCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleCategoryDao {

    @Query("SELECT * FROM article_categories ORDER BY id ASC")
    fun observeAll(): Flow<List<ArticleCategoryEntity>>

    @Query("SELECT * FROM article_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ArticleCategoryEntity?

    @Query("SELECT * FROM article_categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ArticleCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ArticleCategoryEntity): Long

    @Query("DELETE FROM article_categories")
    suspend fun deleteAll()

    @Query("UPDATE article_categories SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateName(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM article_categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
