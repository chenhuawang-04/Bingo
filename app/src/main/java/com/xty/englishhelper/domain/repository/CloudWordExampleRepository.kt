package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample

interface CloudWordExampleRepository {
    suspend fun getExamples(
        word: String,
        source: CloudExampleSource,
        limit: Int = 8
    ): List<CloudWordExample>
}
