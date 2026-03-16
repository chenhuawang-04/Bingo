package com.xty.englishhelper.util

object Constants {
    const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/"
    const val ANTHROPIC_API_VERSION = "2023-06-01"
    const val ANTHROPIC_USER_AGENT = "claude-cli/2.1.50 (external, cli)"

    const val OPENAI_BASE_URL = "https://api.openai.com/"

    val ANTHROPIC_AVAILABLE_MODELS = listOf(
        "claude-haiku-4-5-20251001" to "Claude Haiku 4.5",
        "claude-sonnet-4-6" to "Claude Sonnet 4.6",
        "claude-opus-4-6" to "Claude Opus 4.6",
    )

    val OPENAI_AVAILABLE_MODELS = listOf(
        "gpt-5.2" to "GPT-5.2",
        "gpt-4.1" to "GPT-4.1",
        "gpt-4.1-mini" to "GPT-4.1 Mini",
        "gpt-4.1-nano" to "GPT-4.1 Nano",
        "o4-mini" to "o4-mini",
        "o3" to "o3",
    )

    const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    const val DEFAULT_OPENAI_MODEL = "gpt-4.1-nano"

    const val AI_WORD_PROMPT_RULES = """【最高优先级指令 / 必须严格执行】
你是专业的英语词汇分析助手。用户会提供一个英语单词，你必须输出“纯 JSON 文本”，不得包含任何额外内容。
这是硬性要求。若输出非 JSON 或无法被标准 JSON 解析器解析，将被视为错误结果并直接丢弃。

【严格 JSON 输出规则（必须逐条遵守）】
1) 仅输出 JSON 本体，不要任何解释、标题、注释、前后缀、礼貌语或空话。
2) 禁止任何 Markdown 代码块或围栏（``` / ```json / ''' / '''json）。
3) 输出必须以 { 开始并以 } 结束，只能是“单一 JSON 对象”。
4) 必须使用双引号，字符串内部出现引号必须转义为 \\"。
5) 禁止单引号、禁止尾逗号、禁止 NaN/Infinity。
6) 仅允许 schema 中定义的字段，不得新增字段，不得改字段名，不得省略必填字段。
7) 若无法确定某字段，按类型给空值："" / 0 / 0.0 / false / null / []。

【任务说明】
请分析以下方面：
1. 词性与中文释义 —— 使用详细词性标注：vt. / vi. / vt.&vi. / n.[C] / n.[U] / n.[集合] 等，无法细分则用 n. / v. / adj. / adv. / prep. / conj. / pron. / int. / art. / aux. / phr.
2. 音标（国际音标）
3. 词根拆解 —— 将单词拆分为前缀/词根/后缀等构词成分，每个成分标注类型（PREFIX/ROOT/SUFFIX/STEM/LINKING/OTHER）和含义
4. 词根解释（用文字说明构词逻辑）
5. 词形变化 —— 列出所有常见屈折变化：plural / past_tense / past_participle / present_participle / third_person / comparative / superlative（只列适用项）
6. 近义词（2-4个，附简要区分）
7. 形近词（1-3个，附含义与区分方法）
8. 同根词（2-4个，附含义与共同词根）

【仅允许的输出 JSON schema】
{
  "phonetic": "/音标/",
  "meanings": [
    {"pos": "详细词性", "definition": "中文释义"}
  ],
  "decomposition": [
    {"segment": "构词成分", "role": "PREFIX|ROOT|SUFFIX|STEM|LINKING|OTHER", "meaning": "含义"}
  ],
  "rootExplanation": "词根解释",
  "inflections": [
    {"form": "变形拼写", "formType": "plural|past_tense|past_participle|present_participle|third_person|comparative|superlative"}
  ],
  "synonyms": [
    {"word": "近义词", "explanation": "区分说明"}
  ],
  "similarWords": [
    {"word": "形近词", "meaning": "含义", "explanation": "区分方法"}
  ],
  "cognates": [
    {"word": "同根词", "meaning": "含义", "sharedRoot": "共同词根"}
  ]
}
"""

    val JSON_STRICT_RULES = """
【严格 JSON 输出规则（必须逐条遵守）】
1) 仅输出 JSON 本体，不要任何解释、标题、注释、前后缀、礼貌语或空话。
2) 禁止任何 Markdown 代码块或围栏（``` / ```json / ''' / '''json）。
3) 输出必须以 { 或 [ 开始，并以 } 或 ] 结束；不得夹杂任何非 JSON 文本。
4) 必须使用双引号，字符串内部出现引号必须转义为 \\"。
5) 禁止单引号、禁止尾逗号、禁止 NaN/Infinity。
6) 仅允许 schema 中定义的字段，不得新增字段，不得改字段名，不得省略必填字段。
7) 若无法确定某字段，按类型给空值："" / 0 / 0.0 / false / null / []。
8) 输出必须可被标准 JSON 解析器直接解析。
""".trimIndent()

    const val AI_USER_PROMPT_TEMPLATE = "请严格按规则分析单词：%s"
}