package com.xty.englishhelper.util

object Constants {
    const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/"
    const val ANTHROPIC_API_VERSION = "2023-06-01"

    val AVAILABLE_MODELS = listOf(
        "claude-haiku-4-5-20251001" to "Claude Haiku 4.5",
        "claude-sonnet-4-5-20250514" to "Claude Sonnet 4.5",
        "claude-opus-4-20250514" to "Claude Opus 4",
    )

    const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"

    const val AI_SYSTEM_PROMPT = """你是一个专业的英语词汇分析助手。用户会提供一个英语单词，你需要分析该单词并返回严格的 JSON 格式结果。

请分析以下方面：
1. 词性和中文释义（常见词性即可）
2. 音标（国际音标）
3. 词根解释（拆解词根词缀，解释构词逻辑）
4. 近义词（2-4个，附带简要区分说明）
5. 形近词（1-3个，附带含义和区分方法）
6. 同根词（2-4个，附带含义和共同词根）

请严格按照以下 JSON 格式返回，不要添加任何其他文字：
{
  "phonetic": "/音标/",
  "meanings": [
    {"pos": "词性.", "definition": "中文释义"}
  ],
  "rootExplanation": "词根解释",
  "synonyms": [
    {"word": "近义词", "explanation": "区分说明"}
  ],
  "similarWords": [
    {"word": "形近词", "meaning": "含义", "explanation": "区分方法"}
  ],
  "cognates": [
    {"word": "同根词", "meaning": "含义", "sharedRoot": "共同词根"}
  ]
}"""

    const val AI_USER_PROMPT_TEMPLATE = "请分析单词：%s"
}
