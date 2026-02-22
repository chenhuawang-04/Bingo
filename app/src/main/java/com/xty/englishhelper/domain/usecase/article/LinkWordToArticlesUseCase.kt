package com.xty.englishhelper.domain.usecase.article

import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.WordExample
import android.util.Log
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
        withContext(Dispatchers.IO) {
            try {
                // 先清理该词的旧链接和旧例句，确保更新词形后不残留
                repository.deleteWordLinksByWord(wordId)
                repository.deleteExamplesByWord(wordId)

                // Build normalized tokens from spelling and inflections
                val normalizedTokens = buildSet {
                    add(spelling.trim().lowercase())
                    inflections.forEach { add(it.form.trim().lowercase()) }
                }

                // Pre-compile regex patterns for all tokens (outside loops for efficiency)
                val regexByToken: Map<String, Regex> = normalizedTokens.associateWith { token ->
                    Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)
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
                        val sourceLabel = "「${article.title}」例句"
                        val sentences = repository.getSentences(articleId)

                        for (sentence in sentences) {
                            for ((token, pattern) in regexByToken) {
                                // Use pre-compiled regex pattern (created once, reused many times)
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
                                            sourceType = WordExampleSourceType.ARTICLE,
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
                        Log.w("LinkWordToArticles", "Failed to process articleId=$articleId for wordId=$wordId", e)
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
                Log.w("LinkWordToArticles", "Linkage failed for wordId=$wordId", e)
            }
        }
    }
}
