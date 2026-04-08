package com.xty.englishhelper.domain.usecase.dictionary

import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.repository.CloudWordExampleRepository
import javax.inject.Inject

class GetCloudWordExamplesUseCase @Inject constructor(
    private val repository: CloudWordExampleRepository
) {
    suspend operator fun invoke(
        word: String,
        source: CloudExampleSource,
        limit: Int = 8
    ): List<CloudWordExample> {
        return repository.getExamples(word = word, source = source, limit = limit)
    }
}
