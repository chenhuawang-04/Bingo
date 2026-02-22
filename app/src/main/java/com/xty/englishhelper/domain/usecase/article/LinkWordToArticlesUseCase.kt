package com.xty.englishhelper.domain.usecase.article

import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.WordExample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LinkWordToArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository
) {
    suspend operator fun invoke(
        wordId: Long,
        dictionaryId: Long,
        spelling: String,
        inflections: List<Inflection>
    ) {
        withContext(Dispatchers.Default) {
            try {
                // 先清理该词的旧链接和旧例句，确保更新词形后不残留
                repository.deleteWordLinksByWord(wordId)
                repository.deleteExamplesByWord(wordId)

                // Build normalized tokens from spelling and inflections
                val normalizedTokens = buildSet {
                    add(spelling.trim().lowercase())
                    inflections.forEach { add(it.form.trim().lowercase()) }
                }

                // Query articles that contain these tokens
                val articleIds = repository.getArticleIdsByTokens(normalizedTokens.toList())
                if (articleIds.isEmpty()) return@withContext

                // Process each article
                val wordLinks = mutableListOf<ArticleWordLink>()
                val examples = mutableListOf<WordExample>()

                for (articleId in articleIds) {
                    try {
                        val article = repository.getArticleByIdOnce(articleId) ?: continue
                        val sourceLabel = "「${article.title}」"
                        val sentences = repository.getSentences(articleId)

                        for (sentence in sentences) {
                            for (token in normalizedTokens) {
                                // Word boundary detection using regex
                                val pattern = Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)
                                val matchResult = pattern.find(sentence.text)

                                if (matchResult != null) {
                                    // Create word link
                                    wordLinks.add(
                                        ArticleWordLink(
                                            articleId = articleId,
                                            sentenceId = sentence.id,
                                            wordId = wordId,
                                            dictionaryId = dictionaryId,
                                            matchedToken = matchResult.value
                                        )
                                    )

                                    // Create word example
                                    examples.add(
                                        WordExample(
                                            wordId = wordId,
                                            sentence = sentence.text,
                                            sourceType = 1,  // Article source
                                            sourceArticleId = articleId,
                                            sourceSentenceId = sentence.id,
                                            sourceLabel = sourceLabel,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )

                                    // Only create one link per sentence
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Log article processing error but continue
                    }
                }

                // Insert links and examples (duplicates are ignored via UNIQUE constraints)
                if (wordLinks.isNotEmpty()) {
                    repository.upsertWordLinks(wordLinks)
                }
                if (examples.isNotEmpty()) {
                    repository.insertExamples(examples)
                }
            } catch (e: Exception) {
                // Linkage failure is non-critical, don't propagate
            }
        }
    }
}
