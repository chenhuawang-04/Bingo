package com.xty.englishhelper.data.local.converter

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val type = Types.newParameterizedType(List::class.java, MeaningJson::class.java)
    private val adapter = moshi.adapter<List<MeaningJson>>(type)

    @TypeConverter
    fun fromMeaningsJson(json: String): List<MeaningJson> {
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun toMeaningsJson(meanings: List<MeaningJson>): String {
        return adapter.toJson(meanings)
    }
}

data class MeaningJson(
    val pos: String = "",
    val definition: String = ""
)
