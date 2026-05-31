package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brainstorm_settings")
data class BrainstormSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "last_daily_target") val lastDailyTarget: Int = 200,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
