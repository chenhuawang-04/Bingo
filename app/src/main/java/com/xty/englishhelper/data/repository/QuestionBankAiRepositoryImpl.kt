package com.xty.englishhelper.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.repository.AnswerResult
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.ScanResult
import com.xty.englishhelper.domain.repository.ScannedQuestion
import com.xty.englishhelper.domain.repository.ScannedQuestionGroup
import com.xty.englishhelper.domain.repository.TranslationScore
import com.xty.englishhelper.domain.repository.TranslationScoreInput
import com.xty.englishhelper.domain.repository.VerifyResult
import com.xty.englishhelper.domain.repository.WritingDeduction
import com.xty.englishhelper.domain.repository.WritingPromptSourceResult
import com.xty.englishhelper.domain.repository.WritingSampleResult
import com.xty.englishhelper.domain.repository.WritingScore
import com.xty.englishhelper.domain.repository.WritingSubScores
import com.xty.englishhelper.util.AiResponseUnwrapper
import com.xty.englishhelper.util.AiJsonRepairer
import com.xty.englishhelper.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionBankAiRepositoryImpl @Inject constructor(
    private val clientProvider: AiApiClientProvider,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore
) : QuestionBankAiRepository {

    override suspend fun scanQuestions(
        images: List<ByteArray>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): ScanResult {
        val prompt = buildString {
            append("You are an exam paper OCR specialist. ")
            append("Carefully extract all content from these exam paper images.\n")
            append("Return strict JSON matching this structure:\n")
            append("""
{
  "examPaperTitle": "paper title",
  "questionGroups": [
    {
      "questionType": "READING_COMPREHENSION",
      "sectionLabel": "Text 1",
      "directions": "directions text or null",
      "passageParagraphs": ["paragraph 1", "paragraph 2"],
      "sentenceOptions": ["A. ...", "B. ..."],
      "sourceInfo": "source description or null",
      "sourceUrl": "URL you found via search, or null",
      "questions": [
        {
          "questionNumber": 21,
          "questionText": "question stem",
          "optionA": "[A] option text",
          "optionB": "[B] option text",
          "optionC": "[C] option text",
          "optionD": "[D] option text",
          "wordCount": 45,
          "difficultyLevel": "MEDIUM",
          "difficultyScore": 0.6
        }
      ],
      "wordCount": 350,
      "difficultyLevel": "MEDIUM",
      "difficultyScore": 0.65
    }
  ],
  "confidence": 0.9
}
""".trimIndent())
            append("\nRules:\n")
            append("- questionType: \"READING_COMPREHENSION\" for standard reading comprehension, \"CLOZE\" for cloze/fill-in-the-blank (e.g. Section I / Use of English), \"TRANSLATION\" for translation sections, \"WRITING\" for essay writing tasks, \"PARAGRAPH_ORDER\" for paragraph ordering (Part B: reorder paragraphs A-H into correct order), \"SENTENCE_INSERTION\" for sentence insertion, \"COMMENT_OPINION_MATCH\" for comment-opinion matching, \"SUBHEADING_MATCH\" for subheading matching, \"INFORMATION_MATCH\" for information matching.\n")
            append("- CLOZE rules: Mark blanks in passageParagraphs as __N__ where N is the exact questionNumber of the corresponding question (e.g. if questions are numbered 1-20, blanks are __1__ to __20__; if 21-40, blanks are __21__ to __40__). questionText should be empty for CLOZE questions. Options are the candidate words.\n")
            append("- TRANSLATION rules (英语一 / multiple underlined sentences): In passageParagraphs, wrap each underlined sentence with ((N))sentence text((/N)) where N = questionNumber. Each marked sentence becomes one question with questionText = the English sentence to translate. optionA/B/C/D must all be null.\n")
            append("- TRANSLATION rules (英语二 / single paragraph translation): passageParagraphs contain the full paragraph. Only 1 question, questionText = the full English text to translate. optionA/B/C/D must all be null.\n")
            append("- WRITING rules: questionText = full writing prompt/instructions. optionA/B/C/D must all be null. passageParagraphs may be empty or contain background material if provided in the paper.\n")
            append("- PARAGRAPH_ORDER rules: passageParagraphs must list paragraphs A-H in their labeled order, and each paragraph text must start with its label like \"A. ...\", \"B. ...\". directions should include the slot pattern (e.g. \"41 __ 42 __ C 43 __ H 44 __ A 45 __\") if present. Each blank is one question with questionText like \"Blank 41\". optionA/B/C/D should be empty or null.\n")
            append("- SENTENCE_INSERTION rules: passageParagraphs contain the full passage with blanks marked as __N__ where N is the blank/question number. sentenceOptions must list candidate sentences labeled A-G like \"A. ...\", \"B. ...\". Each blank is one question with questionText like \"Blank 41\". optionA/B/C/D should be empty or null.\n")
            append("- COMMENT_OPINION_MATCH rules: passageParagraphs list each comment/opinion block as a separate paragraph, starting with its numbered name if present (e.g. \"(41) Hannah ...\"). sentenceOptions must list summary statements labeled A-G. Each comment is one question with questionNumber (41-45) and questionText can be the name or empty. optionA/B/C/D should be empty or null.\n")
            append("- SUBHEADING_MATCH rules: passageParagraphs list the full text for each numbered paragraph (41-45) in order, without the heading blanks. sentenceOptions must list subheadings labeled A-G. Each paragraph is one question with questionNumber (41-45) and questionText can be the paragraph title or empty. optionA/B/C/D should be empty or null.\n")
            append("- INFORMATION_MATCH rules: passageParagraphs list the option texts labeled A-G (right column) as separate paragraphs. sentenceOptions must list the same options labeled A-G. Each matching item on the left is one question with questionNumber (41-45) and questionText as the statement to match. optionA/B/C/D should be empty or null.\n")
            append("- Transcribe passage text EXACTLY as printed, preserving all paragraphs.\n")
            append("- sourceUrl: Do NOT extract URLs from the exam paper image (exam papers never print source URLs). ")
            append("Instead, use web content to identify and confirm the original source article for each reading passage. ")
            append("If you find a likely match, provide its URL. If not, set to null.\n")
            append("- sourceInfo: a brief description of the source you found (e.g. \"The Guardian, 2024\"), or null.\n")
            append("- wordCount = word count of passage + all questions in the group.\n")
            append("- difficultyLevel: EASY/MEDIUM/HARD based on vocabulary and sentence complexity.\n")
            append("- confidence: your overall OCR confidence (0-1).\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            imageBytes = images, prompt = prompt, maxTokens = 8192
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseScanResult(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun generateQuestionsFromArticle(
        articleTitle: String,
        articleText: String,
        questionType: String,
        variant: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ScanResult {
        val prompt = buildQuestionGenerationPrompt(
            articleTitle = articleTitle,
            articleText = articleText,
            questionType = questionType,
            variant = variant
        )

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl,
            apiKey = apiKey,
            model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 8192
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseScanResult(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun verifySource(
        passageText: String, referenceUrl: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): VerifyResult {
        val userMessage = buildString {
            append("You are a source article research assistant. ")
            append("Your task is to find the original published article from which an exam reading passage was taken, ")
            append("using web content to confirm the source.\n\n")
            append("Below is a reading passage extracted from an English exam paper. ")
            append("Find the original published article that this passage comes from.\n\n")
            append("=== PASSAGE TEXT ===\n")
            append(passageText)
            append("\n=== END PASSAGE ===\n\n")
            if (referenceUrl.isNotBlank()) {
                append("A previous search suggested this URL as a possible source (use it as a hint, but do NOT limit your search to it — it may be incorrect):\n")
                append(referenceUrl)
                append("\n\n")
            }
            append("Instructions:\n")
            append("- Use web content to find the original full article.\n")
            append("- If found, return matched=true with the complete article text split into paragraphs.\n")
            append("- If the reference URL is wrong, find and return the correct URL.\n")
            append("- If you cannot find the original article at all, return matched=false with an explanation.\n\n")
            append("Return strict JSON:\n")
            append("""
{
  "matched": true,
  "errorMessage": null,
  "articleTitle": "title of the original article",
  "articleAuthor": "author name",
  "articleContent": "full article text (all paragraphs joined)",
  "articleSummary": "brief 1-2 sentence summary",
  "articleParagraphs": ["paragraph 1", "paragraph 2", "..."],
  "sourceUrl": "the actual canonical URL of the source article"
}
""".trimIndent())
            append("\nIf not found, set matched=false and explain in errorMessage. ")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 4096
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseVerifyResult(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun generateAnswers(
        passageText: String, questions: List<QuestionItem>,
        questionType: String,
        apiKey: String, model: String, baseUrl: String, providerName: String, provider: AiProvider
    ): List<AnswerResult> {
        @Suppress("UNUSED_PARAMETER") val _providerName = providerName
        val isCloze = questionType == "CLOZE"
        val isTranslation = questionType == "TRANSLATION"
        val isParagraphOrder = questionType == "PARAGRAPH_ORDER"
        val isSentenceInsertion = questionType == "SENTENCE_INSERTION"
        val isCommentOpinionMatch = questionType == "COMMENT_OPINION_MATCH"
        val isSubheadingMatch = questionType == "SUBHEADING_MATCH"
        val isInformationMatch = questionType == "INFORMATION_MATCH"
        val instruction = when {
            isCloze -> "You are an expert English cloze test solver. " +
                "Read the passage with numbered blanks (__1__, __2__, etc.) and choose the best word for each blank based on context, grammar, collocations and meaning."
            isTranslation -> "You are an expert English-to-Chinese translator for 考研英语 (postgraduate entrance exam). " +
                "Provide accurate, natural Chinese translations and key translation notes."
            isParagraphOrder -> "You are an expert at paragraph ordering questions. " +
                "The passage contains paragraphs labeled A-H. Choose the correct paragraph letter for each blank position so the text is coherent."
            isSentenceInsertion -> "You are an expert at sentence insertion questions. " +
                "Choose the correct sentence letter (A-G) for each blank so the passage reads naturally and logically."
            isCommentOpinionMatch -> "You are an expert at comment-opinion matching questions. " +
                "Match each numbered comment to the best summary statement letter (A-G) based on stance, focus, and tone."
            isSubheadingMatch -> "You are an expert at subheading matching questions. " +
                "Match each numbered paragraph to the best subheading letter (A-G) based on the main idea."
            isInformationMatch -> "You are an expert at information matching questions. " +
                "Match each numbered statement to the correct option letter (A-G) based on the key facts."
            else -> "You are an expert English exam answer generator. Read the passage and answer each multiple-choice question."
        }
        val sentenceOptions = if (isSentenceInsertion || isCommentOpinionMatch || isSubheadingMatch || isInformationMatch) {
            parseSentenceInsertionOptions(questions.firstOrNull()?.extraData)
        } else {
            emptyList()
        }
        val userMessage = buildString {
            append(instruction).append("\n\n")
            if (isTranslation) {
                append("Passage:\n$passageText\n\n")
                append("Translate the following English sentences into Chinese:\n")
                questions.forEach { q ->
                    append("${q.questionNumber}. ${q.questionText}\n")
                }
                append("\nReturn strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append("""
[
  {
    "questionNumber": $exampleNum,
    "answer": "中文参考译文",
    "explanation": "翻译要点：关键词汇、句式结构、得分点分析",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
""".trimIndent())
            } else if (isParagraphOrder) {
                append("Passage:\n$passageText\n\n")
                append("Blanks to fill (choose paragraph letter A-H for each):\n")
                questions.forEach { q ->
                    val label = if (q.questionText.isNotBlank()) q.questionText else "Blank ${q.questionNumber}"
                    append("${q.questionNumber}. $label\n")
                }
                append("\nReturn strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append(
                    """
[
  {
    "questionNumber": $exampleNum,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
                    """.trimIndent()
                )
            } else if (isSentenceInsertion) {
                append("Passage:\n$passageText\n\n")
                if (sentenceOptions.isNotEmpty()) {
                    append("Sentence options:\n")
                    sentenceOptions.forEach { option ->
                        append(option).append("\n")
                    }
                    append("\n")
                }
                append("Blanks to fill (choose sentence letter A-G for each):\n")
                questions.forEach { q ->
                    val label = if (q.questionText.isNotBlank()) q.questionText else "Blank ${q.questionNumber}"
                    append("${q.questionNumber}. $label\n")
                }
                append("\nReturn strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append(
                    """
[
  {
    "questionNumber": $exampleNum,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
                    """.trimIndent()
                )
            } else if (isCommentOpinionMatch) {
                append("Comments:\n$passageText\n\n")
                if (sentenceOptions.isNotEmpty()) {
                    append("Summary statements:\n")
                    sentenceOptions.forEach { option ->
                        append(option).append("\n")
                    }
                    append("\n")
                }
                append("Match each numbered comment to the best statement letter (A-G):\n")
                questions.forEach { q ->
                    val label = if (q.questionText.isNotBlank()) q.questionText else "Comment ${q.questionNumber}"
                    append("${q.questionNumber}. $label\n")
                }
                append("\nReturn strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append(
                    """
[
  {
    "questionNumber": $exampleNum,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
                    """.trimIndent()
                )
            } else if (isSubheadingMatch) {
                append("Paragraphs:\n$passageText\n\n")
                if (sentenceOptions.isNotEmpty()) {
                    append("Subheadings:\n")
                    sentenceOptions.forEach { option ->
                        append(option).append("\n")
                    }
                    append("\n")
                }
                append("Match each numbered paragraph to the best subheading letter (A-G):\n")
                questions.forEach { q ->
                    val label = if (q.questionText.isNotBlank()) q.questionText else "Paragraph ${q.questionNumber}"
                    append("${q.questionNumber}. $label\n")
                }
                append("\nReturn strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append(
                    """
[
  {
    "questionNumber": $exampleNum,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
                    """.trimIndent()
                )
            } else if (isInformationMatch) {
                append("Options:\n$passageText\n\n")
                if (sentenceOptions.isNotEmpty()) {
                    append("Options list:\n")
                    sentenceOptions.forEach { option ->
                        append(option).append("\n")
                    }
                    append("\n")
                }
                append("Match each numbered statement to the best option letter (A-G):\n")
                questions.forEach { q ->
                    val label = if (q.questionText.isNotBlank()) q.questionText else "Item ${q.questionNumber}"
                    append("${q.questionNumber}. $label\n")
                }
                append("\nReturn strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append(
                    """
[
  {
    "questionNumber": $exampleNum,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
                    """.trimIndent()
                )
            } else {
                append("Passage:\n$passageText\n\nQuestions:\n")
                questions.forEach { q ->
                    if (isCloze) {
                        append("${q.questionNumber}. ")
                    } else {
                        append("${q.questionNumber}. ${q.questionText}\n")
                    }
                    q.optionA?.let { append("[A] $it ") }
                    q.optionB?.let { append("[B] $it ") }
                    q.optionC?.let { append("[C] $it ") }
                    q.optionD?.let { append("[D] $it") }
                    append("\n")
                }
                append("Return strict JSON array:\n")
                val exampleNum = questions.firstOrNull()?.questionNumber ?: 1
                append("""
[
  {
    "questionNumber": $exampleNum,
    "answer": "A",
    "explanation": "brief explanation",
    "difficultyLevel": "MEDIUM",
    "difficultyScore": 0.6
  }
]
""".trimIndent())
            }
            append("\nIMPORTANT: questionNumber in your response must match the actual question numbers provided above (${questions.firstOrNull()?.questionNumber}–${questions.lastOrNull()?.questionNumber}).")
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 4096
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseAnswerResults(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun scanAnswers(
        images: List<ByteArray>, questionNumbers: List<Int>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<AnswerResult> {
        val prompt = buildString {
            append("Extract answer keys from these answer sheet images.\n")
            append("Expected question numbers: ${questionNumbers.joinToString(", ")}\n")
            append("Return strict JSON array:\n")
            append("""
[
  {"questionNumber": 21, "answer": "A", "explanation": "explanation if visible"}
]
""".trimIndent())
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            imageBytes = images, prompt = prompt, maxTokens = 2048
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseAnswerResults(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun scoreTranslations(
        items: List<TranslationScoreInput>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<TranslationScore> {
        val userMessage = buildString {
            append("You are a 考研英语翻译评分专家. ")
            append("Score each translation on a 0-2 scale based on accuracy of key terms, sentence structure, and naturalness of the Chinese expression. ")
            append("Provide specific feedback on scoring points gained and lost.\n\n")
            append("Score the following translations:\n\n")
            items.forEach { item ->
                append("--- Question ${item.questionNumber} ---\n")
                append("Original: ${item.originalText}\n")
                append("Reference: ${item.referenceTranslation}\n")
                append("Student: ${item.userTranslation}\n\n")
            }
            append("Return strict JSON array with EXACTLY ${items.size} elements, one per question above.\n")
            append("IMPORTANT: You must return a score for EVERY question number listed above (${items.joinToString(", ") { it.questionNumber.toString() }}). Do not skip any.\n")
            append("""
[
  {
    "questionNumber": ${items.firstOrNull()?.questionNumber ?: 1},
    "score": 1.5,
    "maxScore": 2,
    "feedback": "得分点：xxx；失分点：xxx"
  }
]
""".trimIndent())
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 4096
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseTranslationScores(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun searchWritingPromptSource(
        paperTitle: String,
        questionText: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): WritingPromptSourceResult {
        val prompt = buildString {
            append("你是写作题来源检索助手。请结合网络内容，找到该写作题干可能的真实来源链接。\n")
            append("不要编造链接或来源。\n\n")
            append("试卷名称：").append(paperTitle).append("\n")
            append("作文题干：").append(questionText.take(400)).append("\n\n")
            append("要求：\n")
            append("- 返回真实可访问链接与来源描述\n")
            append("- 找不到则 matched=false 并说明原因\n")
            append("- 返回严格 JSON（不要 Markdown / ``` / '''）\n\n")
            append("返回格式：\n")
            append(
                """
{
  "matched": true,
  "sourceUrl": "可访问链接",
  "sourceInfo": "网站名 + 年份/作者（可空）",
  "confidence": 0.0,
  "errorMessage": null
}
                """.trimIndent()
            )
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 2048
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseWritingPromptSource(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun searchWritingSample(
        paperTitle: String,
        questionText: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): WritingSampleResult {
        val prompt = buildString {
            append("你是写作范文检索助手。请结合网络内容，检索该作文题目的真实范文与可访问链接。\n")
            append("严禁编造范文或链接。\n\n")
            append("试卷名称：").append(paperTitle).append("\n")
            append("作文题干：").append(questionText.take(400)).append("\n\n")
            append("要求：\n")
            append("- 只返回真实存在且可访问的范文与链接\n")
            append("- 找不到则 matched=false 并说明原因\n")
            append("- 返回严格 JSON（不要 Markdown / ``` / '''）\n\n")
            append("返回格式：\n")
            append(
                """
{
  "matched": true,
  "sampleTitle": "范文标题或来源描述",
  "sampleText": "范文正文",
  "sourceUrl": "可访问链接",
  "sourceInfo": "网站名 + 年份/作者（可空）",
  "confidence": 0.0,
  "errorMessage": null
}
                """.trimIndent()
            )
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 4096
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseWritingSampleResult(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun extractWritingFromImages(
        images: List<ByteArray>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): String {
        val prompt = buildString {
            append("使用 OCR 提取图片中的考生作文正文。\n")
            append("只保留考生写作内容，不要题干、标题、页眉页脚或批注。\n")
            append("返回严格 JSON：\n")
            append("""{"content":"作文正文"}""")
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMultimodalMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            imageBytes = images, prompt = prompt, maxTokens = 2048
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseWritingOcr(responseText, unwrapEnabled, repairEnabled)
    }

    override suspend fun scoreWriting(
        questionText: String,
        essayText: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): WritingScore {
        val userMessage = buildString {
            appendLine("你是“考研英语作文阅卷专家”，评分严格遵循《考研英语一作文评分分析手册（2026版）》。")
            appendLine("先整体定档，再按扣分细则微调。")
            appendLine("只返回 JSON，不要 Markdown / ``` / '''。")
            appendLine()
            append("作文题干：\n").append(questionText).append("\n\n")
            append("考生作文：\n").append(essayText).append("\n\n")
            append("评分规则（必须执行，来自2026版评分分析手册）：\n")
            append("1) 先整体定档（五档），再按细则微调1-3分。\n")
            append("2) 判断小/大作文：题干含“100词/小作文”→小作文；含“160-200词/大作文”→大作文。\n")
            append("3) 字数硬扣：\n")
            append("   - 小作文：低于90词扣分。\n")
            append("   - 大作文扣分表：151-160(-1),141-150(-2.5),131-140(-4),121-130(-6),111-120(-8),101-110(-10),≤100(-12或降档)。\n")
            append("4) 其他硬扣：跑题/空白/无关内容→0-5分甚至零档；模板痕迹过重→降档；严重语法/拼写影响理解→每处扣0.5-2分。\n")
            append("5) 五档标准：\n")
            append("   第五档：要点全覆盖，语言丰富准确，结构衔接自然，格式语域完美。\n")
            append("   第四档：要点基本覆盖，语言较丰富，偶错，结构较清晰。\n")
            append("   第三档：多数要点，语言基本够用，有错但不影响理解。\n")
            append("   第二档：漏要点较多，语言单调，错误影响理解，结构混乱。\n")
            append("   第一档/零档：严重漏要点/跑题/不可读。\n")
            append("6) 评分维度：内容覆盖、语言丰富准确、结构衔接、格式语域。\n\n")
            append("返回严格 JSON：\n")
            append(
                """
{
  "writingType": "SMALL|LARGE",
  "wordCount": 0,
  "band": "第五档/第四档/第三档/第二档/第一档/零档",
  "totalScore": 0.0,
  "maxScore": 10|20,
  "subScores": {
    "content": 0.0,
    "language": 0.0,
    "structure": 0.0,
    "format": 0.0
  },
  "deductions": [
    {"reason": "字数不足", "score": -2.5}
  ],
  "summary": "总体评价",
  "suggestions": ["建议1", "建议2"]
}
                """.trimIndent()
            )
            append("\n")
            append(Constants.JSON_STRICT_RULES)
        }

        val client = clientProvider.getClient(provider)
        val responseText = client.sendMessage(
            url = baseUrl, apiKey = apiKey, model = model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            maxTokens = 4096
        )

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        return parseWritingScore(responseText, unwrapEnabled, repairEnabled)
    }

    // ── JSON Parsing ──

    private fun parseScanResult(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): ScanResult {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(ScanResultJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            ScanResult(
                examPaperTitle = parsed.examPaperTitle ?: "",
                questionGroups = parsed.questionGroups?.map { it.toDomain() } ?: emptyList(),
                confidence = parsed.confidence ?: 0f
            )
        } else {
            ScanResult(confidence = 0f)
        }
    }

    private fun parseVerifyResult(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): VerifyResult {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(VerifyResultJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            VerifyResult(
                matched = parsed.matched ?: false,
                errorMessage = parsed.errorMessage,
                articleTitle = parsed.articleTitle,
                articleAuthor = parsed.articleAuthor,
                articleContent = parsed.articleContent,
                articleSummary = parsed.articleSummary,
                articleParagraphs = parsed.articleParagraphs,
                sourceUrl = parsed.sourceUrl
            )
        } else {
            VerifyResult(matched = false, errorMessage = "Failed to parse AI response")
        }
    }

    private fun parseAnswerResults(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): List<AnswerResult> {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonArray(cleaned) ?: cleaned.trim()
        val type = Types.newParameterizedType(List::class.java, AnswerResultJson::class.java)
        val adapter = moshi.adapter<List<AnswerResultJson>>(type).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return parsed?.map {
            AnswerResult(
                questionNumber = it.questionNumber ?: 0,
                answer = it.answer ?: "",
                explanation = it.explanation,
                difficultyLevel = it.difficultyLevel,
                difficultyScore = it.difficultyScore
            )
        } ?: emptyList()
    }

    private fun parseSentenceInsertionOptions(extraData: String?): List<String> {
        if (extraData.isNullOrBlank()) return emptyList()
        val adapter = moshi.adapter(SentenceInsertionExtraJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(extraData) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(extraData)) }.getOrNull()
        return parsed?.options?.filter { it.isNotBlank() } ?: emptyList()
    }

    private fun parseTranslationScores(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): List<TranslationScore> {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonArray(cleaned) ?: cleaned.trim()
        val type = Types.newParameterizedType(List::class.java, TranslationScoreJson::class.java)
        val adapter = moshi.adapter<List<TranslationScoreJson>>(type).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return parsed?.map {
            TranslationScore(
                questionNumber = it.questionNumber ?: 0,
                score = it.score ?: 0f,
                maxScore = it.maxScore ?: 2f,
                feedback = it.feedback ?: ""
            )
        } ?: emptyList()
    }

    private fun parseWritingSampleResult(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): WritingSampleResult {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(WritingSampleJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            WritingSampleResult(
                matched = parsed.matched ?: false,
                sampleTitle = parsed.sampleTitle,
                sampleText = parsed.sampleText,
                sourceUrl = parsed.sourceUrl,
                sourceInfo = parsed.sourceInfo,
                confidence = parsed.confidence ?: 0f,
                errorMessage = parsed.errorMessage
            )
        } else {
            WritingSampleResult(matched = false, errorMessage = "Failed to parse AI response")
        }
    }

    private fun buildQuestionGenerationPrompt(
        articleTitle: String,
        articleText: String,
        questionType: String,
        variant: String?
    ): String {
        val baseSchema = """
            仅返回如下 JSON（不含答案与解析）：
            {
              "examPaperTitle": "试卷标题",
              "questionGroups": [
                {
                  "questionType": "$questionType",
                  "sectionLabel": "题型标题",
                  "directions": "作答说明",
                  "passageParagraphs": ["段落1", "段落2"],
                  "sentenceOptions": ["A. ...", "B. ..."],
                  "sourceInfo": "",
                  "sourceUrl": "",
                  "questions": [
                    {
                      "questionNumber": 1,
                      "questionText": "题干",
                      "optionA": "A选项",
                      "optionB": "B选项",
                      "optionC": "C选项",
                      "optionD": "D选项"
                    }
                  ],
                  "wordCount": 0,
                  "difficultyLevel": "MEDIUM",
                  "difficultyScore": 0
                }
              ],
              "confidence": 0.85
            }
        """.trimIndent()

        val commonRules = buildString {
            appendLine("你是考研英语命题专家。请根据给定文章内容出题。")
            appendLine("必须遵守：")
            appendLine("- 只使用提供的文章内容，不虚构外部事实。")
            appendLine("- 不输出答案与解析。")
            appendLine("- 题干与选项必须可从文章中唯一支持。")
            appendLine("- 返回严格 JSON，字段名必须与 schema 一致。")
            appendLine()
        }

        val typeRules = when (questionType) {
            "READING_COMPREHENSION" -> """
                题型：阅读理解（Part A）。
                - 只生成 1 篇文章 + 5 题。
                - 文章长度约 400–500 词，逻辑清晰，议论/说明文风格。
                - 题型分布建议：2 细节 + 1 推断 + 1 例证作用 + 1 主旨/态度。
                - 每题 4 选项，选项语义平行，干扰项具迷惑性但可排除。
            """.trimIndent()
            "CLOZE" -> """
                题型：完形填空（Use of English）。
                - 1 篇短文，20 空，长度约 280–360 词。
                - 空位用 __1__ 到 __20__ 标记，必须严格连续编号。
                - questions 列表共 20 题，questionText 为空字符串即可。
                - 每空 4 选项（词或短语），同词类/语义场平行。
            """.trimIndent()
            "TRANSLATION" -> {
                if (variant == "ENG1") {
                    """
                    题型：翻译（英语一，长文划线）。
                    - 生成约 400–460 词长文。
                    - 在文中标出 5 处划线句段：((1))...((/1)) 到 ((5))...((/5))。
                    - questions 列表共 5 题，每题 questionText 为对应划线句段（不含标记）。
                    """.trimIndent()
                } else {
                    """
                    题型：翻译（英语二，整段短文）。
                    - 生成 140–160 词短文，8–10 句。
                    - passageParagraphs 只保留完整段落，不做句子编号或标记。
                    - questions 列表仅 1 题，questionText 为整段短文（不含任何标记）。
                    """.trimIndent()
                }
            }
            "WRITING" -> {
                if (variant == "SMALL") {
                    """
                    题型：写作（小作文，应用文）。
                    - 题干明确交际场景与写作目的，给出 2–3 个要点。
                    - 明确字数：约 100 词。
                    - questions 列表仅 1 题，questionText 为完整题干。
                    """.trimIndent()
                } else {
                    """
                    题型：写作（大作文）。
                    - 题干为“describe → interpret → comment”结构。
                    - 明确字数：160–200 词。
                    - questions 列表仅 1 题，questionText 为完整题干。
                    """.trimIndent()
                }
            }
            "PARAGRAPH_ORDER" -> """
                题型：新题型—段落排序。
                - 生成 8 段（A–H），每段必须以 "A. ", "B. " ... "H. " 开头。
                - 其中 2–3 段位置可在 directions 中说明“已固定”。
                - questions 列表共 5 题，对应 5 个空位（questionText 如“空1/空2”）。
                - 每段要有清晰的时间/因果/指代线索，保证唯一排序。
            """.trimIndent()
            "SENTENCE_INSERTION" -> """
                题型：新题型—句子插入。
                - 生成 1 篇短文，包含 5 个空位 __1__ 到 __5__。
                - sentenceOptions 提供 7 个候选句（A–G），其中 2 个为干扰句。
                - questions 列表共 5 题，对应 5 个空位（questionText 如“空1/空2”）。
            """.trimIndent()
            "COMMENT_OPINION_MATCH" -> """
                题型：新题型—评论观点匹配。
                - 生成 5 条评论段落，段首用“1. 姓名: ...”格式。
                - sentenceOptions 提供 7 条总结性观点（A–G），2 个干扰项。
                - questions 列表共 5 题，questionText 用评论者姓名。
            """.trimIndent()
            "SUBHEADING_MATCH" -> """
                题型：新题型—小标题匹配。
                - 生成 5 个段落，段首用“1. ...”编号。
                - sentenceOptions 提供 7 个小标题（A–G），2 个干扰项。
                - questions 列表共 5 题（questionText 可用“段落1/段落2”）。
            """.trimIndent()
            "INFORMATION_MATCH" -> """
                题型：新题型—信息匹配。
                - passageParagraphs 提供 7 条信息选项（A–G），每条信息为 1–2 句。
                - questions 列表共 5 题，questionText 为需匹配的描述。
                - 选项需有唯一识别特征，干扰项“关键词相同但结论不同”。
            """.trimIndent()
            else -> "按考研英语出题规范设计。"
        }

        return buildString {
            appendLine(commonRules)
            appendLine(typeRules)
            appendLine()
            appendLine(baseSchema)
            appendLine()
            appendLine("文章标题：$articleTitle")
            appendLine("文章正文：")
            appendLine(articleText.trim())
            appendLine()
            append(Constants.JSON_STRICT_RULES)
        }
    }

    private fun parseWritingPromptSource(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): WritingPromptSourceResult {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(WritingPromptSourceJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            WritingPromptSourceResult(
                matched = parsed.matched ?: false,
                sourceUrl = parsed.sourceUrl,
                sourceInfo = parsed.sourceInfo,
                confidence = parsed.confidence ?: 0f,
                errorMessage = parsed.errorMessage
            )
        } else {
            WritingPromptSourceResult(matched = false, errorMessage = "Failed to parse AI response")
        }
    }

    private fun parseWritingOcr(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): String {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(WritingOcrJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()
        return parsed?.content?.trim().orEmpty()
    }

    private fun parseWritingScore(
        responseText: String,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): WritingScore {
        val cleaned = normalizeResponse(responseText, unwrapEnabled, repairEnabled)
        val json = extractFirstJsonObject(cleaned) ?: cleaned.trim()
        val adapter = moshi.adapter(WritingScoreJson::class.java).lenient()
        val parsed = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: runCatching { adapter.fromJson(removeTrailingCommas(json)) }.getOrNull()

        return if (parsed != null) {
            WritingScore(
                writingType = parsed.writingType ?: "UNKNOWN",
                wordCount = parsed.wordCount ?: 0,
                band = parsed.band ?: "",
                totalScore = parsed.totalScore ?: 0f,
                maxScore = parsed.maxScore ?: 0f,
                subScores = WritingSubScores(
                    content = parsed.subScores?.content ?: 0f,
                    language = parsed.subScores?.language ?: 0f,
                    structure = parsed.subScores?.structure ?: 0f,
                    format = parsed.subScores?.format ?: 0f
                ),
                deductions = parsed.deductions?.map {
                    WritingDeduction(
                        reason = it.reason ?: "",
                        score = it.score ?: 0f
                    )
                } ?: emptyList(),
                summary = parsed.summary ?: "",
                suggestions = parsed.suggestions ?: emptyList()
            )
        } else {
            WritingScore()
        }
    }

    // ── Utility functions ──

    private fun stripCodeFence(text: String): String =
        text
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .replace("'''json", "", ignoreCase = true)
            .replace("'''", "")
            .trim()

    private fun normalizeResponse(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): String {
        val cleaned = stripCodeFence(text)
        val unwrapped = if (unwrapEnabled) {
            val candidate = extractFirstJsonObject(cleaned) ?: cleaned.trim()
            AiResponseUnwrapper.unwrapJsonEnvelope(candidate) ?: cleaned
        } else {
            cleaned
        }
        val stripped = stripCodeFence(unwrapped)
        return if (repairEnabled) AiJsonRepairer.repair(stripped) else stripped
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0; var inString = false; var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) { escaped = false; continue }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun extractFirstJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0; var inString = false; var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) { escaped = false; continue }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '[' -> if (!inString) depth++
                ']' -> if (!inString) { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun removeTrailingCommas(json: String): String =
        json.replace(Regex(",\\s*([}\\]])"), "$1")
}

// ── Internal JSON models ──

private data class ScanResultJson(
    val examPaperTitle: String? = null,
    val questionGroups: List<ScannedQuestionGroupJson>? = null,
    val confidence: Float? = null
)

private data class ScannedQuestionGroupJson(
    val questionType: String? = null,
    val sectionLabel: String? = null,
    val directions: String? = null,
    val passageParagraphs: List<String>? = null,
    val sentenceOptions: List<String>? = null,
    val sourceInfo: String? = null,
    val sourceUrl: String? = null,
    val questions: List<ScannedQuestionJson>? = null,
    val wordCount: Int? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
) {
    fun toDomain() = ScannedQuestionGroup(
        questionType = questionType ?: "READING_COMPREHENSION",
        sectionLabel = sectionLabel,
        directions = directions,
        passageParagraphs = passageParagraphs ?: emptyList(),
        sentenceOptions = sentenceOptions ?: emptyList(),
        sourceInfo = sourceInfo,
        sourceUrl = sourceUrl,
        questions = questions?.map { it.toDomain() } ?: emptyList(),
        wordCount = wordCount ?: 0,
        difficultyLevel = difficultyLevel,
        difficultyScore = difficultyScore
    )
}

private data class ScannedQuestionJson(
    val questionNumber: Int? = null,
    val questionText: String? = null,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val wordCount: Int? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
) {
    fun toDomain() = ScannedQuestion(
        questionNumber = questionNumber ?: 0,
        questionText = questionText ?: "",
        optionA = optionA ?: "",
        optionB = optionB ?: "",
        optionC = optionC ?: "",
        optionD = optionD ?: "",
        wordCount = wordCount ?: 0,
        difficultyLevel = difficultyLevel,
        difficultyScore = difficultyScore
    )
}

private data class VerifyResultJson(
    val matched: Boolean? = null,
    val errorMessage: String? = null,
    val articleTitle: String? = null,
    val articleAuthor: String? = null,
    val articleContent: String? = null,
    val articleSummary: String? = null,
    val articleParagraphs: List<String>? = null,
    val sourceUrl: String? = null
)

private data class AnswerResultJson(
    val questionNumber: Int? = null,
    val answer: String? = null,
    val explanation: String? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)

private data class SentenceInsertionExtraJson(
    val options: List<String>? = null
)

private data class TranslationScoreJson(
    val questionNumber: Int? = null,
    val score: Float? = null,
    val maxScore: Float? = null,
    val feedback: String? = null
)

private data class WritingSampleJson(
    val matched: Boolean? = null,
    val sampleTitle: String? = null,
    val sampleText: String? = null,
    val sourceUrl: String? = null,
    val sourceInfo: String? = null,
    val confidence: Float? = null,
    val errorMessage: String? = null
)

private data class WritingPromptSourceJson(
    val matched: Boolean? = null,
    val sourceUrl: String? = null,
    val sourceInfo: String? = null,
    val confidence: Float? = null,
    val errorMessage: String? = null
)

private data class WritingOcrJson(
    val content: String? = null
)

private data class WritingScoreJson(
    val writingType: String? = null,
    val wordCount: Int? = null,
    val band: String? = null,
    val totalScore: Float? = null,
    val maxScore: Float? = null,
    val subScores: WritingSubScoresJson? = null,
    val deductions: List<WritingDeductionJson>? = null,
    val summary: String? = null,
    val suggestions: List<String>? = null
)

private data class WritingSubScoresJson(
    val content: Float? = null,
    val language: Float? = null,
    val structure: Float? = null,
    val format: Float? = null
)

private data class WritingDeductionJson(
    val reason: String? = null,
    val score: Float? = null
)
