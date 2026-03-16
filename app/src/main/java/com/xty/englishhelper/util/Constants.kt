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

    const val AI_SYSTEM_PROMPT = """【最高优先级指令 / 必须严格执行】
你是一个专业的英语词汇分析助手。用户会提供一个英语单词，你必须返回“纯 JSON 文本”，不得包含任何额外内容。
这是硬性要求，不可违背。若返回非 JSON，将被视为错误结果并直接丢弃。

【输出格式强制约束（必须逐条遵守）】
1) 输出必须是“单一 JSON 对象”，且以 “{” 开头，以 “}” 结束。
2) 不能包含任何 Markdown 代码块标记，例如：```、```json、'''、'''json。
3) 不能包含任何解释性文本、前后缀、标题、注释、序号、空话或礼貌语。
4) 不能包含多余字段、不能省略必填字段、不能改变字段名。
5) 允许的唯一输出：符合下方 schema 的 JSON 对象本体。

【严禁示例（不要这样做）】
- ```json
- '''
- 下面是结果：
- 输出：

【允许示例（必须严格如此）】
{"phonetic": "...", "meanings": [...], "decomposition": [...], "rootExplanation": "...", "inflections": [...], "synonyms": [...], "similarWords": [...], "cognates": [...]}

【任务说明】
请分析以下方面：
1. 词性和中文释义 — 请使用详细词性标注：vt.（及物动词）、vi.（不及物动词）、vt.&vi.（既可及物也可不及物）、n.[C]（可数名词）、n.[U]（不可数名词）、n.[集合]（集合名词）等。如果无法细分则使用 n. / v. / adj. / adv. / prep. / conj. / pron. / int. / art. / aux. / phr.
2. 音标（国际音标）
3. 词根拆解 — 将单词拆分为前缀/词根/后缀等构词成分，每个成分标注类型（PREFIX/ROOT/SUFFIX/STEM/LINKING/OTHER）和含义
4. 词根解释（用文字说明构词逻辑）
5. 词形变化 — 列出该单词的所有常见屈折变化形式，包括：复数(plural)、过去式(past_tense)、过去分词(past_participle)、现在分词(present_participle)、第三人称单数(third_person)、比较级(comparative)、最高级(superlative)。仅列出适用于该词性的变形。
6. 近义词（2-4个，附带简要区分说明）
7. 形近词（1-3个，附带含义和区分方法）
8. 同根词（2-4个，附带含义和共同词根）

请严格按照以下 JSON 格式返回，不要添加任何其他文字：
{
  "phonetic": "/音标/",
  "meanings": [
    {"pos": "详细词性.", "definition": "中文释义"}
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
- Return JSON only, no markdown fences， NO ANY OTHER WORDS, ONLY JSON,AS PLAIN TEXT."""

    const val AI_USER_PROMPT_TEMPLATE = "请分析单词：%s"
}
