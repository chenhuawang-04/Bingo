package com.xty.englishhelper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.DictionaryEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.WordEntity

@Database(
    entities = [
        DictionaryEntity::class,
        WordEntity::class,
        SynonymEntity::class,
        SimilarWordEntity::class,
        CognateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun wordDao(): WordDao
}
